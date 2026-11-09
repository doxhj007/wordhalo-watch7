package cn.wordhalo.watch;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import android.animation.*;
import android.view.animation.*;
import org.json.JSONObject;
import java.util.*;

/** A native, offline Wear OS app. All controls are real accessible Android Views. */
public final class MainActivity extends Activity {
    static final int BG=0xff000000, INK=0xfff1f5f2, MUTED=0xffa6b6ad, MINT=0xffb8f6d2, PANEL=0xff14221c, RED=0xffffb6aa;
    List<Engine.Word> words=new ArrayList<>(), flashWords=new ArrayList<>();
    Map<Long,List<Engine.Word>> wordBooks=new HashMap<>();
    Map<String,Engine.Word> byId=new HashMap<>();Map<String,Engine.Progress> progress;
    Store store;SharedPreferences prefs;Haptics haptics;Engine.Session session;Engine.Progress pendingProgress;
    Dial root;ScrollView scroll;ArrayList<TextView> answerViews=new ArrayList<>();
    String screen="home";int limit=20,flashAt=0,bookLevel=6;long activeBook=BookCatalog.defaultBook();boolean pictures=true,flipped=false,ready=false,saveFailed=false;
    float unit=1f;long lastAction=0;boolean active=false;
    MediaLibrary media;FocusHalo halo;View focusedCard;Handler ui=new Handler(Looper.getMainLooper());
    final ArrayList<TextView> homeViews=new ArrayList<>();
    int homeFocus=-1,hapticAt=3,settingsY=0,memoryPage=0,accent=1;
    String memoryId="",memoryReturn="home",pronunciationWord="";TextView phonetic,audioStatus;
    boolean motion=true;Runnable focusSave=()->persist(null);
    String soundId="",soundReturn="home";int soundFocus=1;
    final ArrayList<TextView> soundViews=new ArrayList<>();
    ValueAnimator scrollMotion;int scrollTarget;
    int flashMode=0,reviewFocus=-1;boolean flashShuffle=false;long flashSeed=0;
    String flashReturn="flashmenu";final ArrayList<TextView> reviewViews=new ArrayList<>();
    final Set<String> favorites=new HashSet<>();int batchSize=10,statsPage=0,touchFocus=0;boolean recallMode=false;
    String toolsId="",toolsReturn="home",statsReturn="home",hapticsReturn="settings";int studyFocus=-1;final ArrayList<TextView> recallViews=new ArrayList<>(),studyViews=new ArrayList<>(),bookViews=new ArrayList<>();

    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarColor(BG);getWindow().setStatusBarColor(BG);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);limit=prefs.getInt("limit",20);pictures=prefs.getBoolean("pictures",true);activeBook=BookCatalog.sanitize(prefs.getLong("activeBook",BookCatalog.defaultBook()));bookLevel=prefs.getInt("bookLevel",BookCatalog.find(activeBook).level)==4?4:6;
        motion=prefs.getBoolean("motion",true);flashMode=validFlashMode(prefs.getInt("flashMode",0));flashShuffle=prefs.getBoolean("flashShuffle",false);flashSeed=prefs.getLong("flashSeed",0);haptics=new Haptics(this);haptics.setStrength(prefs.getInt("haptics",3));haptics.setNavigationStrength(prefs.getInt("navigationHaptics",haptics.strength));favorites.addAll(prefs.getStringSet("favorites",Collections.emptySet()));batchSize=prefs.getInt("batchSize",10);if(batchSize!=5&&batchSize!=10&&batchSize!=20)batchSize=10;recallMode=prefs.getBoolean("recallMode",false);store=new Store(this);media=new MediaLibrary(this,prefs);
        root=new Dial();setContentView(root);
        WindowInsetsController ctl=getWindow().getInsetsController();if(ctl!=null)ctl.hide(WindowInsets.Type.systemBars());
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::back);
        new Thread(()->{try{
            List<Engine.Word> loaded=Store.words(this);Map<Long,List<Engine.Word>> loadedBooks=Store.bookWords(this,loaded);media.load();media.discardLegacyAudio(loaded);Map<String,Engine.Progress> p=store.progress();Engine.Session s=store.session();
            runOnUiThread(()->{if(isFinishing()||isDestroyed()){store.close();return;}words=loaded;wordBooks=loadedBooks;progress=p;session=s;for(Engine.Word w:words)byId.put(w.id,w);if(!validSession(session))session=null;favorites.retainAll(byId.keySet());ready=true;if(state!=null)restorePage(state);render();});
        }catch(Exception e){runOnUiThread(()->{screen="error";render();});}},"load-vocabulary").start();
    }
    @Override protected void onResume(){super.onResume();active=true;if(haptics!=null)haptics.foreground=true;if(media!=null)media.enterForeground();if(ready)render();}
    @Override protected void onPause(){active=false;haptics.foreground=false;haptics.cancel();media.leaveForeground();stopScroll();if(halo!=null)halo.stop();ui.removeCallbacksAndMessages(null);persist(null);super.onPause();}
    @Override protected void onStop(){if(media!=null)media.leaveForeground();super.onStop();}
    @Override protected void onDestroy(){media.close();if(ready)store.close();super.onDestroy();}
    @Override public void onBackPressed(){back();}
    void back(){if(screen.equals("home")){finish();return;}haptics.press();if(screen.equals("sound"))screen=soundReturn;else if(screen.equals("memory"))screen=memoryReturn;else if(screen.equals("flash"))screen=flashReturn;else if(screen.equals("wordtools"))screen=toolsReturn;else if(screen.equals("stats"))screen=statsReturn;else if(screen.equals("haptics"))screen=hapticsReturn;else if(screen.equals("about")||screen.equals("mnemonics")||screen.equals("flashmenu")||screen.equals("studysettings")||screen.equals("touch")||screen.equals("books"))screen="settings";else screen="home";persist(null);render();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("pageReady",ready);if(!ready)return;state.putString("page",screen);state.putString("toolsId",toolsId);state.putString("toolsReturn",toolsReturn);state.putInt("statsPage",statsPage);state.putString("statsReturn",statsReturn);state.putString("hapticsReturn",hapticsReturn);state.putString("soundId",soundId);state.putString("soundReturn",soundReturn);state.putInt("accent",accent);state.putInt("soundFocus",soundFocus);state.putString("memoryId",memoryId);state.putString("memoryReturn",memoryReturn);state.putInt("memoryPage",memoryPage);state.putString("flashReturn",flashReturn);state.putInt("flashMode",flashMode);state.putStringArrayList("flashIds",flashIds());state.putInt("flashAt",flashAt);state.putBoolean("flipped",flipped);state.putInt("settingsY",settingsY);persist(null);}
    ArrayList<String> flashIds(){ArrayList<String> ids=new ArrayList<>();for(Engine.Word word:flashWords)ids.add(word.id);return ids;}
    void restorePage(Bundle state){if(!state.getBoolean("pageReady"))return;screen=state.getString("page","home");toolsId=state.getString("toolsId","");toolsReturn=state.getString("toolsReturn","home");statsPage=Math.max(0,Math.min(3,state.getInt("statsPage",0)));statsReturn=state.getString("statsReturn","home");hapticsReturn=state.getString("hapticsReturn","settings");soundId=state.getString("soundId","");soundReturn=state.getString("soundReturn","home");accent=Math.max(1,Math.min(2,state.getInt("accent",1)));soundFocus=Math.max(1,Math.min(2,state.getInt("soundFocus",accent)));memoryId=state.getString("memoryId","");memoryReturn=state.getString("memoryReturn","home");memoryPage=state.getInt("memoryPage",0);flashReturn=state.getString("flashReturn","flashmenu");flashMode=Math.max(0,Math.min(4,state.getInt("flashMode",0)));flashWords.clear();ArrayList<String> ids=state.getStringArrayList("flashIds");if(ids!=null)for(String id:ids)if(byId.containsKey(id))flashWords.add(byId.get(id));flashAt=Math.max(0,Math.min(state.getInt("flashAt",0),flashWords.size()-1));flipped=state.getBoolean("flipped",false);settingsY=Math.max(0,state.getInt("settingsY",0));}
    void persist(Engine.Progress p){if(!ready)return;if(p!=null)pendingProgress=p;try{store.save(session,pendingProgress);pendingProgress=null;saveFailed=false;}catch(Exception e){saveFailed=true;Toast.makeText(this,"保存失败，请检查手表可用空间",Toast.LENGTH_LONG).show();}}
    boolean tap(){long now=SystemClock.elapsedRealtime();if(now-lastAction<330)return false;lastAction=now;return true;}
    int px(float v){return Math.round(v*unit);}
    boolean validSession(Engine.Session s){
        if(s==null)return true;
        if(BookCatalog.find(s.books)==null||s.at<0||s.at>s.queue.size()||s.learning<0||s.learning>s.learn.size())return false;
        for(String id:s.queue)if(!byId.containsKey(id))return false;
        for(String id:s.learn)if(!byId.containsKey(id))return false;
        for(String id:s.options)if(!byId.containsKey(id))return false;
        return s.chosen.isEmpty()||byId.containsKey(s.chosen);
    }
    int countBooks(long mask){int n=0;for(Engine.Word w:words)if(Engine.inBooks(w,mask))n++;return n;}
    List<Engine.Word> activeWords(){List<Engine.Word> book=wordBooks.get(activeBook);return book==null?Collections.emptyList():book;}
    List<Engine.Word> sessionWords(){if(session!=null){List<Engine.Word> book=wordBooks.get(session.books);if(book!=null)return book;}return activeWords();}
    int activeWordCount(){return activeWords().size();}
    int bookStudiedCount(long bit){int n=0;for(Engine.Progress p:progress.values()){Engine.Word w=byId.get(p.id);if(w!=null&&Engine.inBooks(w,bit))n++;}return n;}
    int activeNewToday(long now){String day=Engine.today(now);int n=0;for(Engine.Progress p:progress.values()){Engine.Word w=byId.get(p.id);if(w!=null&&Engine.inBooks(w,activeBook)&&day.equals(p.firstDay))n++;}return n;}
    int activeStudiedToday(long now){String day=Engine.today(now);int n=0;for(Engine.Progress p:progress.values()){Engine.Word w=byId.get(p.id);if(w!=null&&Engine.inBooks(w,activeBook)&&day.equals(p.lastDay))n++;}return n;}
    int wrongCount(){int n=0;for(Engine.Progress p:progress.values())if(p.trouble&&byId.containsKey(p.id)&&Engine.inBooks(byId.get(p.id),activeBook))n++;return n;}
    int dueCount(){int n=0;long now=System.currentTimeMillis();for(Engine.Progress p:progress.values())if(p.due<=now&&byId.containsKey(p.id)&&Engine.inBooks(byId.get(p.id),activeBook))n++;return n;}
    void render(){if(root.getWidth()==0)return;media.pageChanged();stopScroll();pronunciationWord="";phonetic=null;audioStatus=null;unit=Math.min(root.getWidth(),root.getHeight())/480f;if(halo!=null)halo.stop();halo=null;focusedCard=null;root.removeAllViews();answerViews.clear();homeViews.clear();soundViews.clear();reviewViews.clear();recallViews.clear();studyViews.clear();bookViews.clear();scroll=null;root.invalidate();
        if(screen.equals("error")){label("无法载入词库",70,145,340,60,34,INK,true);label("请重新打开，或覆盖安装 APK",70,220,340,90,25,MUTED,false);return;}
        if(!ready){label("词间",90,174,300,66,46,MINT,true);label("正在打开离线词库",90,250,300,40,23,MUTED,false);return;}
        switch(screen){case "session":showSession();break;case "settings":showSettings();break;case "books":showBooks();break;case "haptics":showHaptics();break;case "about":showAbout();break;case "flash":showFlash();break;case "flashmenu":showFlashMenu();break;case "review":showReview();break;case "empty":showEmpty();break;case "memory":showMemory();break;case "mnemonics":showMnemonics();break;case "sound":showSound();break;case "wordtools":showWordTools();break;case "studysettings":showStudySettings();break;case "touch":showTouchSettings();break;case "stats":showStats();break;default:showHome();}
        if(saveFailed)label("进度未保存 · 检查存储",110,435,260,25,18,RED,false);
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    void put(View v,float x,float y,float w,float h){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(w),px(h));lp.leftMargin=px(x)+(root.getWidth()-px(480))/2;lp.topMargin=px(y)+(root.getHeight()-px(480))/2;root.addView(v,lp);}
    TextView text(String value,float size,int color,boolean bold){TextView t=new TextView(this);t.setSoundEffectsEnabled(false);t.setHapticFeedbackEnabled(false);t.setText(value);t.setTextColor(color);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size)*Math.min(1.22f,getResources().getConfiguration().fontScale));t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);t.setFontFeatureSettings("kern");t.setTypeface(android.graphics.Typeface.create(bold?"sans-serif-medium":"sans-serif",0));t.setMaxLines(2);t.setEllipsize(TextUtils.TruncateAt.END);return t;}
    TextView label(String value,float x,float y,float w,float h,float size,int color,boolean bold){TextView t=text(value,size,color,bold);int minimum=px(Math.max(12,size*.78f)),maximum=Math.max(minimum,Math.round(px(size)*Math.min(1.22f,getResources().getConfiguration().fontScale)));t.setAutoSizeTextTypeUniformWithConfiguration(minimum,maximum,1,TypedValue.COMPLEX_UNIT_PX);put(t,x,y,w,h);return t;}
    GradientDrawable surface(int color,int border,boolean selected){GradientDrawable g=new GradientDrawable();if(color==MINT){g.setColors(new int[]{0xffd0ffe4,0xffa5e8c1});g.setOrientation(GradientDrawable.Orientation.TL_BR);}else if(color==PANEL){g.setColors(selected?new int[]{0xff203b30,0xff14271f}:new int[]{0xff172720,0xff101a16});g.setOrientation(GradientDrawable.Orientation.TL_BR);}else g.setColor(color);g.setCornerRadius(px(30));if(border!=0)g.setStroke(px(2),border);else if(color==PANEL)g.setStroke(px(selected?2:1),selected?0xff5e9f7e:0xff263b30);return g;}
    android.graphics.drawable.Drawable background(int color,int border){android.graphics.drawable.Drawable content;if(color==PANEL&&border==0){StateListDrawable states=new StateListDrawable();states.setEnterFadeDuration(110);states.setExitFadeDuration(150);states.addState(new int[]{android.R.attr.state_selected},surface(color,0,true));states.addState(new int[]{},surface(color,0,false));content=states;}else content=surface(color,border,false);return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x25ffffff),content,null);}
    TextView button(String value,float x,float y,float w,float h,int fill,int color,Runnable action){TextView t=text(value,27,color,true);t.setPadding(px(18),px(7),px(18),px(7));t.setAutoSizeTextTypeUniformWithConfiguration(px(19),Math.max(px(19),Math.round(px(27)*Math.min(1.22f,getResources().getConfiguration().fontScale))),1,TypedValue.COMPLEX_UNIT_PX);t.setBackground(background(fill,0));t.setClickable(true);t.setFocusable(true);t.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");}});t.setOnClickListener(v->{if(tap())action.run();});put(t,x,y,w,h);return t;}
    void top(String title){TextView heading=label(title,100,47,280,30,22,MUTED,false);heading.setTag("pageTitle");heading.setMaxLines(1);heading.setAutoSizeTextTypeUniformWithConfiguration(px(17),px(22),1,TypedValue.COMPLEX_UNIT_PX);}
    void word(String value){float size=value.length()>19?30:value.length()>14?36:value.length()>10?43:51;TextView t=label(value,52,83,376,58,size,INK,true);t.setMaxLines(1);t.setAutoSizeTextTypeUniformWithConfiguration(px(24),px(size),1,TypedValue.COMPLEX_UNIT_PX);if(!screen.equals("sound")){t.setContentDescription(value+"，点开读音详情");t.setOnClickListener(v->{if(tap())openSound(value);});}t.setOnLongClickListener(v->{openWordTools(value);return true;});
        TextView title=root.findViewWithTag("pageTitle");if(title!=null){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)title.getLayoutParams();lp.width=px(245);lp.leftMargin=px(95);title.setLayoutParams(lp);}
        TextView star=button(favorites.contains(value)?"★":"☆",348,42,46,46,PANEL,0xffecd198,()->toggleFavorite(value));star.setTag("favoriteButton");star.setPadding(0,0,0,0);star.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);star.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(28));star.setContentDescription((favorites.contains(value)?"取消收藏":"收藏")+value);
    }
    void meaning(Engine.Word w,int y,int color){String pos=w.pos.equals("词")?"":w.pos+" ";TextView t=label(pos+w.meaning,71,y,338,64,30,color,true);t.setAutoSizeTextTypeUniformWithConfiguration(px(23),px(30),1,TypedValue.COMPLEX_UNIT_PX);}
    void art(Engine.Word w,int y){
        if(pictures&&media.hasPhoto(w.id)){boolean dedicated=media.hasDedicatedVisual(w.id);String cue=media.scene(w.id).optString("caption");ImageView image=photoView(media.photo(w.id),dedicated);if(dedicated){put(image,76,y,108,102);TextView explanation=label(cue,192,y,212,102,20,INK,true);explanation.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);explanation.setMaxLines(4);explanation.setPadding(px(15),px(8),px(15),px(8));explanation.setBackground(background(PANEL,0));explanation.setAutoSizeTextTypeUniformWithConfiguration(px(16),px(20),1,TypedValue.COMPLEX_UNIT_PX);explanation.setOnClickListener(v->{if(tap())openMemory(w.id);});explanation.setContentDescription("专属联想提示："+cue+"，点开详情");}else put(image,98,y,284,98);image.setContentDescription((dedicated?"专属联想图：":"图片联想：")+cue+"，点开详情");image.setOnClickListener(v->{if(tap())openMemory(w.id);});image.setClickable(true);TextView caption=label(dedicated?"专属彩图 · 点开看":"场景彩图 · 点开看",88,y+103,304,22,17,MUTED,false);caption.setMaxLines(1);caption.setAutoSizeTextTypeUniformWithConfiguration(px(13),px(17),1,TypedValue.COMPLEX_UNIT_PX);caption.setTag("artCaption");}
        else if(!media.mnemonic(w.id).isEmpty()){TextView hint=button("近音助记  ›\n"+media.mnemonic(w.id),88,y,304,99,PANEL,MINT,()->openMemory(w.id));hint.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);hint.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(22));hint.setMaxLines(3);}
        else if(media.hasNote(w.id)){TextView hint=button(media.note(w.id).optString("type")+"线索  ›\n"+media.note(w.id).optString("text"),88,y,304,99,PANEL,MINT,()->openMemory(w.id));hint.setMaxLines(3);hint.setAutoSizeTextTypeUniformWithConfiguration(px(17),px(21),1,TypedValue.COMPLEX_UNIT_PX);}
        else {label("先记住这个意思",100,y+20,280,35,24,MUTED,false);}
    }
    ImageView photoView(Bitmap bitmap){return photoView(bitmap,false);}
    ImageView photoView(Bitmap bitmap,boolean crop){ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setScaleType(crop?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);GradientDrawable shape=new GradientDrawable();shape.setColor(PANEL);shape.setCornerRadius(px(22));image.setBackground(shape);image.setClipToOutline(true);image.setSoundEffectsEnabled(false);return image;}
    void pronunciation(Engine.Word w){
        pronunciationWord=w.id;
        TextView uk=button("英 ▷",64,144,78,44,PANEL,MINT,()->playWord(w.id,1));uk.setPadding(0,0,0,0);uk.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);uk.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(22));uk.setContentDescription("播放"+w.id+"的英式发音");
        TextView us=button("美 ▷",338,144,78,44,PANEL,MINT,()->playWord(w.id,2));us.setPadding(0,0,0,0);us.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);us.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(22));us.setContentDescription("播放"+w.id+"的美式发音");
        phonetic=label("",146,146,188,40,22,MUTED,false);phonetic.setMaxLines(1);phonetic.setAutoSizeTextTypeUniformWithConfiguration(px(16),px(22),1,TypedValue.COMPLEX_UNIT_PX);updatePhonetic(w.id);
        phonetic.setOnClickListener(v->{if(tap())openSound(w.id);});
    }
    void updatePhonetic(String id){String regional=media.ipa(id,accent);String generic=media.genericIpa(id);String value=!regional.isEmpty()?(accent==1?"英 ":"美 ")+"/"+regional+"/":generic.isEmpty()?"点英 / 美听读音":"/"+generic+"/";phonetic.setText(value);phonetic.setContentDescription(value+"，点开读音详情");}
    void playWord(String id,int kind){accent=kind;haptics.press();if(phonetic!=null)updatePhonetic(id);media.phones(id,ok->{if(active&&pronunciationWord.equals(id)){if(phonetic!=null)updatePhonetic(id);if(screen.equals("sound"))refreshSoundRows();}});media.speak(id,kind,msg->{if(active&&pronunciationWord.equals(id)&&audioStatus!=null){audioStatus.setText(msg);if(screen.equals("sound"))refreshSoundRows();}});}
    void quietStatus(){TextView caption=root.findViewWithTag("artCaption");audioStatus=caption!=null?caption:label("点音标看详情 · 点英 / 美发音",100,360,280,24,18,MUTED,false);audioStatus.setMaxLines(1);audioStatus.setAutoSizeTextTypeUniformWithConfiguration(px(14),px(18),1,TypedValue.COMPLEX_UNIT_PX);}
    void openSound(String id){soundId=id;soundReturn=screen;soundFocus=accent;screen="sound";haptics.press();render();}
    void showSound(){if(!byId.containsKey(soundId)){screen=soundReturn;render();return;}top("读音卡 · 点击才发声");word(soundId);pronunciationWord=soundId;
        for(int kind=1;kind<=2;kind++){final int chosen=kind;TextView row=button("",75,161+(kind-1)*99,330,89,PANEL,INK,()->{soundFocus=chosen;refreshSoundRows();focusView(soundViews.get(chosen-1),true);playWord(soundId,chosen);});row.setMaxLines(3);row.setAutoSizeTextTypeUniformWithConfiguration(px(16),px(24),1,TypedValue.COMPLEX_UNIT_PX);soundViews.add(row);}
        audioStatus=label("转圈选口音，点卡片播放",75,365,330,45,22,MUTED,false);audioStatus.setAutoSizeTextTypeUniformWithConfiguration(px(17),px(22),1,TypedValue.COMPLEX_UNIT_PX);refreshSoundRows();focusView(soundViews.get(soundFocus-1),false);
        label("右滑回到刚才的位置",98,425,284,24,19,MUTED,false);
    }
    void refreshSoundRows(){for(int i=0;i<soundViews.size();i++){int kind=i+1;String regional=media.ipa(soundId,kind),generic=media.genericIpa(soundId);String phoneme=!regional.isEmpty()?"/"+regional+"/":!generic.isEmpty()?"基础 /"+generic+"/":"地区音标暂未取得";TextView row=soundViews.get(i);row.setText((kind==1?"英式":"美式")+"  ▷  "+(media.online()?"在线播放":"联网已关")+"\n"+phoneme);row.setSelected(kind==soundFocus);row.setTextColor(kind==soundFocus?MINT:INK);}}
    void showHome(){
        top("词间  /  WORD HALO");long now=System.currentTimeMillis();int today=activeStudiedToday(now),newToday=activeNewToday(now);
        StudyVisual dial=new StudyVisual(this,MINT,newToday/(float)limit);put(dial,145,82,190,190);
        TextView total=label(Integer.toString(today),157,121,166,73,67,MINT,true);total.setContentDescription("今日已练"+today+"词，点开学习概览");total.setOnClickListener(v->{if(tap()){statsReturn="home";screen="stats";statsPage=0;haptics.press();render();}});
        label("今日已练 · 词",150,197,180,27,19,MUTED,false);label("新词 "+newToday+" / "+limit,150,229,180,20,15,MUTED,false);
        String main=session!=null?(session.complete()?"查看本组结果":"继续这一组"):"开始学习 · "+batchSize+"词";
        homeViews.add(button(main,76,280,328,72,MINT,0xff102019,()->{haptics.reveal();if(session==null)start(false);else{screen="session";render();}}));
        homeViews.add(button("错词 "+wrongCount(),95,365,138,60,PANEL,INK,()->{reviewFocus=-1;screen="review";haptics.press();render();}));
        homeViews.add(button("更多",247,365,138,60,PANEL,INK,()->{haptics.press();screen="settings";render();}));
        label(BookCatalog.find(activeBook).brand+" · 待复习 "+dueCount()+" · "+(recallMode?"回忆":"选择"),105,437,270,22,17,MUTED,false);if(homeFocus>=0)focusView(homeViews.get(homeFocus),false);
    }
    void start(boolean wrong){session=Engine.start(activeWords(),progress,activeBook,limit,wrong,System.currentTimeMillis(),batchSize,recallMode);session.bank=BookCatalog.find(activeBook).level;if(session.complete()){session=null;screen="empty";}else screen="session";persist(null);render();}
    void showEmpty(){top("当前没有待学词");label("歇一小会儿",65,143,350,65,42,MINT,true);label("到期复习或明天的新词会在这里出现。也可以去「更多」随手翻卡。",78,222,324,110,25,MUTED,false);button("回到首页",110,356,260,67,PANEL,INK,()->{screen="home";render();});}
    void showSession(){
        if(session==null){screen="home";showHome();return;}
        if(session.studying()){
            Engine.Word w=byId.get(session.learn.get(session.learning));top("先认一遍 · "+(session.learning+1)+" / "+session.learn.size());word(w.id);pronunciation(w);meaning(w,194,INK);art(w,260);
            button(session.learning+1==session.learn.size()?"开始测一测":"记住，下一词",105,386,270,58,MINT,0xff102019,()->{haptics.reveal();session.learning++;persist(null);renderTurn(1);});quietStatus();return;
        }
        if(session.complete()){showComplete();return;}
        Engine.Word w=byId.get(session.current());
        top(session.feedback?"记住词义 · 点单词听发音":(session.mistakesOnly?"错词重练":"选出词义")+" · "+(session.at+1)+" / "+session.queue.size());word(w.id);
        if(session.feedback){
            TextView result=label(session.lastCorrect?(session.recall?"✓  自评记得":"✓  答对了"):"再记一次",105,147,270,42,27,session.lastCorrect?MINT:RED,true);result.setBackground(background(session.lastCorrect?0xff10251b:0xff2a1818,0));result.setTag("answerResult");
            meaning(w,194,INK);if(session.lastCorrect)art(w,260);else showMistake(w);
            button(session.at+1==session.queue.size()?"完成这一组":"下一词",110,386,260,58,session.lastCorrect?MINT:PANEL,session.lastCorrect?0xff102019:INK,this::nextAnswer);
            return;
        }
        if(session.recall){showRecall(w);return;}
        if(session.options.isEmpty()){session.options.addAll(Engine.choices(w,sessionWords(),session.books,session.seed+session.at*997));persist(null);}
        pronunciation(w);
        float[] widths={346,350,302};for(int i=0;i<session.options.size();i++){
            final int index=i;Engine.Word option=byId.get(session.options.get(i));String letter="ABC".substring(i,i+1);
            TextView t=button(letter+"  "+option.meaning,(480-widths[i])/2,199+i*77,widths[i],70,PANEL,INK,()->answer(index));
            t.setPadding(px(17),px(2),px(17),px(2));t.setAutoSizeTextTypeUniformWithConfiguration(px(20),px(28),1,TypedValue.COMPLEX_UNIT_PX);t.setContentDescription("选项 "+letter+"，"+option.meaning);answerViews.add(t);
        }
        audioStatus=label("转圈选择 · 点选确认",80,431,320,24,19,MUTED,false);audioStatus.setMaxLines(1);audioStatus.setAutoSizeTextTypeUniformWithConfiguration(px(15),px(19),1,TypedValue.COMPLEX_UNIT_PX);updateFocus(false);
    }
    void focusView(View target,boolean animated){boolean animate=animated&&motion&&ValueAnimator.areAnimatorsEnabled();if(focusedCard!=null&&focusedCard!=target){focusedCard.setSelected(false);focusedCard.animate().cancel();if(animate)focusedCard.animate().scaleX(1).scaleY(1).translationZ(0).setDuration(150).setInterpolator(new DecelerateInterpolator()).start();else{focusedCard.setScaleX(1);focusedCard.setScaleY(1);focusedCard.setTranslationZ(0);}}target.setSelected(true);target.animate().cancel();if(animate){target.animate().scaleX(1.018f).scaleY(1.018f).translationZ(px(2)).setDuration(190).setInterpolator(new OvershootInterpolator(.55f)).start();}else{target.setScaleX(1.018f);target.setScaleY(1.018f);target.setTranslationZ(px(2));}focusedCard=target;if(halo==null){halo=new FocusHalo(this);halo.stroke=px(2);root.addView(halo,new FrameLayout.LayoutParams(-1,-1));}FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)target.getLayoutParams();RectF r=new RectF(lp.leftMargin+px(2),lp.topMargin+px(2),lp.leftMargin+lp.width-px(2),lp.topMargin+lp.height-px(2));halo.move(r,animate);}
    void updateFocus(){updateFocus(true);}
    void updateFocus(boolean animate){for(int i=0;i<answerViews.size();i++){TextView t=answerViews.get(i);boolean chosen=i==session.focus;t.setSelected(chosen);t.setTextColor(chosen?MINT:INK);if(chosen)focusView(t,animate);}}
    void nextAnswer(){if(session==null||!session.feedback)return;Engine.next(session);persist(null);if(session.complete())haptics.done();else haptics.reveal();render();}
    void answer(int i){if(session==null||session.feedback||session.studying()||session.recall||i<0||i>=session.options.size())return;if(saveFailed){persist(null);if(saveFailed)return;}String chosen=session.options.get(i);boolean correct=chosen.equals(session.current());Engine.Progress p=Engine.answer(session,progress,correct,chosen,System.currentTimeMillis());persist(p);if(correct)haptics.correct();else haptics.wrong();render();if(motion&&ValueAnimator.areAnimatorsEnabled()){View result=root.findViewWithTag("answerResult");if(result!=null){if(correct){result.setScaleX(.90f);result.setScaleY(.90f);result.animate().scaleX(1).scaleY(1).setDuration(230).setInterpolator(new OvershootInterpolator(.9f)).start();}else{result.setTranslationX(-px(5));result.animate().translationX(0).setDuration(210).setInterpolator(new DecelerateInterpolator()).start();}}}}
    void showMistake(Engine.Word correct){Engine.Word chosen=byId.get(session.chosen);if(chosen==null){art(correct,260);return;}TextView contrast=label("刚才选的："+chosen.meaning,82,265,316,87,23,RED,false);contrast.setTag("mistakeContrast");contrast.setMaxLines(3);contrast.setBackground(background(0xff281b1b,0));contrast.setPadding(px(12),px(8),px(12),px(8));contrast.setAutoSizeTextTypeUniformWithConfiguration(px(17),px(23),1,TypedValue.COMPLEX_UNIT_PX);if(media.hasPhoto(correct.id)||!media.mnemonic(correct.id).isEmpty()||media.hasNote(correct.id)){contrast.setContentDescription("刚才选的："+chosen.meaning+"，点开联想");contrast.setOnClickListener(v->{if(tap())openMemory(correct.id);});TextView link=label("点这里看联想  ›",105,354,270,29,20,MINT,false);link.setOnClickListener(v->{if(tap())openMemory(correct.id);});}else label("上方是正确词义",105,354,270,29,19,MUTED,false);}
    void finishGroup(){session=null;persist(null);haptics.tick();screen="home";render();}
    void showComplete(){top("这一组完成了");int errors=session.retried.size();put(new UiArtwork(this,"success",MINT),145,91,190,88);label(session.correct+" / "+session.answered.size(),100,182,280,58,49,INK,true);label(session.recall?(errors==0?"自评全部记得 · 继续保持":"自评记得数量 · 忘词已安排复习"):(errors==0?"首次作答全对 · 继续保持":"首次作答正确 · 错词已安排复习"),66,249,348,48,23,MUTED,false);if(errors>0){button("回看本组错词 · "+errors,85,308,310,61,PANEL,MINT,()->openFlash(3,"session"));button("回到首页",112,382,256,58,MINT,0xff102019,this::finishGroup);}else button("回到首页",110,350,260,66,MINT,0xff102019,this::finishGroup);}
    void setting(LinearLayout list,String title,Runnable action){TextView t=text(title,25,INK,true);t.setPadding(px(14),px(14),px(14),px(14));t.setBackground(background(PANEL,0));t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->{if(tap()){if(screen.equals("settings")&&scroll!=null)settingsY=scroll.getScrollY();action.run();}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(76));p.bottomMargin=px(10);list.addView(t,p);}
    void showSettings(){top("更多 · 转表圈滚动");scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);put(scroll,79,104,322,306);
        setting(list,"当前词书 · "+BookCatalog.find(activeBook).shortLabel(),()->{screen="books";haptics.press();render();});
        setting(list,"翻卡 · 筛选 / 收藏",()->{flashMode=validFlashMode(flashMode);screen="flashmenu";haptics.tick();render();});
        setting(list,"学习方式 · "+(recallMode?"回忆自评":"词义选择"),()->{screen="studysettings";haptics.press();render();});
        setting(list,"我的收藏 · "+favorites.size(),()->openFlash(4,"settings"));
        setting(list,"学习概览 · 曲线与复习计划",()->{statsReturn="settings";statsPage=0;screen="stats";haptics.press();render();});
        setting(list,"触感调校 · 答题 / 表圈",()->{screen="touch";haptics.press();render();});
        setting(list,"试一试震动",()->{hapticsReturn="settings";screen="haptics";render();});
        setting(list,"图片联想 · "+(pictures?"开":"关"),()->{int y=scroll.getScrollY();pictures=!pictures;prefs.edit().putBoolean("pictures",pictures).apply();haptics.tick();render();scroll.post(()->scroll.scrollTo(0,y));});
        setting(list,"谐音助记 · 看一看",()->{haptics.press();screen="mnemonics";render();});
        setting(list,"联网发音 / 高清图 · "+(media.online()?"开":"关"),()->{prefs.edit().putBoolean("online",!media.online()).apply();haptics.press();render();});
        setting(list,"过渡动画 · "+(motion?"开":"关"),()->{motion=!motion;prefs.edit().putBoolean("motion",motion).apply();haptics.press();render();});
        setting(list,"清理图片 / 音标缓存 · "+(media.cacheBytes()/1024/1024)+" MB",()->media.clearCache(ok->{Toast.makeText(this,ok?"已清理，学习进度已保留":"部分缓存暂未清理",Toast.LENGTH_SHORT).show();if(active)render();}));
        setting(list,"使用说明 / 词库来源",()->{screen="about";render();});
        ScrollView menu=scroll;menu.post(()->menu.scrollTo(0,settingsY));menu.setOnScrollChangeListener((v,x,y,ox,oy)->settingsY=y);
        label("右滑返回",130,426,220,23,19,MUTED,false);
    }
    void setBookLevel(int level){bookLevel=level;prefs.edit().putInt("bookLevel",level).apply();haptics.select();renderTurn(level==6?1:-1);}
    void selectBook(BookCatalog.Book book){if(activeBook==book.bit){haptics.boundary();return;}activeBook=book.bit;bookLevel=book.level;prefs.edit().putLong("activeBook",activeBook).putInt("bookLevel",bookLevel).apply();haptics.bookmark();render();}
    void showBooks(){top("选择词书 · 每次一本");button("四级词书",83,96,150,54,bookLevel==4?MINT:PANEL,bookLevel==4?0xff102019:INK,()->setBookLevel(4));button("六级词书",247,96,150,54,bookLevel==6?MINT:PANEL,bookLevel==6?0xff102019:INK,()->setBookLevel(6));
        scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);for(BookCatalog.Book book:BookCatalog.forLevel(bookLevel)){boolean chosen=activeBook==book.bit;int count=countBooks(book.bit),seen=bookStudiedCount(book.bit);TextView row=text((chosen?"●  ":"○  ")+book.brand+" · "+book.title+"\n已学 "+seen+" / "+count+" · "+book.edition,20,chosen?MINT:INK,true);row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setPadding(px(16),px(4),px(12),px(4));row.setMaxLines(2);row.setBackground(background(PANEL,chosen?MINT:0));row.setClickable(true);row.setFocusable(true);row.setContentDescription((chosen?"当前词书":"选择")+book.level+"级"+book.label()+"，已学"+seen+"，共"+count+"词");row.setOnClickListener(v->{if(tap())selectBook(book);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(76));lp.bottomMargin=px(9);list.addView(row,lp);bookViews.add(row);}scroll.addView(list);put(scroll,76,164,328,210);
        label("换书保留进度 · 下一组生效",76,380,328,23,18,MUTED,false);button("完成",105,408,270,53,MINT,0xff102019,this::back);
    }
    void showHaptics(){String[] names={"表圈 · 清脆刻度","按下 · 稳稳落点","翻卡 · 渐强轻弹","答对 · 两下跃动","答错 · 长振回落","完成 · 三段上扬"};top("触感实验室 · "+(hapticAt+1)+" / 6");put(new UiArtwork(this,"wave"+hapticAt,hapticAt==4?RED:MINT),115,117,250,126);label(names[hapticAt],64,250,352,44,31,INK,true);label(haptics.modeLabel(),80,301,320,30,21,MUTED,false);button("点一下，感受",105,350,270,66,PANEL,MINT,this::sampleHaptic);label("转表圈切换并体验",95,427,290,24,20,MUTED,false);}
    void sampleHaptic(){haptics.cancel();switch(hapticAt){case 0:haptics.tick();break;case 1:haptics.press();break;case 2:haptics.reveal();break;case 3:haptics.correct();break;case 4:haptics.wrong();break;default:haptics.done();}}
    void showAbout(){
        top("词间 0.23 · 使用说明");
        scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        int artCount=0,dedicatedCount=0,mnemonicCount=0;for(Engine.Word w:words){if(media.hasPhoto(w.id))artCount++;if(media.hasDedicatedVisual(w.id))dedicatedCount++;if(!media.mnemonic(w.id).isEmpty())mnemonicCount++;}
        String info="离线背词 · 点播发音 · 无账号\n\n"+
            "内置 6 本互相独立的词书：四级和六级各有星火、新东方、闪过。每次只启用一本，词数、原有词序和已学进度都按书显示，不会把几家词表合并成一个学习计划。切换从下一组生效，重合单词继续沿用你的记忆记录。\n\n"+
            "本版共保存 "+words.size()+" 个不重复词条。星火、新东方会标明具体版次；闪过标为公开整理词序。百词斩“六级核心（过考版）”2551 词目前没有可核验的公开完整词序，因此没有拿通用 2551 词冒充。\n\n"+
            "先认识新词，再做选择题。错词会稍后重现。复习间隔：10分钟、1天、3天、7天、14天、30天、60天。\n\n"+
            "首页转圈选择入口；认词时转圈前后翻词，最后点按钮开测；答题时转圈选答案，点选项作答；结果页顺转到下一词、逆转看联想；联想页转圈翻页；菜单转圈滚动。也可沿屏幕最外圈划动。\n\n"+
            "词书成员与词序来自 DictionaryData 和 English Vocabulary Vault；短释义及基础音标主要来自 ECDICT。具体来源、版本和校验值随源码提供。释义为快速记忆摘要，省略例句及部分义项。\n\n"+
            dedicatedCount+" 个六级高频抽象词配有一词一图的专属联想插画，每张都附有针对该词的中文看图提示；其余 "+(artCount-dedicatedCount)+" 个词保留经过筛选的图库场景。专属图优先覆盖原来重复最多的法律、医疗、经济、协作和学术词。谐音只帮记意思，不代表正确发音或词源。\n\n"+
            "图库照片来自 Wikimedia Commons，已附带离线缩略图，点开后可联网加载清晰图；各自的作者、来源与许可都在署名页。专属插画由 OpenAI 图像生成工具制作并逐张按词义筛选，只保存在 APK 内，不发起图片请求。\n\n"+
            "背词和打开读音卡不会联网；只有点英式 / 美式播放按钮后才直接联网播放并查询地区音标，不预下载、不保存音频文件。发音和地区音标来自有道词典；基础音标来自 ECDICT，未取得地区音标时只标基础音标。\n\n"+
            "网络由 Wear OS 选择，可能经过手机蓝牙代理或手表 Wi-Fi。离开应用或划掉后台任务会立即断开请求、释放播放器。第三方服务不可用时学习和记录不受影响；不上传成绩或身份信息。\n\n"+
            "图片与音标资料缓存上限约 48 MB，可在更多里清理。关闭联网后仍可背词、看内置图片；读音每次都需要网络。本次升级自动清理旧版音频文件，保留学习记录。\n\n"+
            "每组可选5、10或20词；每日新词限额不限制到期复习。支持词义选择与回忆自评，设置从下一组生效。翻卡可选全部、今日学过、错词或收藏，支持打乱顺序并分别记住位置；不会改动测验成绩。错词本可随时回看，当前测验会保留。整组完成后还可只回看本组错词。\n\n"+
            "点星星收藏单词；长按单词打开工具。学习概览显示近七天练词量、个人遗忘曲线和每天的复习预测；85%保持率是复习提醒线，到期词按最可能忘记的优先。每日去重统计从0.20版开始，旧进度保留但不推算过去成绩。进度只存在手表本机，同签名覆盖安装可保留，卸载会清除。\n\n"+
            "答题与表圈操作可分别设置震动强度。清晰档使用更长的满幅振动段和分段收尾，实际力度仍受系统和硬件影响。震动尊重系统触感与勿扰；除手动点播发音外没有声音，不保持屏幕常亮。\n\n"+
            "已有校正保留，另有51条自编构词、辨析和画面线索；线索用于辅助记忆，不代替完整词典。完整源码、词库来源与许可说明随项目交付。";
        TextView t=text(info,24,INK,false);t.setGravity(Gravity.START);t.setMaxLines(1000);t.setEllipsize(null);t.setLineSpacing(px(5),1);t.setPadding(px(12),px(8),px(12),px(30));scroll.addView(t);put(scroll,81,105,318,306);label("转表圈滚动 · 右滑返回",95,425,290,25,18,MUTED,false);
    }
    String flashName(){return flashMode==1?"错词回看":flashMode==2?"今日学过":flashMode==3?"本组错词":flashMode==4?"我的收藏":"全部单词";}
    List<Engine.Word> flashCandidates(int mode){ArrayList<Engine.Word> list=new ArrayList<>();String today=Engine.today(System.currentTimeMillis());Set<String> group=session==null?Collections.emptySet():session.retried;List<Engine.Word> source=mode==3?sessionWords():activeWords();for(Engine.Word w:source){Engine.Progress p=progress.get(w.id);if(mode==0||mode==1&&p!=null&&p.trouble||mode==2&&p!=null&&today.equals(p.lastDay)||mode==3&&group.contains(w.id)||mode==4&&favorites.contains(w.id))list.add(w);}if(mode==1)list.sort(Comparator.<Engine.Word>comparingInt(w->-progress.get(w.id).lapses).thenComparingLong(w->progress.get(w.id).due).thenComparing(w->w.id));return list;}
    void showReview(){top("错词本 · 慢慢记牢");int count=wrongCount();label(Integer.toString(count),120,105,240,89,77,count==0?MINT:RED,true);label(count==0?"暂时没有错词":flashShuffle?"已开启打乱翻卡":"错得多的优先翻",95,206,290,32,23,MUTED,false);reviewViews.add(button("翻卡记一遍",77,254,326,65,MINT,0xff102019,()->openFlash(1,"review")));boolean busy=session!=null&&!session.complete();reviewViews.add(button(busy?"继续当前一组":"测一组错词",87,333,306,63,PANEL,INK,()->{haptics.press();if(session!=null&&!session.complete()){screen="session";render();}else start(true);}));label(busy?"翻卡不影响当前进度":"转圈选择 · 点按钮进入",90,420,300,26,19,MUTED,false);if(reviewFocus>=0)focusView(reviewViews.get(reviewFocus),false);}
    void showFlashMenu(){top("翻卡 · 转圈切换范围");button(flashName()+"  ›",80,113,320,64,PANEL,MINT,()->cycleFlashMode(1));label(flashCandidates(flashMode).size()+" 词 · 不计测验成绩",76,191,328,40,25,INK,false);button(flashShuffle?"顺序 · 已打乱":"顺序 · 原顺序",87,246,306,57,PANEL,INK,()->{flashShuffle=!flashShuffle;if(flashShuffle)flashSeed=System.currentTimeMillis();prefs.edit().putBoolean("flashShuffle",flashShuffle).putLong("flashSeed",flashSeed).apply();haptics.select();render();});button("开始 / 继续翻卡",87,323,306,66,MINT,0xff102019,()->openFlash(flashMode,"flashmenu"));label("各范围分别记住上次位置",95,418,290,28,19,MUTED,false);}
    void cycleFlashMode(int direction){int[] modes={0,1,2,4};int at=0;for(int i=0;i<modes.length;i++)if(modes[i]==flashMode)at=i;flashMode=modes[Math.floorMod(at+direction,modes.length)];prefs.edit().putInt("flashMode",flashMode).apply();haptics.select();renderTurn(direction);}
    void openFlash(int mode,String origin){flashMode=mode;flashReturn=origin;flashWords.clear();flashWords.addAll(flashCandidates(mode));if(flashShuffle&&mode!=3)Collections.shuffle(flashWords,new Random(flashSeed+mode));flashAt=0;String last=prefs.getString("flashWord"+mode,"");if(mode!=3){for(int i=0;i<flashWords.size();i++)if(flashWords.get(i).id.equals(last)){flashAt=i;break;}}flipped=false;saveFlashPosition();screen="flash";haptics.reveal();render();}
    void saveFlashPosition(){if(!flashWords.isEmpty()&&flashAt>=0&&flashAt<flashWords.size()&&flashMode!=3)prefs.edit().putString("flashWord"+flashMode,flashWords.get(flashAt).id).apply();}
    void showFlash(){if(flashWords.isEmpty()){top(flashName());label(flashMode==4?"收藏想多看几遍的词":flashMode==1||flashMode==3?"这里还没有错词":"今天还没开始练",65,153,350,61,36,MINT,true);label(flashMode==4?"点单词右上角的星星\n这里会收好你的收藏":"随时可以回来翻卡\n不会改变测验成绩",75,239,330,75,25,MUTED,false);button("返回",110,357,260,65,PANEL,INK,this::back);return;}flashAt=Math.max(0,Math.min(flashAt,flashWords.size()-1));Engine.Word w=flashWords.get(flashAt);top(flashName()+" · "+(flashAt+1)+" / "+flashWords.size());word(w.id);
        pronunciation(w);if(flipped){meaning(w,194,INK);art(w,260);}else{label("先想一想它的意思",95,213,290,52,28,MUTED,false);label("转圈换词 · 翻卡不计成绩",80,285,320,33,21,MUTED,false);}
        if(flipped){button("再想想",85,386,139,58,PANEL,INK,()->{flipped=false;haptics.reveal();renderTurn(-1);});button("下一词",236,386,159,58,MINT,0xff102019,()->moveFlash(1));}else button("翻开词义",110,386,260,58,MINT,0xff102019,()->{flipped=true;haptics.reveal();renderTurn(1);});
        quietStatus();
    }
    void openMemory(String id){memoryId=id;memoryPage=0;memoryReturn=screen;screen="memory";haptics.reveal();render();}
    List<String> memoryKinds(){List<String> pages=new ArrayList<>();if(media.hasPhoto(memoryId))pages.add("photo");if(!media.mnemonic(memoryId).isEmpty())pages.add("mnemonic");if(media.hasNote(memoryId))pages.add("note");if(media.hasPhoto(memoryId))pages.add("credit");return pages;}
    int memoryPages(){return memoryKinds().size();}
    void showMemory(){Engine.Word w=byId.get(memoryId);if(w==null){screen=memoryReturn;render();return;}List<String> pages=memoryKinds();int total=Math.max(1,pages.size());memoryPage=Math.max(0,Math.min(memoryPage,total-1));String kind=pages.isEmpty()?"empty":pages.get(memoryPage);boolean dedicated=media.hasDedicatedVisual(w.id);top((dedicated?"专属图卡 · ":"联想卡 · ")+(memoryPage+1)+" / "+total);word(w.id);
        if(kind.equals("photo")){
            ImageView image=photoView(media.photo(w.id),dedicated);image.setContentDescription(media.scene(w.id).optString("caption"));put(image,69,151,342,194);TextView cue=label(media.scene(w.id).optString("caption"),70,350,340,51,dedicated?22:24,INK,dedicated);cue.setMaxLines(2);cue.setAutoSizeTextTypeUniformWithConfiguration(px(18),px(dedicated?22:24),1,TypedValue.COMPLEX_UNIT_PX);
            if(!dedicated){String requestWord=w.id;media.highPhoto(w.id,b->{if(screen.equals("memory")&&memoryId.equals(requestWord)&&memoryPage==0)image.setImageBitmap(b);});}
        }else if(kind.equals("mnemonic")){
            label("近音梗 · 只帮记词义",70,155,340,34,22,MINT,false);TextView hint=label(media.mnemonic(w.id),72,204,336,128,31,INK,true);hint.setMaxLines(4);hint.setAutoSizeTextTypeUniformWithConfiguration(px(24),px(31),1,TypedValue.COMPLEX_UNIT_PX);TextView listen=button("听真实读音  ▷",85,350,310,51,PANEL,MINT,()->openSound(w.id));listen.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);listen.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(23));
        }else if(kind.equals("note")){
            JSONObject note=media.note(w.id);label(note.optString("type")+"线索",85,157,310,34,23,MINT,true);TextView hint=label(note.optString("text"),77,205,326,148,29,INK,false);hint.setTag("memoryNote");hint.setMaxLines(5);hint.setLineSpacing(px(3),1);hint.setAutoSizeTextTypeUniformWithConfiguration(px(21),px(29),1,TypedValue.COMPLEX_UNIT_PX);label("短线索 · 帮你多记一层",80,365,320,30,20,MUTED,false);
        }else if(kind.equals("credit")){
            JSONObject source=media.scene(w.id);String details=source.optBoolean("generated")?source.optString("caption")+"\n\n专属联想插画\n制作："+source.optString("artist")+"\n说明："+source.optString("license")+"\n\n这张图按当前单词的核心义单独设计并人工检查，只用于帮助记忆，不是词源解释。图片离线保存在 APK 内。":source.optString("caption")+"\n\n作者："+source.optString("artist")+"\n许可："+source.optString("license")+"\n"+source.optString("licenseUrl")+"\n\n来源："+source.optString("source")+"\n\n已缩小并转换为 WebP；按原图比例显示。";scroll=new ScrollView(this);TextView credit=text(details,22,INK,false);credit.setGravity(Gravity.START);credit.setMaxLines(1000);credit.setEllipsize(null);credit.setLineSpacing(px(5),1);credit.setPadding(px(8),px(8),px(8),px(30));scroll.addView(credit);put(scroll,80,155,320,247);
        }else{meaning(w,196,INK);label("这个词暂未配专属联想\n可点星星，留待多看几遍",75,290,330,80,24,MUTED,false);}
        label(scroll!=null?"转圈滚动 · 右滑返回":total>1?"转圈翻页 · 右滑返回":"右滑返回",90,419,300,28,20,MUTED,false);
    }
    void showMnemonics(){top("近音助记 · 不代替发音");scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);for(Engine.Word w:activeWords())if(!media.mnemonic(w.id).isEmpty())setting(list,w.id+" · "+w.meaning,()->openMemory(w.id));scroll.addView(list);put(scroll,77,103,326,307);label("转圈滚动 · 点词查看",90,424,300,25,20,MUTED,false);}
    int validFlashMode(int mode){return mode==1||mode==2||mode==4?mode:0;}
    void toggleFavorite(String id){if(!byId.containsKey(id))return;Set<String> next=new HashSet<>(favorites);boolean added=next.add(id);if(!added)next.remove(id);if(!prefs.edit().putStringSet("favorites",next).commit()){Toast.makeText(this,"收藏未保存，请检查空间",Toast.LENGTH_SHORT).show();return;}favorites.clear();favorites.addAll(next);haptics.bookmark();TextView star=root.findViewWithTag("favoriteButton");if(star!=null){star.setText(added?"★":"☆");star.setContentDescription((added?"取消收藏":"收藏")+id);if(motion&&ValueAnimator.areAnimatorsEnabled()){star.setScaleX(.78f);star.setScaleY(.78f);star.animate().scaleX(1).scaleY(1).setDuration(180).setInterpolator(new OvershootInterpolator(.7f)).start();}}TextView action=root.findViewWithTag("favoriteAction");if(action!=null)action.setText(added?"★  已收藏 · 点此取消":"☆  加入我的收藏");}
    void openWordTools(String id){if(screen.equals("wordtools")||!byId.containsKey(id))return;toolsId=id;toolsReturn=screen;screen="wordtools";haptics.select();render();}
    boolean toolsCanShowMemory(){return !toolsReturn.equals("session")||session==null||session.studying()||session.feedback||session.recall&&session.revealed||!toolsId.equals(session.current());}
    void showWordTools(){Engine.Word w=byId.get(toolsId);if(w==null){screen=toolsReturn;render();return;}top("单词工具 · 随手收好");word(w.id);TextView save=button(favorites.contains(w.id)?"★  已收藏 · 点此取消":"☆  加入我的收藏",77,165,326,64,PANEL,0xffecd198,()->toggleFavorite(w.id));save.setTag("favoriteAction");button("英式 / 美式读音",77,244,326,64,PANEL,MINT,()->openSound(w.id));if(toolsCanShowMemory())button("图片 / 记忆线索",87,323,306,64,PANEL,INK,()->openMemory(w.id));else label("答题后可查看记忆线索",78,329,324,55,23,MUTED,false);label("右滑回到原来的词",98,421,284,27,19,MUTED,false);}
    void showStudySettings(){top("学习偏好 · 下一组生效");studyViews.add(button("方式 · "+(recallMode?"回忆自评":"词义选择"),75,120,330,69,PANEL,MINT,()->{recallMode=!recallMode;prefs.edit().putBoolean("recallMode",recallMode).apply();haptics.select();render();}));studyViews.add(button("每组 · "+batchSize+" 词",85,205,310,65,PANEL,INK,()->{batchSize=batchSize==5?10:batchSize==10?20:5;prefs.edit().putInt("batchSize",batchSize).apply();haptics.select();render();}));studyViews.add(button("每日新词 · "+limit,85,286,310,65,PANEL,INK,()->{limit=limit==10?20:limit==20?30:limit==30?50:10;prefs.edit().putInt("limit",limit).apply();haptics.select();render();}));label(recallMode?"先想词义，再揭晓\n点“记得 / 忘了”记录自评":"新词先认一遍，再选词义\n到期复习优先",85,366,310,55,22,MUTED,false);label("转圈选项 · 点选调整",100,431,280,23,17,MUTED,false);if(studyFocus>=0)focusView(studyViews.get(studyFocus),false);}
    void showRecall(Engine.Word w){TextView title=root.findViewWithTag("pageTitle");title.setText("回忆自评 · "+(session.at+1)+" / "+session.queue.size());pronunciation(w);if(!session.revealed){put(new UiArtwork(this,"spark",MINT),153,212,174,91);label("先在心里说出词义",85,313,310,40,25,INK,true);button("揭晓词义",110,386,260,58,MINT,0xff102019,this::revealRecall);}else{meaning(w,194,INK);art(w,260);recallViews.add(button("忘了",85,386,139,58,PANEL,RED,()->gradeRecall(false)));recallViews.add(button("记得",236,386,159,58,MINT,0xff102019,()->gradeRecall(true)));updateRecallFocus();}quietStatus();}
    void revealRecall(){if(session==null||!session.recall||session.studying()||session.complete()||session.feedback||session.revealed)return;session.revealed=true;session.focus=-1;persist(null);haptics.reveal();renderTurn(1);}
    void updateRecallFocus(){if(session==null||session.focus<0||session.focus>=recallViews.size())return;for(int i=0;i<recallViews.size();i++)recallViews.get(i).setSelected(session.focus==i);focusView(recallViews.get(session.focus),true);}
    void gradeRecall(boolean remembered){if(session==null||!session.recall||!session.revealed||session.feedback||session.studying()||session.complete())return;if(saveFailed){persist(null);if(saveFailed)return;}Engine.Progress p=Engine.answer(session,progress,remembered,remembered?session.current():"",System.currentTimeMillis());persist(p);if(remembered)haptics.correct();else haptics.wrong();render();}
    String strengthName(int level){return new String[]{"关","轻柔","标准","清晰"}[Math.max(0,Math.min(3,level))];}
    void showTouchSettings(){top("触感调校 · 转圈调强度");label("点标题选择要调的触感",80,104,320,30,21,MUTED,false);for(int row=0;row<2;row++){final int kind=row;int level=row==0?haptics.strength:haptics.navigationStrength;TextView title=label((row==0?"答题 / 完成":"表圈 / 翻卡")+" · "+strengthName(level),88,153+row*118,304,34,25,touchFocus==row?MINT:MUTED,true);title.setOnClickListener(v->{if(tap()){touchFocus=kind;haptics.select();render();}});for(int i=0;i<4;i++){final int choice=i;TextView b=button(strengthName(i),92+i*75,198+row*118,71,52,i==level?MINT:PANEL,i==level?0xff102019:INK,()->setTouchLevel(kind,choice));b.setPadding(0,0,0,0);b.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(21));}}button("体验全部节奏",105,387,270,56,PANEL,MINT,()->{hapticsReturn="touch";screen="haptics";render();});}
    void setTouchLevel(int kind,int level){touchFocus=kind;if(kind==0){haptics.setStrength(level);prefs.edit().putInt("haptics",haptics.strength).apply();}else{haptics.setNavigationStrength(level);prefs.edit().putInt("navigationHaptics",haptics.navigationStrength).apply();}haptics.cancel();if(kind==0)haptics.correct();else haptics.tick();render();}
    void meter(String name,int value,int total,int y,int color){label(name,78,y,175,29,22,MUTED,false).setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);label(Integer.toString(value),285,y,115,29,25,INK,true).setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);View track=new View(this);track.setBackground(background(PANEL,0));put(track,80,y+39,320,9);if(value>0){View fill=new View(this);fill.setBackground(background(color,0));put(fill,80,y+39,Math.max(9,320f*value/Math.max(1,total)),9);}}
    List<Engine.Progress> activeProgress(){ArrayList<Engine.Progress> list=new ArrayList<>();for(Engine.Progress p:progress.values()){Engine.Word w=byId.get(p.id);if(w!=null&&Engine.inBooks(w,activeBook))list.add(p);}return list;}
    void showStats(){
        top("学习概览 · "+(statsPage+1)+" / 4");long now=System.currentTimeMillis();
        if(statsPage==0){
            label("最近七天",100,103,280,43,31,INK,true);Map<String,int[]> history=store.practice();int[] counts=new int[7];String[] dates=new String[7];java.time.LocalDate today=java.time.LocalDate.parse(Engine.today(now));int sum=0;
            for(int i=0;i<7;i++){String date=today.minusDays(6-i).toString();int[] row=history.get(date);counts[i]=row==null?0:row[0];sum+=counts[i];dates[i]=date.substring(5).replace('-','/');}
            put(new StudyVisual(this,MINT,counts,dates),68,177,344,156);label("七天合计 · "+sum+" 词次",78,348,324,37,25,MINT,true);label("每日去重 · 从本版开始记录",80,388,320,27,18,MUTED,false);
        }else if(statsPage==1){
            int familiar=0,seen=0,total=activeWordCount();for(Engine.Progress p:progress.values()){Engine.Word w=byId.get(p.id);if(w!=null&&Engine.inBooks(w,activeBook)){seen++;if(!p.trouble&&p.stage>=4)familiar++;}}
            label(BookCatalog.find(activeBook).brand+" · 词书进度",90,104,300,42,31,INK,true);meter("还没学",Math.max(0,total-seen),total,175,MUTED);meter("继续巩固",seen-familiar,total,248,0xffe4c487);meter("较熟悉",familiar,total,321,MINT);label("进度按当前词书统计 · 熟悉为 14 天阶段",65,390,350,28,18,MUTED,false);
        }else if(statsPage==2){
            List<Engine.Progress> tracked=activeProgress();label("个人遗忘曲线",94,92,292,40,30,INK,true);
            if(tracked.isEmpty()){label("先完成一组单词",110,190,260,45,27,MINT,true);label("有记录后会预测每天复习量",80,250,320,50,21,MUTED,false);}
            else{double[] curve=MemoryCurve.forecast(tracked,now);put(new MemoryCurveView(this,MINT,curve),68,137,344,154);int current=(int)Math.round(curve[0]*100),tomorrow=(int)Math.round(curve[1]*100);label("现在 "+current+"% · 24小时后 "+tomorrow+"%",73,305,334,34,23,MINT,true);label("今天 "+dueCount()+" 词 · 24h新增 "+MemoryCurve.dueWithin(tracked,now,now+Engine.DAY)+" 词",72,345,336,32,21,INK,true);label("85% 提醒线 · 按答题记录预测",80,382,320,27,18,MUTED,false);}
        }else{
            List<Engine.Progress> tracked=activeProgress();int due=dueCount(),nextDay=MemoryCurve.dueWithin(tracked,now,now+Engine.DAY),nextThree=MemoryCurve.dueWithin(tracked,now,now+3*Engine.DAY);long next=Long.MAX_VALUE;for(Engine.Progress p:tracked)if(p.due>now)next=Math.min(next,p.due);
            label("复习安排",100,91,280,42,31,INK,true);meter("现在待复习",due,Math.max(1,Math.max(due,nextThree)),142,MINT);meter("未来24h新增",nextDay,Math.max(1,Math.max(due,nextThree)),211,0xffe4c487);meter("未来3天新增",nextThree,Math.max(1,Math.max(due,nextThree)),280,0xff679b83);String when=next==Long.MAX_VALUE?"完成一组后开始预测":due>0?"先清到期词，再学新词":"下一词 · "+new java.text.SimpleDateFormat("M月d日 HH:mm",Locale.CHINA).format(new Date(next));label(when+" · 今日新词 "+activeNewToday(now),75,350,330,58,20,MUTED,false);
        }
        label("转圈翻页 · 右滑返回",100,430,280,25,18,MUTED,false);
    }
    void renderTurn(int direction){render();if(!motion||!ValueAnimator.areAnimatorsEnabled())return;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child==halo)continue;child.setAlpha(.55f);child.setTranslationY(px(9)*direction);child.animate().alpha(1).translationY(0).setDuration(155).setInterpolator(new DecelerateInterpolator()).start();}}
    void moveFlash(int delta){if(flashWords.isEmpty()){haptics.boundary();return;}int next=flashAt+delta;boolean wrapped=next<0||next>=flashWords.size();flashAt=Math.floorMod(next,flashWords.size());saveFlashPosition();flipped=false;if(wrapped)haptics.boundary();else haptics.tick();renderTurn(delta);}
    void stopScroll(){if(scrollMotion!=null){scrollMotion.cancel();scrollMotion=null;}}
    void scrollByRotary(int direction){int max=scroll.getChildCount()==0?0:Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());int from=scrollMotion!=null?scrollTarget:scroll.getScrollY(),target=Math.max(0,Math.min(max,from+px(88)*direction));if(target==from){haptics.boundary();return;}stopScroll();scrollTarget=target;ScrollView view=scroll;if(!motion||!ValueAnimator.areAnimatorsEnabled())view.scrollTo(0,target);else{ValueAnimator animator=ValueAnimator.ofInt(view.getScrollY(),target);scrollMotion=animator;animator.setDuration(180);animator.setInterpolator(new DecelerateInterpolator());animator.addUpdateListener(v->view.scrollTo(0,(Integer)v.getAnimatedValue()));animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(scrollMotion==a)scrollMotion=null;}});animator.start();}haptics.tick();}
    void rotate(int direction){
        if(!ready||direction==0)return;
        if(screen.equals("studysettings")){studyFocus=studyFocus<0?(direction>0?0:2):Math.floorMod(studyFocus+direction,3);haptics.select();focusView(studyViews.get(studyFocus),true);return;}
        if(screen.equals("stats")){statsPage=Math.floorMod(statsPage+direction,4);haptics.select();renderTurn(direction);return;}
        if(screen.equals("touch")){int value=touchFocus==0?haptics.strength:haptics.navigationStrength;int next=Math.max(0,Math.min(3,value+direction));if(value==next){haptics.boundary();return;}setTouchLevel(touchFocus,next);return;}
        if(screen.equals("flashmenu")){cycleFlashMode(direction);return;}
        if(screen.equals("review")){reviewFocus=reviewFocus<0?(direction>0?0:1):Math.floorMod(reviewFocus+direction,2);focusView(reviewViews.get(reviewFocus),true);haptics.select();return;}
        if(scroll!=null){if(screen.equals("memory")&&direction<0&&scroll.getScrollY()==0&&scrollMotion==null&&memoryPage>0){memoryPage--;haptics.reveal();renderTurn(-1);return;}scrollByRotary(direction);return;}
        if(screen.equals("sound")){soundFocus=soundFocus==1?2:1;accent=soundFocus;refreshSoundRows();focusView(soundViews.get(soundFocus-1),true);haptics.select();return;}
        if(screen.equals("home")){homeFocus=homeFocus<0?(direction>0?0:2):Math.floorMod(homeFocus+direction,3);focusView(homeViews.get(homeFocus),true);haptics.select();return;}
        if(screen.equals("haptics")){hapticAt=Math.floorMod(hapticAt+direction,6);render();sampleHaptic();return;}
        if(screen.equals("memory")){int next=Math.max(0,Math.min(memoryPages()-1,memoryPage+direction));if(next==memoryPage){haptics.boundary();return;}memoryPage=next;haptics.reveal();renderTurn(direction);return;}
        if(screen.equals("flash")){moveFlash(direction);return;}
        if(screen.equals("session")&&session!=null){
            if(session.studying()){int next=Math.max(0,Math.min(session.learn.size()-1,session.learning+direction));if(next==session.learning){haptics.boundary();return;}session.learning=next;haptics.reveal();persist(null);renderTurn(direction);return;}
            if(session.complete()){haptics.boundary();return;}
            if(session.feedback){if(direction>0)nextAnswer();else openMemory(session.current());return;}
            if(session.recall){if(!session.revealed){haptics.boundary();return;}session.focus=session.focus<0?(direction>0?0:1):Math.floorMod(session.focus+direction,2);updateRecallFocus();haptics.tick();ui.removeCallbacks(focusSave);ui.postDelayed(focusSave,350);return;}
            session.focus=session.focus<0?(direction>0?0:session.options.size()-1):Math.floorMod(session.focus+direction,session.options.size());updateFocus();haptics.tick();ui.removeCallbacks(focusSave);ui.postDelayed(focusSave,350);
        }
    }
    private long lastRotary=0;
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_SCROLL){float delta=e.getAxisValue(MotionEvent.AXIS_SCROLL);if(delta==0)delta=e.getAxisValue(MotionEvent.AXIS_VSCROLL);if(delta!=0){long now=SystemClock.elapsedRealtime();if(now-lastRotary>=75){lastRotary=now;rotate(delta<0?1:-1);}return true;}}return super.dispatchGenericMotionEvent(e);}
    @Override public boolean onKeyDown(int code,KeyEvent e){if(code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_UP){rotate(code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);return true;}if(code==KeyEvent.KEYCODE_DPAD_CENTER&&screen.equals("studysettings")&&studyFocus>=0){studyViews.get(studyFocus).performClick();return true;}if(code==KeyEvent.KEYCODE_DPAD_CENTER&&screen.equals("review")&&reviewFocus>=0){reviewViews.get(reviewFocus).performClick();return true;}if(code==KeyEvent.KEYCODE_DPAD_CENTER&&screen.equals("sound")){soundViews.get(soundFocus-1).performClick();return true;}if(code==KeyEvent.KEYCODE_DPAD_CENTER&&screen.equals("home")&&homeFocus>=0){homeViews.get(homeFocus).performClick();return true;}if(code==KeyEvent.KEYCODE_DPAD_CENTER&&screen.equals("session")&&session!=null&&session.focus>=0&&!session.feedback){if(session.recall){if(session.revealed&&tap())gradeRecall(session.focus==1);}else if(tap())answer(session.focus);return true;}return super.onKeyDown(code,e);}

    final class Dial extends FrameLayout {
        Paint p=new Paint(3);float downX,downY,bezelAngle,bezelAccum;boolean consumed,bezel;
        Dial(){super(MainActivity.this);setBackgroundColor(BG);setWillNotDraw(false);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(()->render());}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-px(10);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(3));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xff1c2923);c.drawArc(cx-r,cy-r,cx+r,cy+r,145,250,false,p);float portion=0;
            if(ready){if(screen.equals("session")&&session!=null)portion=session.studying()?(float)session.learning/Math.max(1,session.learn.size()):(float)session.at/Math.max(1,session.queue.size());else if(screen.equals("flash")&&!flashWords.isEmpty())portion=(float)(flashAt+1)/flashWords.size();else portion=Math.min(1f,activeNewToday(System.currentTimeMillis())/(float)limit);}
            p.setColor(MINT);c.drawArc(cx-r,cy-r,cx+r,cy+r,145,250*portion,false,p);
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();float radius=(float)Math.hypot(x-getWidth()/2f,y-getHeight()/2f);float angle=(float)Math.toDegrees(Math.atan2(y-getHeight()/2f,x-getWidth()/2f));if(e.getActionMasked()==MotionEvent.ACTION_DOWN){stopScroll();downX=x;downY=y;consumed=false;bezel=radius>px(214);bezelAngle=angle;bezelAccum=0;}
            if(bezel&&e.getActionMasked()==MotionEvent.ACTION_MOVE&&radius<px(185))bezel=false;
            if(bezel&&e.getActionMasked()==MotionEvent.ACTION_MOVE){float d=angle-bezelAngle;if(d>180)d-=360;if(d< -180)d+=360;bezelAngle=angle;bezelAccum+=d;if(Math.abs(bezelAccum)>=13){if(!consumed){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();}consumed=true;rotate(bezelAccum>0?1:-1);bezelAccum=0;}if(consumed)return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!consumed&&x-downX>px(100)&&Math.abs(y-downY)<px(70)){consumed=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();back();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP&&screen.equals("flash")&&!consumed&&Math.abs(y-downY)>px(70)&&Math.abs(x-downX)<px(70)){consumed=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();moveFlash(y<downY?1:-1);return true;}
            if(consumed)return true;return super.dispatchTouchEvent(e);
        }
    }
}
