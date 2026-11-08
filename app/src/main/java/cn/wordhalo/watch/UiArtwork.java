package cn.wordhalo.watch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Quiet, original vector accents. The containing screen owns any animation. */
final class UiArtwork extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private final int color;

    UiArtwork(Context context, String kind, int color) {
        super(context);
        this.kind = kind == null ? "" : kind;
        this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
        setSoundEffectsEnabled(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 160f, getHeight() / 88f);
        if (scale <= 0) return;
        int saved = canvas.save();
        canvas.translate((getWidth() - 160 * scale) / 2f,
                (getHeight() - 88 * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (kind.startsWith("wave") && kind.length() == 5
                && kind.charAt(4) >= '0' && kind.charAt(4) <= '5') {
            waveform(canvas, kind.charAt(4) - '0');
        } else if (kind.equals("success")) {
            success(canvas);
        } else if (kind.equals("retry")) {
            retry(canvas);
        } else if (kind.equals("spark")) {
            spark(canvas);
        }
        canvas.restoreToCount(saved);
    }

    private int tone(float opacity, float light) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.argb(Math.round(Color.alpha(color) * opacity),
                Math.round(r + (255 - r) * light),
                Math.round(g + (255 - g) * light),
                Math.round(b + (255 - b) * light));
    }

    private void stroke(float width, float opacity, float light) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(tone(opacity, light));
    }

    private void dot(Canvas canvas, float x, float y, float radius,
            float opacity, float light) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(opacity, light));
        canvas.drawCircle(x, y, radius, paint);
    }

    private void waveform(Canvas canvas, int type) {
        // Twenty-three columns share a quiet dotted baseline. Shape, spacing and
        // brightness distinguish tactile events without looking like a chart.
        final float[][] amplitudes = {
            {0,0,0,0,0,0,2,7,2,0,0,0,0,0,0,3,1,0,0,0,0,0,0},
            {0,0,0,0,1,3,8,17,23,12,3,0,0,3,7,4,1,0,0,0,0,0,0},
            {0,0,0,1,2,4,7,11,14,16,15,12,8,5,3,1,0,0,0,0,0,0,0},
            {0,0,0,1,4,11,20,11,4,1,0,0,1,5,14,23,14,5,1,0,0,0,0},
            {0,0,1,3,6,9,12,15,17,18,19,19,18,17,15,12,9,6,3,1,0,0,0},
            {0,0,1,5,9,5,1,0,0,2,8,15,8,2,0,0,3,12,23,12,3,0,0}
        };
        float[] envelope = amplitudes[type];
        for (int i = 0; i < envelope.length; i++) {
            float x = 25 + i * 5;
            float amplitude = envelope[i];
            float weight = amplitude / 23f;
            if (amplitude == 0) {
                dot(canvas, x, 44, 1.05f, .20f, 0);
            } else {
                stroke(2.6f, .40f + weight * .60f, weight * .30f);
                canvas.drawLine(x, 44 - amplitude, x, 44 + amplitude, paint);
            }
        }
        // Detached end dots leave room for the rhythm to breathe.
        dot(canvas, 14, 44, 1, .10f, 0);
        dot(canvas, 146, 44, 1, .10f, 0);
    }

    private void success(Canvas canvas) {
        stroke(1.2f, .16f, 0);
        canvas.drawCircle(80, 44, 31, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(.08f, 0));
        canvas.drawCircle(80, 44, 25, paint);
        stroke(1.5f, .50f, 0);
        canvas.drawArc(55, 19, 105, 69, 144, 278, false, paint);
        stroke(3.1f, 1, .14f);
        Path check = new Path();
        check.moveTo(68, 43);
        check.lineTo(77, 52);
        check.lineTo(94, 34);
        canvas.drawPath(check, paint);
        dot(canvas, 111, 27, 2.2f, .75f, .20f);
        dot(canvas, 45, 56, 1.6f, .35f, 0);
        dot(canvas, 116, 59, 1.3f, .25f, 0);
    }

    private void retry(Canvas canvas) {
        stroke(1.2f, .14f, 0);
        canvas.drawCircle(80, 44, 31, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(.06f, 0));
        canvas.drawCircle(80, 44, 25, paint);
        stroke(2.6f, .90f, .08f);
        canvas.drawArc(62, 26, 98, 62, -120, 286, false, paint);
        // The open end of the turn is at the upper-left; a short arrow returns
        // gently toward the beginning, instead of an alarming error cross.
        Path arrow = new Path();
        arrow.moveTo(64, 29);
        arrow.lineTo(71, 28.4f);
        arrow.lineTo(70.2f, 21.4f);
        canvas.drawPath(arrow, paint);
        dot(canvas, 80, 44, 2, .35f, 0);
        dot(canvas, 45, 33, 1.6f, .25f, 0);
        dot(canvas, 116, 54, 1.5f, .35f, 0);
    }

    private void spark(Canvas canvas) {
        stroke(1.2f, .18f, 0);
        canvas.drawCircle(80, 44, 29, paint);
        stroke(1.7f, .65f, .10f);
        canvas.drawArc(51, 15, 109, 73, 202, 45, false, paint);
        canvas.drawArc(51, 15, 109, 73, 19, 45, false, paint);
        star(canvas, 80, 44, 18, 7, .95f);
        star(canvas, 111, 22, 5.5f, 2, .65f);
        star(canvas, 48, 61, 4.5f, 1.7f, .45f);
        dot(canvas, 51, 24, 1.8f, .45f, 0);
        dot(canvas, 110, 66, 1.7f, .55f, .10f);
        dot(canvas, 80, 8, 1.2f, .20f, 0);
        dot(canvas, 80, 80, 1.2f, .20f, 0);
    }

    private void star(Canvas canvas, float x, float y, float outer,
            float inner, float opacity) {
        Path shape = new Path();
        shape.moveTo(x, y - outer);
        shape.quadTo(x + inner, y - inner, x + outer, y);
        shape.quadTo(x + inner, y + inner, x, y + outer);
        shape.quadTo(x - inner, y + inner, x - outer, y);
        shape.quadTo(x - inner, y - inner, x, y - outer);
        shape.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(opacity, .18f));
        canvas.drawPath(shape, paint);
    }
}
