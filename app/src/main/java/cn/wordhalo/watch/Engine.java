package cn.wordhalo.watch;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Pure Java scheduling and session rules. No device or network dependency. */
public final class Engine {
    public static final long MINUTE=60_000L, DAY=86_400_000L;
    public static final long[] INTERVALS={10*MINUTE, DAY, 3*DAY, 7*DAY, 14*DAY, 30*DAY, 60*DAY};
    public static class Word {
        public final String id, meaning, pos, scene, hint;
        public final int bank;
        public final long books;
        public Word(String id,String meaning,String pos,int bank,String scene,String hint) {
            this(id,meaning,pos,bank,scene,hint,(bank&1)!=0?BookCatalog.CET4_STAR:BookCatalog.CET6_STAR);
        }
        public Word(String id,String meaning,String pos,int bank,String scene,String hint,long books) {
            this.id=id;this.meaning=meaning;this.pos=pos;this.bank=bank;this.scene=scene;this.hint=hint;this.books=books;
        }
    }
    public static class Progress {
        public String id, firstDay="", lastDay="";
        public int stage=0, lapses=0, attempts=0;
        public long due=0;
        public boolean trouble=false;
        public Progress(String id){this.id=id;}
    }
    public static class Session {
        public ArrayList<String> queue=new ArrayList<>(), learn=new ArrayList<>(), options=new ArrayList<>();
        public HashSet<String> answered=new HashSet<>(), retried=new HashSet<>();
        public int at=0, learning=0, correct=0, bank=6, focus=-1;
        public long books=BookCatalog.defaultMask();
        public boolean feedback=false, lastCorrect=false, mistakesOnly=false, recall=false, revealed=false, quiet=false;
        public String chosen="";
        public long seed;
        public boolean studying(){return learning<learn.size();}
        public boolean complete(){return at>=queue.size();}
        public String current(){return complete()?"":queue.get(at);}
    }
    public static String today(long now){return java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
    public static boolean inBank(Word w,int bank){return bank==0 || (bank==6?w.bank==2:(w.bank&1)!=0);}
    public static boolean inBooks(Word w,long books){return (w.books&books)!=0;}
    public static int studiedToday(Map<String,Progress> all,long now){String d=today(now);int n=0;for(Progress p:all.values())if(d.equals(p.lastDay))n++;return n;}
    public static int newToday(Map<String,Progress> all,long now){String d=today(now);int n=0;for(Progress p:all.values())if(d.equals(p.firstDay))n++;return n;}
    public static Session start(List<Word> words,Map<String,Progress> progress,int bank,int newLimit,boolean wrong,long now) {
        return start(words,progress,bank,newLimit,wrong,now,10,false);
    }
    public static Session start(List<Word> words,Map<String,Progress> progress,int bank,int newLimit,boolean wrong,long now,int batchSize,boolean recall) {
        long books=bank==6?BookCatalog.CET6_STAR:BookCatalog.CET4_STAR;
        Session s=start(words,progress,books,newLimit,wrong,now,batchSize,recall);s.bank=bank;return s;
    }
    public static Session start(List<Word> words,Map<String,Progress> progress,long books,int newLimit,boolean wrong,long now,int batchSize,boolean recall) {
        batchSize=Math.max(1,Math.min(20,batchSize));
        books=BookCatalog.sanitize(books);
        Session s=new Session();s.seed=now;s.books=books;s.mistakesOnly=wrong;
        s.recall=recall;
        ArrayList<Word> due=new ArrayList<>(),fresh=new ArrayList<>();
        for(Word w:words){if(!inBooks(w,books))continue;Progress p=progress.get(w.id);
            if(wrong){if(p!=null&&p.trouble)due.add(w);}
            else if(p==null)fresh.add(w);else if(p.due<=now)due.add(w);
        }
        // Fill each group with the words whose predicted retention is lowest.
        // Eligibility still follows the due time, so this only prioritizes genuinely due words.
        due.sort(MemoryCurve.priority(progress,now));
        for(Word w:due){if(s.queue.size()>=batchSize)break;s.queue.add(w.id);}
        int allowance=Math.max(0,newLimit-newToday(progress,now));
        if(!wrong)for(Word w:fresh){if(s.queue.size()>=batchSize||s.learn.size()>=allowance)break;s.queue.add(w.id);s.learn.add(w.id);}
        Collections.shuffle(s.queue,new Random(s.seed));
        return s;
    }
    /** Five-card, self-rated session designed for silent, seconds-long watch interactions. */
    public static Session startQuiet(List<Word> words,Map<String,Progress> progress,long books,int newLimit,long now) {
        Session s=start(words,progress,books,newLimit,false,now,5,true);
        s.quiet=true;
        // The reveal itself is the first exposure for fresh words in this compact flow.
        s.learning=s.learn.size();
        return s;
    }
    public static List<String> choices(Word target,List<Word> words,long seed) {
        return choices(target,words,BookCatalog.ALL_MASK,seed);
    }
    public static List<String> choices(Word target,List<Word> words,long books,long seed) {
        ArrayList<Word> candidates=new ArrayList<>();
        for(Word w:words)if(inBooks(w,books)&&!w.id.equals(target.id)&&w.pos.equals(target.pos))candidates.add(w);
        Collections.shuffle(candidates,new Random(seed));
        ArrayList<String> result=new ArrayList<>();HashSet<String> meanings=new HashSet<>();
        result.add(target.id);meanings.add(target.meaning);
        String targetStem=stem(target.id);
        for(Word w:candidates){if(result.size()==3)break;if(!stem(w.id).equals(targetStem)&&disjoint(w.meaning,target.meaning)&&meanings.add(w.meaning))result.add(w.id);}
        if(result.size()<3)for(Word w:words){if(result.size()==3)break;if(inBooks(w,books)&&!w.id.equals(target.id)&&meanings.add(w.meaning)&&disjoint(w.meaning,target.meaning))result.add(w.id);}
        Collections.shuffle(result,new Random(seed^0xABCDEF));
        return result;
    }
    private static String stem(String w){return w.replaceAll("(ing|ed|es|s)$","");}
    private static boolean disjoint(String a,String b){for(String x:a.split("[；;，,、]"))for(String y:b.split("[；;，,、]"))if(x.length()>1&&y.length()>1&&(x.contains(y)||y.contains(x)))return false;return true;}
    /** Only the first attempt in a session changes scheduling or the daily count. */
    public static Progress answer(Session s,Map<String,Progress> all,boolean correct,String choice,long now){
        if(s.feedback||s.complete()||s.studying()||s.recall&&!s.revealed)return null;
        String id=s.current();s.feedback=true;s.lastCorrect=correct;s.chosen=choice;
        Progress changed=null;
        if(s.answered.add(id)){
            Progress p=all.get(id);if(p==null){p=new Progress(id);p.firstDay=today(now);all.put(id,p);}
            p.attempts++;p.lastDay=today(now);
            if(correct){p.stage=Math.min(6,p.stage+1);p.trouble=false;s.correct++;}
            else{p.stage=0;p.lapses++;p.trouble=true;}
            p.due=now+INTERVALS[p.stage];changed=p;
        }
        if(!correct&&s.retried.add(id))s.queue.add(Math.min(s.queue.size(),s.at+4),id);
        return changed;
    }
    public static void next(Session s){if(!s.feedback)return;s.at++;s.feedback=false;s.revealed=false;s.options.clear();s.focus=-1;s.chosen="";}
    private Engine(){}
}
