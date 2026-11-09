package cn.wordhalo.watch;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Only explicit taps start sound. Media files never share the progress database directory. */
final class MediaLibrary {
    static final long CACHE_LIMIT=48L*1024*1024;
    final Context context;final SharedPreferences prefs;final File cache;
    final Handler main=new Handler(Looper.getMainLooper());
    ExecutorService requests,disconnects;
    final Map<String,JSONObject> phones=new ConcurrentHashMap<>();
    final Map<String,Long> retryAfter=new ConcurrentHashMap<>();
    final LruCache<String,Bitmap> bitmaps=new LruCache<>(8);
    JSONObject words=new JSONObject(),scenes=new JSONObject(),notes=new JSONObject(),visuals=new JSONObject(),photoCuration=new JSONObject();
    MediaPlayer player;TextToSpeech speech;AudioFocusRequest focus;Request phoneTask,photoTask;Runnable soundTimeout;
    boolean speechReady=false,forceStreamingForTests=false;int speechGeneration=0,speechAccent=0;
    String speechUtterance="";Consumer<String> speechStatus;final Set<Integer> failedLocalAccents=new HashSet<>();
    int soundToken=0;volatile int cacheGeneration=0;boolean foreground=false;volatile boolean closed=false;
    final AudioManager audio;
    interface ConnectionFactory {HttpURLConnection open(URL url)throws IOException;}
    ConnectionFactory connections=url->(HttpURLConnection)url.openConnection();

    /** A canceled request stays canceled even if its worker or UI callback arrives later. */
    final class Request {
        volatile boolean canceled;volatile Future<?> future;HttpURLConnection connection;
        synchronized void attach(HttpURLConnection c)throws InterruptedIOException{check();connection=c;}
        synchronized void detach(HttpURLConnection c){if(connection==c)connection=null;}
        void check()throws InterruptedIOException{if(canceled||closed||!foreground||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        void cancel(){HttpURLConnection c;synchronized(this){canceled=true;c=connection;connection=null;}Future<?> f=future;if(f!=null)f.cancel(true);
            // Disconnect may block on some implementations; never do it on the UI thread.
            if(c!=null)disconnect(c);
        }
        boolean current(){return !canceled&&!closed&&foreground;}
    }
    MediaLibrary(Context c,SharedPreferences p){context=c;prefs=p;cache=new File(c.getCacheDir(),"media-v2");cache.mkdirs();audio=c.getSystemService(AudioManager.class);}
    synchronized ExecutorService requestPool(){if(closed||!foreground)return null;if(requests==null||requests.isShutdown())requests=Executors.newFixedThreadPool(2);return requests;}
    synchronized ExecutorService disconnectPool(){if(disconnects==null||disconnects.isShutdown())disconnects=Executors.newCachedThreadPool();return disconnects;}
    void disconnect(HttpURLConnection connection){try{disconnectPool().execute(connection::disconnect);}catch(RejectedExecutionException ignored){try{connection.disconnect();}catch(Exception ignoredAgain){}}}
    void enterForeground(){if(!closed){foreground=true;initSpeech();}}
    void leaveForeground(){foreground=false;pageChanged();shutdownSpeech();ExecutorService requestPool,disconnectPool;synchronized(this){requestPool=requests;requests=null;disconnectPool=disconnects;disconnects=null;}if(requestPool!=null)requestPool.shutdownNow();if(disconnectPool!=null)disconnectPool.shutdown();}
    void initSpeech(){
        if(closed||!foreground||speech!=null||forceStreamingForTests)return;final int generation=++speechGeneration;speechReady=false;
        try{speech=new TextToSpeech(context,result->main.post(()->{
            TextToSpeech active=speech;if(closed||!foreground||generation!=speechGeneration||active==null)return;
            speechReady=result==TextToSpeech.SUCCESS;if(!speechReady)return;
            active.setSpeechRate(.93f);active.setPitch(1f);active.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String utteranceId){main.post(()->speechStarted(utteranceId));}
                @Override public void onDone(String utteranceId){main.post(()->speechFinished(utteranceId));}
                @Override public void onError(String utteranceId){main.post(()->speechFailed(utteranceId));}
                @Override public void onError(String utteranceId,int errorCode){main.post(()->speechFailed(utteranceId));}
            });
        }));}catch(Exception ignored){speech=null;speechReady=false;}
    }
    void shutdownSpeech(){speechGeneration++;speechReady=false;speechUtterance="";speechStatus=null;failedLocalAccents.clear();TextToSpeech old=speech;speech=null;if(old!=null)try{old.shutdown();}catch(Exception ignored){}}
    void load()throws Exception{words=asset("word-media.json");scenes=asset("scenes.json");notes=asset("memory-notes.json");visuals=asset("visual-mnemonics.json").optJSONObject("words");if(visuals==null)visuals=new JSONObject();photoCuration=asset("stock-photo-curation.json");}
    JSONObject asset(String name)throws Exception{try(InputStream in=context.getAssets().open(name)){return new JSONObject(new String(read(in,3_000_000),StandardCharsets.UTF_8));}}
    JSONObject word(String id){JSONObject w=words.optJSONObject(id);return w==null?new JSONObject():w;}
    JSONObject scene(String id){JSONObject exact=visuals.optJSONObject(id);if(exact!=null)return exact;String sceneId=word(id).optString("scene");JSONArray keep=photoCuration.optJSONArray(sceneId);if(keep!=null){boolean found=false;for(int i=0;i<keep.length();i++)if(id.equals(keep.optString(i))){found=true;break;}if(!found)return new JSONObject();}JSONObject s=scenes.optJSONObject(sceneId);return s==null?new JSONObject():s;}
    boolean hasDedicatedVisual(String id){return visuals.has(id);}
    boolean hasPhoto(String id){return !scene(id).optString("file").isEmpty();}
    String mnemonic(String id){return word(id).optString("mnemonic");}
    JSONObject note(String id){JSONObject value=notes.optJSONObject(id);return value==null?new JSONObject():value;}
    boolean hasNote(String id){return !note(id).optString("text").isEmpty();}
    boolean online(){return prefs.getBoolean("online",true);}
    String ipa(String id,int accent){JSONObject p=phones.get(id);return p==null?"":p.optString(accent==1?"uk":"us");}
    String genericIpa(String id){return word(id).optString("ipa");}
    Bitmap photo(String id){String file=scene(id).optString("file");if(file.isEmpty())return null;Bitmap b=bitmaps.get(file);if(b!=null)return b;
        try(InputStream in=context.getAssets().open(file)){byte[] bytes=read(in,1_500_000);b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b!=null)bitmaps.put(file,b);}catch(Exception ignored){}return b;
    }
    void phones(String id,Consumer<Boolean> callback){
        if(phoneTask!=null)phoneTask.cancel();if(closed)return;
        if(phones.containsKey(id)){callback.accept(true);return;}
        if(SystemClock.elapsedRealtime()<retryAfter.getOrDefault(id,0L)){callback.accept(false);return;}
        ExecutorService pool=requestPool();if(pool==null)return;Request task=new Request();phoneTask=task;try{task.future=pool.submit(()->{boolean ok=false;try{
            String url="https://dict.youdao.com/jsonapi?q="+encode(id)+"&dicts="+encode("{\"count\":1,\"dicts\":[[\"ec\"]]}");
            File f=download(url,300_000,task);JSONObject data=new JSONObject(new String(fileBytes(f,300_000),StandardCharsets.UTF_8));
            JSONObject entry=data.getJSONObject("ec").getJSONArray("word").getJSONObject(0);
            String head=entry.getJSONObject("return-phrase").getJSONObject("l").getString("i");
            if(!head.equalsIgnoreCase(id))throw new IOException("Different dictionary entry");
            JSONObject pair=new JSONObject();pair.put("uk",entry.optString("ukphone"));pair.put("us",entry.optString("usphone"));task.check();phones.put(id,pair);ok=true;
        }catch(Exception ignored){if(!task.canceled&&!Thread.currentThread().isInterrupted())retryAfter.put(id,SystemClock.elapsedRealtime()+60_000);}
        final boolean found=ok;main.post(()->{if(task.current()&&phoneTask==task)callback.accept(found);});});}catch(RejectedExecutionException ignored){task.cancel();if(phoneTask==task)phoneTask=null;}
    }
    void highPhoto(String id,Consumer<Bitmap> callback){
        if(photoTask!=null)photoTask.cancel();String url=scene(id).optString("url");if(closed||url.isEmpty())return;
        ExecutorService pool=requestPool();if(pool==null)return;Request task=new Request();photoTask=task;try{task.future=pool.submit(()->{Bitmap b=null;try{File f=download(url,1_500_000,task);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),bounds);if(bounds.outWidth>0&&bounds.outWidth<=2000&&bounds.outHeight>0&&bounds.outHeight<=2000){b=BitmapFactory.decodeFile(f.getPath());if(b==null)f.delete();}}catch(Exception ignored){}
            final Bitmap photo=b;main.post(()->{if(task.current()&&photoTask==task&&photo!=null)callback.accept(photo);});});}catch(RejectedExecutionException ignored){task.cancel();if(photoTask==task)photoTask=null;}
    }
    static String voiceUrl(String id,int accent){return "https://dict.youdao.com/dictvoice?audio="+encode(id)+"&type="+(accent==1?1:2);}
    String accentName(int accent){return accent==1?"英式":"美式";}
    Locale accentLocale(int accent){return accent==1?Locale.UK:Locale.US;}
    boolean chooseLocalVoice(int accent){
        TextToSpeech active=speech;if(!speechReady||active==null||failedLocalAccents.contains(accent))return false;Locale wanted=accentLocale(accent);
        try{Voice chosen=null;Set<Voice> voices=active.getVoices();if(voices!=null)for(Voice voice:voices){Locale locale=voice.getLocale();if(voice.isNetworkConnectionRequired()||locale==null||!wanted.getLanguage().equals(locale.getLanguage())||!wanted.getCountry().equals(locale.getCountry()))continue;if(chosen==null||voice.getQuality()>chosen.getQuality())chosen=voice;}if(voices!=null&&!voices.isEmpty()&&chosen==null){failedLocalAccents.add(accent);return false;}int support=chosen!=null?active.setVoice(chosen):active.setLanguage(wanted);if(support<TextToSpeech.LANG_AVAILABLE){failedLocalAccents.add(accent);return false;}return true;}catch(Exception ignored){failedLocalAccents.add(accent);return false;}
    }
    boolean requestSoundFocus(AudioAttributes attr,int token,Consumer<String> status){
        if(audio==null)return true;focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attr).setOnAudioFocusChangeListener(change->{if(change<0&&token==soundToken){status.accept("播放已暂停");stop();}}).build();return audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    void releaseSoundFocus(){if(audio!=null&&focus!=null)try{audio.abandonAudioFocusRequest(focus);}catch(Exception ignored){}focus=null;}
    boolean speakLocal(String id,int accent,Consumer<String> status,int token){
        TextToSpeech active=speech;if(!chooseLocalVoice(accent)||active==null)return false;
        AudioAttributes attr=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if(!requestSoundFocus(attr,token,status)){status.accept("暂时无法播放，请稍后再试");stop();return true;}
        active.setAudioAttributes(attr);active.setSpeechRate(accent==1?.91f:.94f);active.setPitch(accent==1?.98f:1f);
        String utterance="wordhalo-"+token+"-"+accent;speechUtterance=utterance;speechStatus=status;speechAccent=accent;
        Bundle params=new Bundle();params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,1f);
        int result=active.speak(id,TextToSpeech.QUEUE_FLUSH,params,utterance);if(result==TextToSpeech.ERROR){speechUtterance="";speechStatus=null;failedLocalAccents.add(accent);releaseSoundFocus();return false;}
        status.accept(accentName(accent)+" · 本机即时发声");return true;
    }
    void speechStarted(String utterance){if(utterance.equals(speechUtterance)&&speechStatus!=null&&foreground&&!closed)speechStatus.accept(accentName(speechAccent)+" · 本机即时发声");}
    void speechFinished(String utterance){if(!utterance.equals(speechUtterance)||speechStatus==null)return;Consumer<String> status=speechStatus;speechUtterance="";speechStatus=null;releaseSoundFocus();if(foreground&&!closed)status.accept("播放完毕 · 点按可重听");}
    void speechFailed(String utterance){if(!utterance.equals(speechUtterance)||speechStatus==null)return;Consumer<String> status=speechStatus;int accent=speechAccent;failedLocalAccents.add(accent);speechUtterance="";speechStatus=null;releaseSoundFocus();if(foreground&&!closed){status.accept("本机语音不可用 · 正在切换在线原声");stream(speakingWord,accent,status);}}
    String speakingWord="";
    /** Uses the pre-warmed system voice first, then streams as a fallback. Audio is never persisted. */
    void speak(String id,int accent,Consumer<String> status){
        stop();if(!foreground||closed)return;speakingWord=id;final int token=soundToken;
        if(!forceStreamingForTests&&speakLocal(id,accent,status,token))return;
        if(!forceStreamingForTests&&speech!=null&&!speechReady){status.accept("正在准备本机语音…");soundTimeout=()->{if(token!=soundToken||!foreground||closed)return;soundTimeout=null;if(!speakLocal(id,accent,status,token))stream(id,accent,status);};main.postDelayed(soundTimeout,450);return;}
        stream(id,accent,status);
    }
    void stream(String id,int accent,Consumer<String> status){
        if(!foreground||closed)return;if(!online()){status.accept("本机语音不可用 · 联网备用已关闭");return;}final int token=soundToken;
        status.accept("正在连接"+accentName(accent)+"在线原声…");
        try{
            AudioAttributes attr=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            MediaPlayer next=new MediaPlayer();player=next;next.setAudioAttributes(attr);
            Map<String,String> headers=new HashMap<>();headers.put("User-Agent","WordHaloPersonal/0.11");headers.put("Cache-Control","no-store");headers.put("android-allow-cross-domain-redirect","0");
            next.setDataSource(context,android.net.Uri.parse(voiceUrl(id,accent)),headers);
            next.setOnPreparedListener(mp->{if(!currentSound(mp,token))return;
                if(soundTimeout!=null)main.removeCallbacks(soundTimeout);
                if(!requestSoundFocus(attr,token,status)){status.accept("暂时无法播放，请稍后再试");stop();return;}
                try{mp.start();status.accept(accentName(accent)+" · 在线原声");soundTimeout=()->{if(token==soundToken){status.accept("播放已结束");stop();}};main.postDelayed(soundTimeout,12_000);}
                catch(Exception e){status.accept("读音暂不可用 · 点按重试");stop();}
            });
            next.setOnCompletionListener(mp->{if(currentSound(mp,token)){status.accept("播放完毕 · 点按可重听");stop();}});
            next.setOnErrorListener((mp,what,extra)->{if(currentSound(mp,token)){status.accept("网络或读音暂不可用 · 点按重试");stop();}return true;});
            next.prepareAsync();soundTimeout=()->{if(currentSound(next,token)){status.accept("连接超时 · 点按重试");stop();}};main.postDelayed(soundTimeout,15_000);
        }catch(Exception e){stop();status.accept("读音暂不可用 · 点按重试");}
    }
    boolean currentSound(MediaPlayer mp,int token){return player==mp&&token==soundToken&&foreground&&!closed;}
    void stop(){soundToken++;if(soundTimeout!=null){main.removeCallbacks(soundTimeout);soundTimeout=null;}speechUtterance="";speechStatus=null;if(speech!=null)try{speech.stop();}catch(Exception ignored){}if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}releaseSoundFocus();}
    void pageChanged(){stop();if(phoneTask!=null){phoneTask.cancel();phoneTask=null;}if(photoTask!=null){photoTask.cancel();photoTask=null;}ExecutorService pool=requests;if(pool instanceof ThreadPoolExecutor)((ThreadPoolExecutor)pool).purge();}
    /** Upgrade cleanup is limited to the old hashed UK/US audio files for this vocabulary. */
    void discardLegacyAudio(List<Engine.Word> vocabulary){
        if(prefs.getBoolean("streamingAudioCleanup",false))return;boolean ok=true;
        String[] names=cache.list();if(names==null)return;Set<String> remaining=new HashSet<>(Arrays.asList(names));
        try{for(Engine.Word word:vocabulary){if(remaining.isEmpty())break;for(int kind=1;kind<=2;kind++){String name=hash(voiceUrl(word.id,kind));for(String suffix:new String[]{"",".part"})if(remaining.remove(name+suffix)){File f=new File(cache,name+suffix);if(f.exists()&&!f.delete())ok=false;}}}}catch(Exception e){ok=false;}
        if(ok)prefs.edit().putBoolean("streamingAudioCleanup",true).apply();
    }
    void close(){closed=true;leaveForeground();shutdownSpeech();main.removeCallbacksAndMessages(null);bitmaps.evictAll();}
    long cacheBytes(){long n=0;File[] files=cache.listFiles();if(files!=null)for(File f:files)n+=f.length();return n;}
    void clearCache(Consumer<Boolean> done){pageChanged();cacheGeneration++;ExecutorService pool=requestPool();if(pool==null){done.accept(false);return;}try{pool.submit(()->{boolean ok=true;synchronized(this){File[] fs=cache.listFiles();if(fs!=null)for(File f:fs)ok=f.delete()&&ok;phones.clear();retryAfter.clear();}boolean result=ok;main.post(()->{if(!closed&&foreground)done.accept(result);});});}catch(RejectedExecutionException ignored){done.accept(false);}}
    File download(String url,int max)throws Exception{return download(url,max,new Request());}
    File download(String url,int max,Request task)throws Exception{
        final int generation=cacheGeneration;task.check();if(url.contains("/dictvoice?"))throw new IOException("Audio must stream directly");File target=new File(cache,hash(url));
        if(usable(target,max)){target.setLastModified(System.currentTimeMillis());return target;}
        if(!online())throw new IOException("Offline mode");URL address=new URL(url);if(!address.getProtocol().equals("https"))throw new IOException("HTTPS required");
        HttpURLConnection c=connections.open(address);
        try{task.attach(c);c.setConnectTimeout(5000);c.setReadTimeout(9000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","WordHaloPersonal/0.11 (Wear OS vocabulary)");
            int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);
            long length=c.getContentLengthLong();if(length>max)throw new IOException("Too large");
            byte[] data;try(InputStream in=c.getInputStream()){data=read(in,max);}
            if(data.length<16||length>=0&&length!=data.length)throw new IOException("Incomplete response");
            synchronized(this){task.check();if(!online()||cacheGeneration!=generation)throw new InterruptedIOException();
                if(usable(target,max))return target;
                File part=new File(cache,target.getName()+".part");try{try(FileOutputStream out=new FileOutputStream(part)){out.write(data);}if(target.exists()&&!target.delete())throw new IOException("Cannot replace invalid cache");if(!part.renameTo(target))throw new IOException("Cache write failed");}finally{if(part.exists())part.delete();}
                trim();return target;
            }
        }finally{task.detach(c);c.disconnect();}
    }
    static boolean usable(File file,int max){long length=file.length();return file.isFile()&&length>=16&&length<=max;}
    void trim(){File[] files=cache.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=cacheBytes();for(File f:files){if(total<=CACHE_LIMIT)break;long size=f.length();if(f.delete())total-=size;}}
    static byte[] read(InputStream in,int max)throws IOException{long deadline=SystemClock.elapsedRealtime()+60_000;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||SystemClock.elapsedRealtime()>deadline)throw new InterruptedIOException();if(out.size()+n>max)throw new IOException("Response too large");out.write(buffer,0,n);}return out.toByteArray();}
    static byte[] fileBytes(File f,int max)throws IOException{try(InputStream in=new FileInputStream(f)){return read(in,max);}}
    static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
    static String hash(String text)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));char[] hex="0123456789abcdef".toCharArray(),out=new char[d.length*2];for(int i=0;i<d.length;i++){out[i*2]=hex[(d[i]&255)>>>4];out[i*2+1]=hex[d[i]&15];}return new String(out);}
}
