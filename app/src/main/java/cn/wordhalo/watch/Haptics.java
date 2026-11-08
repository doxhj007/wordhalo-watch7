package cn.wordhalo.watch;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

/** Short, silent tactile cues. A result always takes precedence over navigation. */
final class Haptics {
    private static final int TICK = VibrationEffect.Composition.PRIMITIVE_TICK;
    private static final int LOW_TICK = VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
    private static final int CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK;
    private static final int RISE = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE;
    private static final int FALL = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL;

    // Primitive delays are pauses AFTER the preceding primitive, not timestamps.
    // Fallback envelopes use a gentle attack/release instead of rectangular buzzes.
    private static final Cue BEZEL = new Cue(0, false, 55,
            new int[]{LOW_TICK}, new float[]{.34f}, new int[]{0},
            new long[]{4, 22, 8}, new int[]{150, 255, 140});
    private static final Cue PRESS = new Cue(1, false, 65,
            new int[]{CLICK}, new float[]{.42f}, new int[]{0},
            new long[]{5, 32, 10}, new int[]{160, 255, 150});
    private static final Cue SELECT = new Cue(2, false, 60,
            new int[]{TICK}, new float[]{.56f}, new int[]{0},
            new long[]{5, 36, 11}, new int[]{170, 255, 155});
    private static final Cue REVEAL = new Cue(3, false, 110,
            new int[]{RISE, TICK}, new float[]{.24f, .44f}, new int[]{0, 12},
            new long[]{8, 18, 20, 12, 20, 28, 10},
            new int[]{90, 150, 220, 150, 0, 255, 120});
    private static final Cue CORRECT = new Cue(4, true, 110,
            new int[]{CLICK, CLICK}, new float[]{.56f, .92f}, new int[]{0, 37},
            new long[]{5, 32, 10, 42, 5, 48, 12},
            new int[]{170, 240, 160, 0, 185, 255, 145});
    private static final Cue WRONG = new Cue(5, true, 180,
            new int[]{RISE, FALL}, new float[]{.58f, .46f}, new int[]{0, 0},
            new long[]{10, 24, 36, 130, 58, 32, 18},
            new int[]{145, 205, 240, 255, 225, 175, 105});
    private static final Cue DONE = new Cue(6, true, 250,
            new int[]{TICK, CLICK, CLICK}, new float[]{.40f, .65f, .92f},
            new int[]{0, 43, 66},
            new long[]{5, 25, 8, 58, 5, 36, 10, 75, 5, 52, 15},
            new int[]{130, 195, 130, 0, 160, 230, 145, 0, 180, 255, 125});
    private static final Cue BOUNDARY = new Cue(7, false, 150,
            new int[]{LOW_TICK, LOW_TICK}, new float[]{.48f, .22f},
            new int[]{0, 24},
            new long[]{28, 36, 15}, new int[]{230, 0, 160});
    private static final Cue BOOKMARK = new Cue(8, false, 140,
            new int[]{TICK, CLICK},new float[]{.4f,.8f},new int[]{0,25},
            new long[]{6,24,8,32,6,40,12},new int[]{120,205,100,0,150,255,110});

    private final Context context;
    private final Vibrator vibrator;
    private final NotificationManager notifications;
    private final boolean available;
    private final boolean amplitudeControl;
    private final boolean[] primitiveSupported = new boolean[9];
    private final int[] primitiveDurations = new int[9];
    private final long[] lastCueAt = new long[9];
    private final AudioAttributes legacyAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private final VibrationAttributes touchAttributes = new VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH).build();
    private long resultProtectedUntil;
    private long lastInteractionAt = -1000;

    // Retained for callers and persisted preferences: off / gentle / standard / clear.
    int strength = 3;
    int navigationStrength = 3;
    boolean foreground = false;

    Haptics(Context c) {
        context = c;
        Vibrator found = null;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = c.getSystemService(VibratorManager.class);
            if (manager != null) found = manager.getDefaultVibrator();
        }
        if (found == null) found = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator = found;
        notifications = c.getSystemService(NotificationManager.class);
        available = vibrator != null && vibrator.hasVibrator();
        amplitudeControl = available && vibrator.hasAmplitudeControl();
        for (int i = 0; i < lastCueAt.length; i++) lastCueAt[i] = -1000;
        // Android 11's capability result is not reliable per primitive. Use fallback there.
        if (available && Build.VERSION.SDK_INT >= 31) {
            int[] ids = {TICK, LOW_TICK, CLICK, RISE, FALL};
            try {
                boolean[] supported = vibrator.arePrimitivesSupported(ids);
                int[] durations = vibrator.getPrimitiveDurations(ids);
                for (int i = 0; i < ids.length; i++) {
                    primitiveSupported[ids[i]] = supported[i];
                    primitiveDurations[ids[i]] = durations[i];
                }
            } catch (RuntimeException ignored) {
                // A vendor implementation may not expose every capability. Waveforms remain.
                for (int i = 0; i < primitiveSupported.length; i++) primitiveSupported[i] = false;
            }
        }
    }

    void setStrength(int value) {
        strength = Math.max(0, Math.min(3, value));
        if (strength == 0) cancel();
    }

    void tick() { play(BEZEL); }
    void press() { play(PRESS); }
    void select() { play(SELECT); }
    void boundary() { play(BOUNDARY); }
    void reveal() { play(REVEAL); }
    void correct() { play(CORRECT); }
    void wrong() { play(WRONG); }
    void done() { play(DONE); }
    void bookmark() { play(BOOKMARK); }
    void setNavigationStrength(int value){navigationStrength=Math.max(0,Math.min(3,value));if(navigationStrength==0)cancel();}

    String modeLabel() {
        if (strength >= 2) return amplitudeControl ? "分段力度 · 渐起渐落" : "节奏兼容 · 强弱由系统控制";
        return primitiveSupported[CLICK] && primitiveSupported[TICK]
                ? "自适应细腻触感" : "自适应兼容触感";
    }

    private boolean enabled() {
        if (!foreground || !available) return false;
        try {
            if (Settings.System.getInt(context.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return false;
            if (notifications != null) {
                int filter = notifications.getCurrentInterruptionFilter();
                if (filter == NotificationManager.INTERRUPTION_FILTER_NONE
                        || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS
                        || filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) return false;
            }
        } catch (SecurityException ignored) {
            // USAGE_TOUCH still lets Android enforce policy; never request a bypass flag.
        }
        return true;
    }

    private float scale(int level) { return level == 1 ? .48f : level == 3 ? 1f : .73f; }

    private void play(Cue cue) {
        if (!enabled()) return;
        int level=cue.result?strength:navigationStrength;if(level==0)return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastCueAt[cue.id] < cue.debounceMs) return;
        if (!cue.result && (now < resultProtectedUntil || now - lastInteractionAt < 48)) return;

        long duration = 0;
        boolean sent = false;
        if (canCompose(cue,level)) {
            try {
                VibrationEffect.Composition composition = VibrationEffect.startComposition();
                for (int i = 0; i < cue.primitives.length; i++) {
                    int id = cue.primitives[i];
                    composition.addPrimitive(id, cue.scales[i] * scale(level), cue.delays[i]);
                    duration += cue.delays[i] + primitiveDuration(id);
                }
                sent = dispatch(composition.compose());
            } catch (RuntimeException ignored) {
                // Rejecting any primitive must never result in a missing answer cue.
            }
        }
        if (!sent) {
            long[] timings = cue.timings.clone();
            int[] amplitudes = new int[cue.amplitudes.length];
            duration = 0;
            for (int i = 0; i < amplitudes.length; i++) {
                if (cue.amplitudes[i] == 0) amplitudes[i] = 0;
                else if (amplitudeControl) amplitudes[i] = Math.max(1,
                        Math.min(255, Math.round(cue.amplitudes[i] * scale(level))));
                else {
                    amplitudes[i] = VibrationEffect.DEFAULT_AMPLITUDE;
                    // Without amplitude control, duration distinguishes the strength levels.
                    float timeScale = level == 1 ? .58f : level == 2 ? .80f : 1f;
                    timings[i] = Math.max(2, Math.round(timings[i] * timeScale));
                }
                duration += timings[i];
            }
            try { sent = dispatch(VibrationEffect.createWaveform(timings, amplitudes, -1)); }
            catch (RuntimeException ignored) { /* Haptics must never interrupt study. */ }
        }
        if (sent) {
            lastCueAt[cue.id] = now;
            if (cue.result) resultProtectedUntil = now + duration + 28;
            else lastInteractionAt = now;
        }
    }

    private boolean canCompose(Cue cue,int level) {
        // Brief vendor primitives were almost imperceptible on a loosely worn Watch7.
        // Standard/clear therefore use explicit, sustained envelopes with full peaks.
        if (Build.VERSION.SDK_INT < 31 || level >= 2) return false;
        int duration = 0;
        for (int i = 0; i < cue.primitives.length; i++) {
            int id = cue.primitives[i];
            if (!primitiveSupported[id]) return false;
            duration += cue.delays[i] + primitiveDuration(id);
        }
        // Keep all cues brief even if a vendor gives a primitive an unusual duration.
        return duration <= 450;
    }

    private int primitiveDuration(int id) {
        if (primitiveDurations[id] > 0) return primitiveDurations[id];
        if (id == RISE) return 150;
        if (id == FALL) return 100;
        return id == TICK ? 5 : 12;
    }

    private boolean dispatch(VibrationEffect effect) {
        try {
            if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect, touchAttributes);
            else vibrator.vibrate(effect, legacyAttributes);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    void cancel() {
        resultProtectedUntil = 0;
        lastInteractionAt = -1000;
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (RuntimeException ignored) { }
        }
    }

    private static final class Cue {
        final int id;
        final boolean result;
        final int debounceMs;
        final int[] primitives;
        final float[] scales;
        final int[] delays;
        final long[] timings;
        final int[] amplitudes;

        Cue(int id, boolean result, int debounceMs, int[] primitives, float[] scales,
            int[] delays, long[] timings, int[] amplitudes) {
            this.id = id;
            this.result = result;
            this.debounceMs = debounceMs;
            this.primitives = primitives;
            this.scales = scales;
            this.delays = delays;
            this.timings = timings;
            this.amplitudes = amplitudes;
        }
    }
}
