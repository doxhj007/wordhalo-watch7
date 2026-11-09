package cn.wordhalo.watch;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import java.io.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,qualifiers="w240dp-h240dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DeviceFlowTest {
    MainActivity a;ActivityController<MainActivity> controller;
    @Before public void open()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("online",false).commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();a=controller.get();
        for(int i=0;i<100&&!a.ready;i++){Thread.sleep(20);shadowOf(Looper.getMainLooper()).idle();}
        assertTrue("Vocabulary loads from APK assets",a.ready);a.media.connections=url->{throw new IOException("Real networking disabled in tests");};layout();
    }
    void layout(){a.root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));a.root.layout(0,0,480,480);shadowOf(Looper.getMainLooper()).idle();a.render();a.root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));a.root.layout(0,0,480,480);}
    void shot(String name)throws Exception{layout();Bitmap b=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);a.root.draw(new Canvas(b));File f=new File(System.getProperty("shots"),name+".png");f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    @Test public void learnAnswerResumeAndFinish()throws Exception{
        shot("01-home");a.start(false);assertNotNull(a.session);assertTrue(a.session.studying());shot("02-study");
        a.session.learning=a.session.learn.size();a.render();assertEquals(3,a.answerViews.size());shot("03-quiz");
        String id=a.session.current();int correct=a.session.options.indexOf(id);int wrong=(correct+1)%3;
        a.answer(wrong);assertTrue(a.session.feedback);assertTrue(a.progress.get(id).trouble);shot("04-wrong");
        Engine.Session restored=a.store.session();assertEquals(id,restored.current());assertTrue(restored.feedback);assertEquals(1,a.store.progress().get(id).attempts);
        Engine.next(a.session);a.render();a.answer(a.session.options.indexOf(a.session.current()));assertTrue(a.session.lastCorrect);shot("05-correct");
        while(!a.session.complete()){Engine.next(a.session);if(!a.session.complete()){a.render();a.answer(a.session.options.indexOf(a.session.current()));}}
        a.render();shot("06-complete");assertEquals(10,a.session.answered.size());assertEquals(9,a.session.correct);
        a.screen="settings";a.render();shot("07-settings");assertNotNull(a.scroll);a.rotate(1);
        a.screen="haptics";a.render();a.haptics.tick();a.haptics.correct();a.haptics.wrong();shot("08-haptics");
    }
    @Test public void sessionRoundTripAndRotary()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();a.rotate(1);assertEquals(0,a.session.focus);a.rotate(1);assertEquals(1,a.session.focus);assertFalse(a.session.feedback);
        Engine.Session saved=Store.decode(Store.encode(a.session));assertEquals(a.session.options,saved.options);assertEquals(a.session.queue,saved.queue);assertEquals(1,saved.focus);
        a.onPause();a.onResume();assertEquals(saved.current(),a.session.current());assertEquals(saved.options,a.session.options);
        a.flashWords.clear();a.flashWords.add(a.byId.get("illuminate"));a.flashAt=0;a.flipped=true;a.screen="flash";shot("09-illustration");
    }
    @Test public void longestChoicesFitAndActivityRestores()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.screen="session";
        java.util.List<Engine.Word> sorted=new java.util.ArrayList<>(a.words);sorted.sort((x,y)->Integer.compare(y.meaning.length(),x.meaning.length()));
        for(int i=0;i<40;i++){
            a.session.options.clear();for(int j=0;j<3;j++)a.session.options.add(sorted.get((i+j)%sorted.size()).id);a.render();layout();
            for(TextView t:a.answerViews){android.text.Layout l=t.getLayout();assertNotNull(l);assertTrue("Text height fits: "+t.getText(),l.getHeight()<=t.getHeight()-t.getPaddingTop()-t.getPaddingBottom());for(int k=0;k<l.getLineCount();k++)assertEquals("Full choice text: "+t.getText(),0,l.getEllipsisCount(k));}
        }
        shot("10-long-choices");a.persist(null);String current=a.session.current();java.util.List<String> options=new java.util.ArrayList<>(a.session.options);
        controller.pause().stop().destroy();controller=null;open();assertEquals(current,a.session.current());assertEquals(options,a.session.options);
    }
    @Test public void smoothRotaryAndExpandedScreens()throws Exception{
        a.rotate(1);assertEquals(0,a.homeFocus);assertNotNull(a.halo);
        a.rotate(1);assertEquals(1,a.homeFocus);assertNotNull(a.halo.motion);
        a.start(false);a.rotate(1);assertEquals(1,a.session.learning);a.rotate(-1);assertEquals(0,a.session.learning);a.rotate(-1);assertEquals(0,a.session.learning);
        a.session.learning=a.session.learn.size();a.render();layout();a.rotate(1);RectF first=new RectF(a.halo.current);a.rotate(1);
        assertNotNull(a.halo.motion);assertEquals("Highlight begins at the previous bounds",first,a.halo.current);
        a.halo.motion.setCurrentPlayTime(100);assertTrue("Highlight interpolates between rows",a.halo.current.top>first.top&&a.halo.current.top<a.px(278));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));assertEquals(1,a.session.focus);assertFalse(a.session.feedback);shot("11-focus");
        assertNotNull(a.phonetic);assertEquals(a.session.current(),a.pronunciationWord);assertNull("Rendering a quiz must never play audio",a.media.player);
        a.openMemory("ecology");assertTrue(a.media.hasPhoto("ecology"));shot("12-photo");a.back();assertEquals("session",a.screen);
        a.openMemory("ambitious");assertFalse(a.media.mnemonic("ambitious").isEmpty());shot("13-mnemonic");
        a.screen="haptics";a.render();int before=a.hapticAt;a.rotate(1);assertEquals((before+1)%6,a.hapticAt);
        a.motion=false;a.screen="session";a.render();a.rotate(1);assertNull("Reduced motion never starts an animator",a.halo.motion);
    }
    @Test public void strengthenedHapticsRespectResultsAndLifecycle()throws Exception{
        android.os.Vibrator vibrator=a.getSystemService(android.os.VibratorManager.class).getDefaultVibrator();org.robolectric.shadows.ShadowVibrator shadow=shadowOf(vibrator);shadow.setHasVibrator(true);shadow.setHasAmplitudeControl(true);
        android.provider.Settings.System.putInt(a.getContentResolver(),android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,1);
        a.getSystemService(android.app.NotificationManager.class).setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
        Haptics h=new Haptics(a);h.foreground=true;h.setStrength(3);h.correct();
        long[] correct=shadow.getPattern();assertNotNull(correct);assertTrue("The clear correct cue must last at least 140 ms",java.util.Arrays.stream(correct).sum()>=140);assertEquals("Never repeat vibration",-1,shadow.getRepeat());
        h.tick();assertArrayEquals("Navigation must not cut off answer feedback",correct,shadow.getPattern());h.cancel();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));h.wrong();assertTrue("Error is a distinct longer envelope",java.util.Arrays.stream(shadow.getPattern()).sum()>=280);h.cancel();assertTrue(shadow.isCancelled());
        h.foreground=false;h.tick();assertFalse("No vibration after leaving the app",shadow.isVibrating());
        java.lang.reflect.Field f=Haptics.class.getDeclaredField("CORRECT");f.setAccessible(true);Object cue=f.get(null);java.lang.reflect.Field amp=cue.getClass().getDeclaredField("amplitudes");amp.setAccessible(true);assertEquals("Clear mode reaches the hardware amplitude ceiling",255,java.util.Arrays.stream((int[])amp.get(cue)).max().getAsInt());
    }
    static byte[] wav(){java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(204).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{'R','I','F','F'}).putInt(196).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(160);return b.array();}
    File seed(String url,byte[] bytes)throws Exception{File f=new File(a.media.cache,MediaLibrary.hash(url));try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}return f;}
    @Test public void readoutCardKeepsLearningAndNeverAutoplays()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();String before=Store.encode(a.session).toString();a.openSound(a.session.current());layout();
        assertEquals("sound",a.screen);assertEquals(2,a.soundViews.size());assertNull(a.media.player);int token=a.media.soundToken;a.rotate(1);assertEquals(token,a.media.soundToken);assertNull(a.media.player);assertEquals(before,Store.encode(a.session).toString());
        a.back();assertEquals("session",a.screen);assertEquals(before,Store.encode(a.session).toString());
        a.openMemory("ambitious");int page=a.memoryPage;a.openSound("ambitious");a.back();assertEquals("memory",a.screen);assertEquals(page,a.memoryPage);
        a.prefs.edit().putBoolean("online",true).commit();a.media.phones.put("ambitious",new org.json.JSONObject().put("uk","æmˈbɪʃəs").put("us","æmˈbɪʃəs"));a.openSound("ambitious");shot("15-pronunciation");assertTrue(a.soundViews.get(0).getText().toString().contains("在线播放"));assertTrue(a.soundViews.get(1).getText().toString().contains("在线播放"));a.prefs.edit().putBoolean("online",false).commit();
        java.util.List<Engine.Word> longest=new java.util.ArrayList<>(a.words);longest.sort((x,y)->Integer.compare(a.media.genericIpa(y.id).length(),a.media.genericIpa(x.id).length()));
        for(int i=0;i<10;i++){a.soundId=longest.get(i).id;layout();for(TextView t:a.soundViews){android.text.Layout l=t.getLayout();assertTrue(l.getHeight()<=t.getHeight()-t.getPaddingTop()-t.getPaddingBottom());for(int k=0;k<l.getLineCount();k++)assertEquals("Full IPA: "+t.getText(),0,l.getEllipsisCount(k));}}
    }
    @Test public void rapidRotaryAccumulatesAndTouchTakesOver(){
        a.screen="settings";layout();a.rotate(1);a.rotate(1);a.rotate(1);assertEquals(264,a.scrollTarget);assertNotNull(a.scrollMotion);a.scrollMotion.setCurrentPlayTime(180);assertEquals(264,a.scroll.getScrollY());
        a.rotate(-1);assertEquals(176,a.scrollTarget);MotionEvent e=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,240,240,0);a.root.dispatchTouchEvent(e);e.recycle();assertNull(a.scrollMotion);
        a.motion=false;a.rotate(1);assertNull(a.scrollMotion);assertEquals(a.scrollTarget,a.scroll.getScrollY());
    }
    @Test public void movingOffTheRimDoesNotRotate(){
        MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,462,240,0),move=MotionEvent.obtain(0,20,MotionEvent.ACTION_MOVE,250,260,0),up=MotionEvent.obtain(0,40,MotionEvent.ACTION_UP,250,260,0);
        a.root.dispatchTouchEvent(down);a.root.dispatchTouchEvent(move);a.root.dispatchTouchEvent(up);down.recycle();move.recycle();up.recycle();assertEquals(-1,a.homeFocus);
    }
    @Test public void canceledMetadataAndPhotoCallbacksAreSuppressed()throws Exception{
        String id="ambitious",url="https://dict.youdao.com/jsonapi?q="+MediaLibrary.encode(id)+"&dicts="+MediaLibrary.encode("{\"count\":1,\"dicts\":[[\"ec\"]]}");
        seed(url,"{\"ec\":{\"word\":[{\"return-phrase\":{\"l\":{\"i\":\"ambitious\"}},\"ukphone\":\"test\",\"usphone\":\"test\"}]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.util.List<Boolean> results=new java.util.ArrayList<>();a.media.phones(id,results::add);a.media.phoneTask.future.get(3,java.util.concurrent.TimeUnit.SECONDS);a.media.pageChanged();shadowOf(Looper.getMainLooper()).idle();assertTrue(results.isEmpty());
        org.json.JSONObject scene=a.media.scene("ecology");try(InputStream in=a.getAssets().open(scene.getString("file"))){seed(scene.getString("url"),MediaLibrary.read(in,1500000));}
        java.util.List<Bitmap> pictures=new java.util.ArrayList<>();a.media.highPhoto("ecology",pictures::add);a.media.photoTask.future.get(3,java.util.concurrent.TimeUnit.SECONDS);a.media.pageChanged();shadowOf(Looper.getMainLooper()).idle();assertTrue(pictures.isEmpty());
    }
    static class FakeConnection extends java.net.HttpURLConnection {
        byte[] data;long declared;String type="audio/wav";volatile boolean disconnected;
        FakeConnection(java.net.URL url,byte[] bytes){super(url);data=bytes;declared=bytes.length;}
        @Override public void connect(){}@Override public boolean usingProxy(){return false;}@Override public void disconnect(){disconnected=true;}
        @Override public int getResponseCode(){return 200;}@Override public String getContentType(){return type;}@Override public long getContentLengthLong(){return declared;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(data);}
    }
    @Test public void onlineAudioStreamsWithoutFilesAndRepeatsOnDemand()throws Exception{
        a.prefs.edit().putBoolean("online",true).commit();java.util.List<String> messages=new java.util.ArrayList<>();java.util.concurrent.atomic.AtomicInteger opens=new java.util.concurrent.atomic.AtomicInteger();
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(source->{opens.incrementAndGet();return new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(400,100);});
        long bytes=a.media.cacheBytes();a.media.speak("ambitious",1,messages::add);android.media.MediaPlayer first=a.media.player;assertNotNull(first);assertFalse(first.isPlaying());
        assertEquals(android.net.Uri.parse(MediaLibrary.voiceUrl("ambitious",1)),shadowOf(first).getSourceUri());shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(120));assertTrue(first.isPlaying());assertTrue(messages.get(messages.size()-1).contains("在线播放"));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));assertNull(a.media.player);assertTrue(messages.get(messages.size()-1).contains("播放完毕"));assertEquals(bytes,a.media.cacheBytes());
        a.media.speak("ambitious",1,messages::add);assertNotNull(a.media.player);assertNotSame(first,a.media.player);assertTrue(opens.get()>=2);a.media.stop();assertEquals(bytes,a.media.cacheBytes());
        try{a.media.download(MediaLibrary.voiceUrl("ambitious",1),750000);fail("Audio must never use persistent cache");}catch(IOException expected){}
    }
    @Test public void latePreparedEventsCannotPlayAfterSwitchOrPause()throws Exception{
        a.prefs.edit().putBoolean("online",true).commit();org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(source->new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(400,5000));
        java.util.List<String> old=new java.util.ArrayList<>(),fresh=new java.util.ArrayList<>();a.media.speak("ambitious",1,old::add);android.media.MediaPlayer first=a.media.player;android.media.MediaPlayer.OnPreparedListener late=shadowOf(first).getOnPreparedListener();
        a.media.speak("ambitious",2,fresh::add);android.media.MediaPlayer second=a.media.player;assertNotSame(first,second);late.onPrepared(first);assertEquals(1,old.size());assertFalse(first.isPlaying());assertSame(second,a.media.player);
        android.media.MediaPlayer.OnPreparedListener lateSecond=shadowOf(second).getOnPreparedListener();a.onPause();lateSecond.onPrepared(second);assertNull(a.media.player);assertFalse(second.isPlaying());assertEquals(1,fresh.size());
    }
    @Test public void failedAndTimedOutStreamingCanRetryWithoutBlockingStudy()throws Exception{
        a.start(false);String current=a.session.current();a.prefs.edit().putBoolean("online",true).commit();java.util.List<String> messages=new java.util.ArrayList<>();
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(source->new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(400,60000));a.media.speak("ambitious",1,messages::add);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(15001));assertNull(a.media.player);assertTrue(messages.get(messages.size()-1).contains("超时"));assertEquals(current,a.session.current());
        a.media.speak("ambitious",2,messages::add);assertNotNull(a.media.player);shadowOf(a.media.player).invokeErrorListener(android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN,0);assertNull(a.media.player);assertTrue(messages.get(messages.size()-1).contains("重试"));
        a.media.speak("ambitious",1,messages::add);assertNotNull(a.media.player);a.media.pageChanged();assertNull(a.media.player);
    }
    @Test public void upgradeRemovesOnlyLegacyAudioAndOfflineModeNeverPlaysIt()throws Exception{
        File legacy=seed(MediaLibrary.voiceUrl("ambitious",1),wav()),partial=seed(MediaLibrary.voiceUrl("ambitious",2),wav());File part=new File(partial.getPath()+".part");assertTrue(partial.renameTo(part));File photo=seed("https://example.test/photo",new byte[30]);
        a.start(false);a.session.learning=a.session.learn.size();a.render();String id=a.session.current();a.answer(a.session.options.indexOf(id));int attempts=a.store.progress().get(id).attempts;
        java.util.List<String> messages=new java.util.ArrayList<>();a.media.speak("ambitious",1,messages::add);assertNull(a.media.player);assertTrue(messages.get(0).contains("联网已关闭"));
        a.prefs.edit().putBoolean("streamingAudioCleanup",false).commit();a.media.discardLegacyAudio(a.words);assertFalse(legacy.exists());assertFalse(part.exists());assertTrue(photo.exists());assertEquals(attempts,a.store.progress().get(id).attempts);
        a.media.clearCache(ok->{});for(int i=0;i<40&&photo.exists();i++)Thread.sleep(10);assertFalse(photo.exists());assertEquals(attempts,a.store.progress().get(id).attempts);
    }
    @Test public void allPhotosDecodeAndNoDownloadEntryRemains()throws Exception{
        int pictured=0;java.util.Map<String,Integer> reused=new java.util.HashMap<>();for(Engine.Word w:a.words)if(a.media.hasPhoto(w.id)){pictured++;Bitmap b=a.media.photo(w.id);assertNotNull("Bundled photo decodes: "+w.id,b);assertTrue(b.getWidth()>20&&b.getHeight()>20);assertFalse(a.media.scene(w.id).optString("source").isEmpty());assertFalse(a.media.scene(w.id).optString("license").isEmpty());if(!a.media.hasDedicatedVisual(w.id)){String file=a.media.scene(w.id).optString("file");reused.put(file,reused.getOrDefault(file,0)+1);}}assertTrue(pictured>=200);for(java.util.Map.Entry<String,Integer> use:reused.entrySet())assertTrue("Curated stock photo is not over-reused: "+use.getKey(),use.getValue()<=3);
        a.screen="settings";layout();LinearLayout list=(LinearLayout)a.scroll.getChildAt(0);for(int i=0;i<list.getChildCount();i++){String title=((TextView)list.getChildAt(i)).getText().toString();assertFalse(title.contains("课前缓存"));}assertNull(a.media.player);
    }
    @Test public void dedicatedMnemonicArtIsUniqueOfflineAndExplainsTheWord()throws Exception{
        assertEquals(72,a.media.visuals.length());java.util.Set<String> files=new java.util.HashSet<>();java.util.Iterator<String> keys=a.media.visuals.keys();while(keys.hasNext()){String id=keys.next();assertNotNull("Dedicated visual belongs to vocabulary: "+id,a.byId.get(id));org.json.JSONObject visual=a.media.scene(id);assertTrue(visual.optBoolean("generated"));assertTrue(visual.optString("caption").length()>=8);assertTrue("One image per word: "+id,files.add(visual.getString("file")));Bitmap bitmap=a.media.photo(id);assertNotNull("Dedicated visual decodes: "+id,bitmap);assertEquals(512,bitmap.getWidth());assertEquals(512,bitmap.getHeight());}
        java.util.concurrent.atomic.AtomicInteger opens=new java.util.concurrent.atomic.AtomicInteger();a.media.connections=url->{opens.incrementAndGet();throw new IOException("Dedicated visual must stay offline");};a.openMemory("hypothesis");layout();assertEquals(0,opens.get());assertTrue(a.media.hasDedicatedVisual("hypothesis"));TextView title=a.root.findViewWithTag("pageTitle");assertTrue(title.getText().toString().startsWith("专属图卡"));boolean cropped=false;for(int i=0;i<a.root.getChildCount();i++)if(a.root.getChildAt(i) instanceof ImageView)cropped|=((ImageView)a.root.getChildAt(i)).getScaleType()==ImageView.ScaleType.CENTER_CROP;assertTrue(cropped);shot("38-dedicated-visual");a.screen="session";a.session=new Engine.Session();a.session.books=a.activeBook;a.session.learn.add("hypothesis");a.session.learning=0;a.render();shot("39-dedicated-study");assertEquals(0,opens.get());
    }
    @Test public void canceledSlowMetadataDisconnectsAndTruncatedDataIsRejected()throws Exception{
        a.prefs.edit().putBoolean("online",true).commit();java.util.concurrent.CountDownLatch reading=new java.util.concurrent.CountDownLatch(1),released=new java.util.concurrent.CountDownLatch(1);
        FakeConnection blocked=new FakeConnection(new java.net.URL("https://dict.youdao.com/jsonapi"),new byte[20]){
            @Override public InputStream getInputStream(){return new InputStream(){public int read()throws IOException{reading.countDown();while(released.getCount()>0){try{released.await(50,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}}throw new IOException("Disconnected");}};}
            @Override public void disconnect(){super.disconnect();released.countDown();}
        };
        a.media.connections=u->blocked;java.util.List<Boolean> messages=new java.util.ArrayList<>();a.media.phones("ambitious",messages::add);assertTrue(reading.await(3,java.util.concurrent.TimeUnit.SECONDS));a.media.pageChanged();assertTrue(released.await(3,java.util.concurrent.TimeUnit.SECONDS));shadowOf(Looper.getMainLooper()).idle();assertTrue(messages.isEmpty());assertTrue(blocked.disconnected);
        FakeConnection truncated=new FakeConnection(new java.net.URL("https://example.test/photo"),new byte[20]);truncated.declared=100;a.media.connections=u->truncated;
        try{a.media.download("https://example.test/photo",1500000);fail("Incomplete responses must not be cached");}catch(IOException expected){}assertFalse(new File(a.media.cache,MediaLibrary.hash("https://example.test/photo")).exists());
    }
    @Test public void studyStaysOfflineAndBackgroundClosesEveryNetworkWorker()throws Exception{
        a.prefs.edit().putBoolean("online",true).commit();java.util.concurrent.atomic.AtomicInteger opens=new java.util.concurrent.atomic.AtomicInteger();
        a.media.connections=url->{opens.incrementAndGet();throw new IOException("Unexpected automatic network request");};a.start(false);a.session.learning=a.session.learn.size();a.render();a.openSound(a.session.current());layout();assertEquals("Study and opening the sound card stay offline until Play is tapped",0,opens.get());assertNull(a.media.phoneTask);assertNull(a.media.requests);
        java.util.concurrent.CountDownLatch reading=new java.util.concurrent.CountDownLatch(1),released=new java.util.concurrent.CountDownLatch(1);FakeConnection blocked=new FakeConnection(new java.net.URL("https://dict.youdao.com/jsonapi"),new byte[20]){@Override public InputStream getInputStream(){return new InputStream(){public int read()throws IOException{reading.countDown();while(released.getCount()>0){try{released.await(50,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}}throw new IOException("Disconnected");}};}@Override public void disconnect(){super.disconnect();released.countDown();}};
        a.media.connections=url->{opens.incrementAndGet();return blocked;};a.media.phones("background-network-test",ok->fail("Background callback must be suppressed"));assertTrue(reading.await(3,java.util.concurrent.TimeUnit.SECONDS));a.onPause();a.onStop();assertTrue(released.await(3,java.util.concurrent.TimeUnit.SECONDS));assertTrue(blocked.disconnected);assertFalse(a.media.foreground);assertNull(a.media.requests);assertNull(a.media.disconnects);assertNull(a.media.player);a.onResume();assertTrue(a.media.foreground);assertEquals("Resume and render still do not wake Wi-Fi",1,opens.get());
    }
    Engine.Progress fixture(int index,boolean wrong,int lapses,String day)throws Exception{Engine.Word target=a.activeWords().get(index);assertNotNull(target);Engine.Progress p=new Engine.Progress(target.id);p.trouble=wrong;p.lapses=lapses;p.attempts=1;p.firstDay=day;p.lastDay=day;p.due=System.currentTimeMillis()+Engine.DAY;a.progress.put(p.id,p);a.store.save(a.session,p);return p;}
    void click(TextView view){a.lastAction=-1000;view.performClick();}
    @Test public void filteredFlashcardsPreserveQuizAndPrioritizeDifficultWords()throws Exception{
        String today=Engine.today(System.currentTimeMillis()),yesterday=Engine.today(System.currentTimeMillis()-Engine.DAY);Engine.Progress p=fixture(0,true,2,today),q=fixture(1,true,5,yesterday),r=fixture(2,false,0,today);
        a.start(false);String before=Store.encode(a.session).toString();assertEquals(2,a.flashCandidates(1).size());assertEquals(q.id,a.flashCandidates(1).get(0).id);assertEquals(2,a.flashCandidates(2).size());assertEquals(a.activeWordCount(),a.flashCandidates(0).size());
        a.openFlash(1,"review");assertEquals(q.id,a.flashWords.get(0).id);assertEquals(2,a.flashWords.size());a.moveFlash(1);a.flipped=true;shot("17-wrong-flash");assertEquals(before,Store.encode(a.session).toString());assertEquals(1,a.store.progress().get(p.id).attempts);a.back();assertEquals("review",a.screen);shot("18-review");
        a.openFlash(2,"flashmenu");assertTrue(a.flashIds().contains(p.id));assertTrue(a.flashIds().contains(r.id));assertFalse(a.flashIds().contains(q.id));assertNull(a.media.player);
    }
    @Test public void shuffledCardsRememberWordPerScopeAndReturnWithoutCheating()throws Exception{
        String today=Engine.today(System.currentTimeMillis());fixture(0,true,2,today);fixture(1,true,3,today);a.start(false);String before=Store.encode(a.session).toString();a.flashShuffle=true;a.flashSeed=42;
        a.openFlash(0,"flashmenu");java.util.List<String> order=a.flashIds();assertEquals(a.activeWordCount(),new java.util.HashSet<>(order).size());a.moveFlash(7);String last=a.flashWords.get(a.flashAt).id;
        a.openFlash(1,"review");a.moveFlash(1);String wrongLast=a.flashWords.get(a.flashAt).id;a.openFlash(0,"flashmenu");assertEquals(order,a.flashIds());assertEquals(last,a.flashWords.get(a.flashAt).id);assertFalse(a.flipped);
        a.openFlash(1,"review");assertEquals(wrongLast,a.flashWords.get(a.flashAt).id);a.flipped=true;layout();for(int i=0;i<a.root.getChildCount();i++){View v=a.root.getChildAt(i);if(v instanceof TextView&&((TextView)v).getText().toString().equals("再想想")){click((TextView)v);break;}}assertFalse(a.flipped);assertEquals(before,Store.encode(a.session).toString());
        a.flashMode=0;a.screen="flashmenu";layout();a.rotate(1);assertEquals(1,a.flashMode);a.rotate(1);assertEquals(2,a.flashMode);a.rotate(1);assertEquals(4,a.flashMode);a.rotate(1);assertEquals(0,a.flashMode);shot("19-flash-menu");
    }
    @Test public void emptyFlashAndBusyReviewDoNotLoseTheCurrentGroup()throws Exception{
        a.start(false);String before=Store.encode(a.session).toString();a.openFlash(1,"review");assertTrue(a.flashWords.isEmpty());a.rotate(1);a.rotate(-1);assertEquals(0,a.flashAt);a.back();assertEquals("review",a.screen);a.rotate(1);assertEquals(0,a.reviewFocus);a.rotate(1);assertEquals(1,a.reviewFocus);a.lastAction=-1000;a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));assertEquals("session",a.screen);assertEquals(before,Store.encode(a.session).toString());
        a.openFlash(2,"flashmenu");assertTrue(a.flashWords.isEmpty());shot("20-empty-flash");a.back();assertEquals("flashmenu",a.screen);assertEquals(before,Store.encode(a.session).toString());
    }
    @Test public void mistakesShowTheActualChoiceAndLongContrastsFit()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();String id=a.session.current();int wrong=(a.session.options.indexOf(id)+1)%3;String chosen=a.session.options.get(wrong);a.answer(wrong);layout();TextView contrast=a.root.findViewWithTag("mistakeContrast");assertNotNull(contrast);assertTrue(contrast.getText().toString().contains(a.byId.get(chosen).meaning));assertTrue(a.session.feedback);shot("21-contrast");if(a.media.hasPhoto(id)||!a.media.mnemonic(id).isEmpty()){click(contrast);assertEquals("memory",a.screen);a.back();assertEquals("session",a.screen);assertTrue(a.session.feedback);}
        java.util.List<Engine.Word> sorted=new java.util.ArrayList<>(a.words);sorted.sort((x,y)->Integer.compare(y.meaning.length(),x.meaning.length()));for(int i=0;i<40;i++){a.session.chosen=sorted.get(i).id;layout();contrast=a.root.findViewWithTag("mistakeContrast");android.text.Layout l=contrast.getLayout();assertTrue(l.getHeight()<=contrast.getHeight()-contrast.getPaddingTop()-contrast.getPaddingBottom());for(int j=0;j<l.getLineCount();j++)assertEquals("Full mistake contrast: "+contrast.getText(),0,l.getEllipsisCount(j));}
    }
    @Test public void completedGroupReviewContainsEachMistakeOnce()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();String wrong=a.session.current();a.answer((a.session.options.indexOf(wrong)+1)%3);
        while(!a.session.complete()){a.nextAnswer();if(!a.session.complete()){a.render();a.answer(a.session.options.indexOf(a.session.current()));}}
        String before=Store.encode(a.session).toString();int attempts=a.store.progress().get(wrong).attempts;shot("22-complete-review");a.openFlash(3,"session");assertEquals(1,a.flashWords.size());assertEquals(wrong,a.flashWords.get(0).id);a.moveFlash(1);a.flipped=true;a.render();a.back();assertEquals("session",a.screen);assertEquals(before,Store.encode(a.session).toString());assertEquals(attempts,a.store.progress().get(wrong).attempts);
        a.finishGroup();assertNull(a.session);assertNull(a.store.session());
    }
    @Test public void activityRecreationRestoresNestedCardsAndQuizProgress()throws Exception{
        a.start(false);String before=Store.encode(a.session).toString();a.flashShuffle=true;a.flashSeed=91;a.openFlash(0,"flashmenu");a.moveFlash(9);a.flipped=true;String id=a.flashWords.get(a.flashAt).id;java.util.List<String> order=a.flashIds();a.openMemory(id);a.openSound(id);
        android.os.Bundle state=new android.os.Bundle();controller.pause().saveInstanceState(state).stop().destroy();controller=null;
        controller=Robolectric.buildActivity(MainActivity.class).setup(state);a=controller.get();for(int i=0;i<100&&!a.ready;i++){Thread.sleep(20);shadowOf(Looper.getMainLooper()).idle();}assertTrue(a.ready);a.media.connections=url->{throw new IOException("Real networking disabled in tests");};layout();
        assertEquals("sound",a.screen);assertEquals(id,a.soundId);assertNull(a.media.player);assertEquals(before,Store.encode(a.session).toString());a.back();assertEquals("memory",a.screen);assertEquals(id,a.memoryId);a.back();assertEquals("flash",a.screen);assertTrue(a.flipped);assertEquals(order,a.flashIds());assertEquals(id,a.flashWords.get(a.flashAt).id);a.back();assertEquals("flashmenu",a.screen);
    }
    @Test public void recallRequiresRevealAndRotationNeverGrades()throws Exception{
        a.recallMode=true;a.batchSize=5;a.start(false);assertTrue(a.session.recall);assertEquals(5,a.session.learn.size());a.session.learning=a.session.learn.size();a.render();String id=a.session.current();assertTrue(a.answerViews.isEmpty());a.gradeRecall(true);assertFalse(a.session.feedback);assertFalse(a.progress.containsKey(id));a.rotate(1);assertFalse(a.session.revealed);shot("23-recall-hidden");
        a.revealRecall();assertTrue(a.session.revealed);a.rotate(1);assertEquals(0,a.session.focus);a.rotate(1);assertEquals(1,a.session.focus);assertFalse(a.session.feedback);shot("24-recall-revealed");a.gradeRecall(false);assertTrue(a.session.feedback);assertTrue(a.progress.get(id).trouble);assertEquals(1,a.progress.get(id).attempts);
        Engine.Session saved=a.store.session();assertTrue(saved.recall);assertTrue(saved.revealed);assertTrue(saved.feedback);a.nextAnswer();assertFalse(a.session.revealed);assertTrue(a.recallViews.isEmpty());
        while(!a.session.complete()){a.revealRecall();a.gradeRecall(true);a.nextAnswer();}assertEquals(4,a.session.correct);assertEquals(5,a.session.answered.size());assertEquals(1,a.progress.get(id).attempts);shot("25-recall-complete");
    }
    @Test public void groupPreferencesApplyOnlyToTheNextGroup()throws Exception{
        a.start(false);String saved=Store.encode(a.session).toString();a.screen="studysettings";layout();java.util.List<TextView> buttons=new java.util.ArrayList<>();for(int i=0;i<a.root.getChildCount();i++){View v=a.root.getChildAt(i);if(v instanceof TextView&&v.isClickable())buttons.add((TextView)v);}click(buttons.get(0));assertTrue(a.recallMode);assertFalse(a.session.recall);assertEquals(saved,Store.encode(a.session).toString());
        a.batchSize=5;a.prefs.edit().putInt("batchSize",5).commit();shot("26-study-settings");a.finishGroup();a.start(false);assertEquals(5,a.session.queue.size());assertTrue(a.session.recall);
    }
    @Test public void favoritesArePersistentAndDoNotRevealAnUnansweredMeaning()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();String id=a.session.current(),before=Store.encode(a.session).toString();TextView star=a.root.findViewWithTag("favoriteButton");assertNotNull(star);click(star);assertTrue(a.favorites.contains(id));assertTrue(a.prefs.getStringSet("favorites",java.util.Collections.emptySet()).contains(id));assertEquals(before,Store.encode(a.session).toString());
        a.openWordTools(id);assertFalse(a.toolsCanShowMemory());shot("27-word-tools");a.back();assertEquals("session",a.screen);a.openFlash(4,"settings");assertEquals(1,a.flashWords.size());assertEquals(id,a.flashWords.get(0).id);a.flipped=true;shot("28-favorites");a.toggleFavorite(id);assertFalse(a.favorites.contains(id));a.openFlash(4,"settings");assertTrue(a.flashWords.isEmpty());a.rotate(1);assertEquals(before,Store.encode(a.session).toString());assertNull(a.media.player);
        a.toggleFavorite(id);controller.pause().stop().destroy();controller=null;open();assertTrue(a.favorites.contains(id));assertEquals(before,Store.encode(a.session).toString());
    }
    @Test public void dailyHistoryCountsEachWordOnceAndKeepsItsFirstResult()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();String id=a.session.current();a.answer((a.session.options.indexOf(id)+1)%3);String today=Engine.today(System.currentTimeMillis());int[] first=a.store.practice().get(today);assertArrayEquals(new int[]{1,0},first);
        Engine.Progress p=a.progress.get(id);p.trouble=false;p.stage=1;p.attempts++;a.store.save(a.session,p);assertArrayEquals(new int[]{1,0},a.store.practice().get(today));a.store.save(a.session,p);assertArrayEquals(new int[]{1,0},a.store.practice().get(today));
        a.screen="stats";a.statsPage=0;shot("29-weekly");a.rotate(1);assertEquals(1,a.statsPage);shot("30-progress");a.rotate(1);assertEquals(2,a.statsPage);a.rotate(1);assertEquals(3,a.statsPage);shot("31-schedule");a.rotate(1);assertEquals(0,a.statsPage);
    }
    @Test public void forgettingCurveForecastRestoresWithoutChangingTheGroup()throws Exception{
        a.start(false);a.session.learning=a.session.learn.size();a.render();a.answer(a.session.options.indexOf(a.session.current()));String before=Store.encode(a.session).toString();
        a.statsReturn="session";a.screen="stats";a.statsPage=2;layout();MemoryCurveView curve=null;for(int i=0;i<a.root.getChildCount();i++)if(a.root.getChildAt(i) instanceof MemoryCurveView)curve=(MemoryCurveView)a.root.getChildAt(i);
        assertNotNull(curve);assertTrue(curve.getContentDescription().toString().contains("百分之85"));assertEquals(before,Store.encode(a.session).toString());shot("35-forgetting-curve");
        android.os.Bundle state=new android.os.Bundle();controller.pause().saveInstanceState(state).stop().destroy();controller=null;controller=Robolectric.buildActivity(MainActivity.class).setup(state);a=controller.get();for(int i=0;i<100&&!a.ready;i++){Thread.sleep(20);shadowOf(Looper.getMainLooper()).idle();}assertTrue(a.ready);a.media.connections=u->{throw new IOException("Real networking disabled in tests");};layout();
        assertEquals("stats",a.screen);assertEquals(2,a.statsPage);assertEquals(before,Store.encode(a.session).toString());a.rotate(1);assertEquals(3,a.statsPage);shot("36-review-plan");a.back();assertEquals("session",a.screen);
    }
    @Test public void databaseUpgradePreservesV1ProgressAndOldSessions()throws Exception{
        a.start(false);Engine.Session original=a.session;org.json.JSONObject json=Store.encode(original);json.remove("recall");json.remove("revealed");
        android.database.sqlite.SQLiteOpenHelper legacy=new android.database.sqlite.SQLiteOpenHelper(a,"legacy-test.db",null,1){public void onCreate(android.database.sqlite.SQLiteDatabase db){db.execSQL("CREATE TABLE progress(id TEXT PRIMARY KEY,stage INTEGER,due INTEGER,lapses INTEGER,attempts INTEGER,firstDay TEXT,lastDay TEXT,trouble INTEGER)");db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY CHECK(id=1),json TEXT NOT NULL)");}public void onUpgrade(android.database.sqlite.SQLiteDatabase db,int x,int y){}};
        android.database.sqlite.SQLiteDatabase db=legacy.getWritableDatabase();String id=a.words.get(0).id;db.execSQL("INSERT INTO progress VALUES(?,?,?,?,?,?,?,?)",new Object[]{id,3,123456789L,2,7,"2026-11-01","2026-11-02",1});db.execSQL("INSERT INTO state VALUES(1,?)",new Object[]{json.toString()});legacy.close();
        Store upgraded=new Store(a,"legacy-test.db");try{assertEquals(2,upgraded.getReadableDatabase().getVersion());Engine.Progress p=upgraded.progress().get(id);assertEquals(7,p.attempts);assertEquals(123456789L,p.due);assertTrue(p.trouble);Engine.Session restored=upgraded.session();assertEquals(original.queue,restored.queue);assertFalse(restored.recall);assertFalse(restored.revealed);assertTrue(upgraded.practice().isEmpty());upgraded.save(restored,p);assertArrayEquals(new int[]{1,0},upgraded.practice().get("2026-11-02"));}finally{upgraded.close();}
    }
    @Test public void separateHapticLevelsKeepResultsStrongAndCanSilenceNavigation()throws Exception{
        android.os.Vibrator vibrator=a.getSystemService(android.os.VibratorManager.class).getDefaultVibrator();org.robolectric.shadows.ShadowVibrator shadow=shadowOf(vibrator);shadow.setHasVibrator(true);shadow.setHasAmplitudeControl(true);android.provider.Settings.System.putInt(a.getContentResolver(),android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,1);a.getSystemService(android.app.NotificationManager.class).setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
        Haptics h=new Haptics(a);h.foreground=true;h.setStrength(3);h.setNavigationStrength(0);h.tick();assertFalse(shadow.isVibrating());h.correct();assertNotNull(shadow.getPattern());assertTrue(java.util.Arrays.stream(shadow.getPattern()).sum()>=140);h.cancel();h.setStrength(0);h.setNavigationStrength(3);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));h.tick();assertTrue(shadow.isVibrating());h.cancel();h.correct();assertFalse(shadow.isVibrating());h.bookmark();assertTrue(shadow.isVibrating());h.cancel();
        a.screen="touch";a.haptics.setStrength(3);a.haptics.setNavigationStrength(1);shot("32-touch-settings");a.touchFocus=1;a.rotate(1);assertEquals(2,a.haptics.navigationStrength);assertEquals(3,a.haptics.strength);
    }
    @Test public void authoredNotesFitAndKeepPhotoAttributionReachable()throws Exception{
        int count=0;for(Engine.Word word:a.words)if(a.media.hasNote(word.id)){a.openMemory(word.id);a.memoryPage=a.memoryKinds().indexOf("note");layout();TextView note=a.root.findViewWithTag("memoryNote");assertNotNull(note);android.text.Layout l=note.getLayout();assertTrue("Note fits: "+word.id,l.getHeight()<=note.getHeight());for(int j=0;j<l.getLineCount();j++)assertEquals("Whole note: "+word.id,0,l.getEllipsisCount(j));if(a.media.hasPhoto(word.id))assertEquals("credit",a.memoryKinds().get(a.memoryKinds().size()-1));count++;}
        assertEquals(50,count);a.openMemory("competence");a.memoryPage=a.memoryKinds().indexOf("note");shot("33-memory-note");assertNull(a.media.player);
    }
    @Test public void recallRevealAndToolsSurviveActivityRecreation()throws Exception{
        a.recallMode=true;a.batchSize=5;a.start(false);a.session.learning=a.session.learn.size();a.render();a.revealRecall();String id=a.session.current();a.openWordTools(id);assertTrue(a.toolsCanShowMemory());android.os.Bundle state=new android.os.Bundle();controller.pause().saveInstanceState(state).stop().destroy();controller=null;controller=Robolectric.buildActivity(MainActivity.class).setup(state);a=controller.get();for(int i=0;i<100&&!a.ready;i++){Thread.sleep(20);shadowOf(Looper.getMainLooper()).idle();}assertTrue(a.ready);a.media.connections=u->{throw new IOException("Real networking disabled in tests");};layout();assertEquals("wordtools",a.screen);assertEquals(id,a.toolsId);assertTrue(a.session.recall);assertTrue(a.session.revealed);assertTrue(a.toolsCanShowMemory());a.back();assertEquals("session",a.screen);assertEquals(2,a.recallViews.size());assertNull(a.media.player);
    }
    @Test public void largerSystemTextDoesNotClipPrimaryActions()throws Exception{
        android.content.res.Configuration config=new android.content.res.Configuration(a.getResources().getConfiguration());config.fontScale=1.3f;a.getResources().updateConfiguration(config,a.getResources().getDisplayMetrics());
        a.screen="home";layout();assertButtonsFit();a.start(false);a.session.learning=a.session.learn.size();a.render();a.toggleFavorite(a.session.current());a.openWordTools(a.session.current());layout();assertButtonsFit();shot("34-large-text-tools");
        a.screen="studysettings";layout();assertButtonsFit();a.screen="touch";layout();assertButtonsFit();a.screen="session";a.session.recall=true;a.session.revealed=true;a.render();layout();assertButtonsFit();
    }
    void assertButtonsFit(){for(int i=0;i<a.root.getChildCount();i++){View view=a.root.getChildAt(i);if(view instanceof TextView&&view.isClickable()){TextView t=(TextView)view;android.text.Layout l=t.getLayout();assertNotNull(l);assertTrue("Action height fits: "+t.getText(),l.getHeight()<=t.getHeight()-t.getPaddingTop()-t.getPaddingBottom());for(int row=0;row<l.getLineCount();row++)assertEquals("Action text visible: "+t.getText(),0,l.getEllipsisCount(row));}}}
    @Test public void booksStaySeparateAndSwitchWithoutResettingProgressOrCurrentGroup()throws Exception{
        long original=a.activeBook;BookCatalog.Book originalBook=BookCatalog.find(original),chosen=null;for(BookCatalog.Book book:BookCatalog.forLevel(originalBook.level))if(book.bit!=original){chosen=book;break;}assertNotNull(chosen);
        final BookCatalog.Book selectedBook=chosen;Engine.Progress learned=fixture(0,false,0,Engine.today(System.currentTimeMillis()));a.start(false);long groupBook=a.session.books;String current=a.session.current();a.screen="books";layout();shot("37-word-books");
        a.selectBook(selectedBook);assertEquals(selectedBook.bit,a.activeBook);assertEquals(selectedBook.headwords,a.activeWordCount());assertEquals(groupBook,a.session.books);assertEquals(current,a.session.current());assertEquals(selectedBook.bit,a.prefs.getLong("activeBook",0));assertNotNull(a.progress.get(learned.id));assertEquals(originalBook.headwords,a.wordBooks.get(original).size());assertEquals(selectedBook.headwords,a.wordBooks.get(selectedBook.bit).size());
        a.session=null;a.start(false);assertEquals(selectedBook.bit,a.session.books);assertTrue(a.session.queue.stream().allMatch(id->Engine.inBooks(a.byId.get(id),selectedBook.bit)));a.activeBook=original;a.prefs.edit().putLong("activeBook",original).commit();
    }
    @Test public void invalidLegacySessionIsRejectedWithoutDeletingProgress()throws Exception{
        Engine.Progress kept=fixture(0,false,0,Engine.today(System.currentTimeMillis()));a.start(false);assertTrue(a.validSession(a.session));
        a.session.queue.set(0,"removed-from-new-dataset");assertFalse(a.validSession(a.session));assertSame(kept,a.progress.get(kept.id));
        a.session.queue.set(0,a.activeWords().get(1).id);a.session.at=a.session.queue.size()+1;assertFalse(a.validSession(a.session));
    }
    @Test public void settingsRotarySelectsWithoutChangingPreferences(){
        a.screen="studysettings";layout();int size=a.batchSize;boolean mode=a.recallMode;a.rotate(1);assertEquals(0,a.studyFocus);assertEquals(mode,a.recallMode);a.rotate(1);assertEquals(1,a.studyFocus);assertEquals(size,a.batchSize);a.lastAction=-1000;a.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));assertNotEquals(size,a.batchSize);
        a.screen="touch";layout();for(int i=0;i<a.root.getChildCount();i++){View v=a.root.getChildAt(i);if(v instanceof TextView&&((TextView)v).getText().toString().equals("体验全部节奏")){click((TextView)v);break;}}assertEquals("haptics",a.screen);a.back();assertEquals("touch",a.screen);a.statsReturn="settings";a.screen="stats";a.render();a.back();assertEquals("settings",a.screen);
    }
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
}
