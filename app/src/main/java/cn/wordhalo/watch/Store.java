package cn.wordhalo.watch;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Store extends SQLiteOpenHelper {
    Store(Context c){this(c,"words.db");}
    Store(Context c,String name){super(c,name,null,2);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE progress(id TEXT PRIMARY KEY,stage INTEGER,due INTEGER,lapses INTEGER,attempts INTEGER,firstDay TEXT,lastDay TEXT,trouble INTEGER)");db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY CHECK(id=1),json TEXT NOT NULL)");createPractice(db);}
    static void createPractice(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS practice(day TEXT NOT NULL,word TEXT NOT NULL,correct INTEGER NOT NULL,PRIMARY KEY(day,word))");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<2)createPractice(db);}
    static List<Engine.Word> words(Context c)throws Exception{
        ArrayList<Engine.Word> out=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getAssets().open("words.tsv"),StandardCharsets.UTF_8))){
            String line;while((line=r.readLine())!=null){String[] a=line.split("\t",-1);if(a.length==6)out.add(new Engine.Word(a[0],a[1],a[2],Integer.parseInt(a[3]),a[4],a[5]));}
        }return out;
    }
    Map<String,Engine.Progress> progress(){Map<String,Engine.Progress> m=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM progress",null)){while(c.moveToNext()){Engine.Progress p=new Engine.Progress(c.getString(0));p.stage=c.getInt(1);p.due=c.getLong(2);p.lapses=c.getInt(3);p.attempts=c.getInt(4);p.firstDay=c.getString(5);p.lastDay=c.getString(6);p.trouble=c.getInt(7)!=0;m.put(p.id,p);}}return m;}
    void save(Engine.Session s,Engine.Progress p)throws JSONException{
        String json=s==null?null:encode(s).toString();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(p!=null){ContentValues v=new ContentValues();v.put("id",p.id);v.put("stage",p.stage);v.put("due",p.due);v.put("lapses",p.lapses);v.put("attempts",p.attempts);v.put("firstDay",p.firstDay);v.put("lastDay",p.lastDay);v.put("trouble",p.trouble?1:0);db.replaceOrThrow("progress",null,v);}
            if(p!=null&&!p.lastDay.isEmpty())db.execSQL("INSERT OR IGNORE INTO practice(day,word,correct) VALUES(?,?,?)",new Object[]{p.lastDay,p.id,p.trouble?0:1});
            if(s==null)db.delete("state","id=1",null);else{ContentValues v=new ContentValues();v.put("id",1);v.put("json",json);db.replaceOrThrow("state",null,v);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    Engine.Session session()throws JSONException{try(Cursor c=getReadableDatabase().rawQuery("SELECT json FROM state WHERE id=1",null)){return c.moveToFirst()?decode(new JSONObject(c.getString(0))):null;}}
    Map<String,int[]> practice(){Map<String,int[]> result=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT day,COUNT(*),SUM(correct) FROM practice GROUP BY day",null)){while(c.moveToNext())result.put(c.getString(0),new int[]{c.getInt(1),c.getInt(2)});}return result;}
    static JSONObject encode(Engine.Session s)throws JSONException{return new JSONObject().put("queue",new JSONArray(s.queue)).put("learn",new JSONArray(s.learn)).put("options",new JSONArray(s.options)).put("answered",new JSONArray(s.answered)).put("retried",new JSONArray(s.retried)).put("at",s.at).put("learning",s.learning).put("correct",s.correct).put("bank",s.bank).put("focus",s.focus).put("feedback",s.feedback).put("lastCorrect",s.lastCorrect).put("wrong",s.mistakesOnly).put("chosen",s.chosen).put("seed",s.seed).put("recall",s.recall).put("revealed",s.revealed);}
    static Engine.Session decode(JSONObject o)throws JSONException{Engine.Session s=new Engine.Session();strings(o,"queue",s.queue);strings(o,"learn",s.learn);strings(o,"options",s.options);strings(o,"answered",s.answered);strings(o,"retried",s.retried);s.at=o.getInt("at");s.learning=o.getInt("learning");s.correct=o.getInt("correct");s.bank=o.getInt("bank");s.focus=o.getInt("focus");s.feedback=o.getBoolean("feedback");s.lastCorrect=o.getBoolean("lastCorrect");s.mistakesOnly=o.getBoolean("wrong");s.chosen=o.getString("chosen");s.seed=o.getLong("seed");s.recall=o.optBoolean("recall",false);s.revealed=o.optBoolean("revealed",false);return s;}
    static void strings(JSONObject o,String key,Collection<String> out)throws JSONException{JSONArray a=o.getJSONArray(key);for(int i=0;i<a.length();i++)out.add(a.getString(i));}
}
