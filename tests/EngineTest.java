package cn.wordhalo.watch;
import java.util.*;
import java.nio.file.*;
public class EngineTest {
    static int count=0;
    static void check(boolean b,String message){count++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<Engine.Word> words=new ArrayList<>();for(String line:Files.readAllLines(Path.of(args[0]))){String[] a=line.split("\t",-1);words.add(new Engine.Word(a[0],a[1],a[2],Integer.parseInt(a[3]),a[4],a[5],Long.parseLong(a[6])));}
        Map<String,Engine.Word> by=new HashMap<>();for(Engine.Word w:words)by.put(w.id,w);
        check(words.size()==7981,"Bundled six-book union");check(by.size()==words.size(),"No duplicate word IDs");
        Map<Long,List<Engine.Word>> books=new HashMap<>();for(BookCatalog.Book book:BookCatalog.ALL)books.put(book.bit,new ArrayList<>());
        for(String line:Files.readAllLines(Path.of(args[1]))){String[] a=line.split("\t",2);long bit=Long.parseLong(a[0]);Engine.Word word=by.get(a[1]);check(word!=null,"Book order resolves: "+a[1]);books.get(bit).add(word);}
        String[][] edges={{"inherent","unexpected"},{"abandon","zoo"},{"corresponding","worksheet"},{"same","commute"},{"abandon","wound"},{"bias","henceforth"}};
        for(int i=0;i<BookCatalog.ALL.size();i++){BookCatalog.Book book=BookCatalog.ALL.get(i);List<Engine.Word> ordered=books.get(book.bit);check(ordered.size()==book.headwords,"Exact count for "+book.label());check(new HashSet<>(ordered).size()==ordered.size(),"No duplicate headword in "+book.label());check(ordered.get(0).id.equals(edges[i][0])&&ordered.get(ordered.size()-1).id.equals(edges[i][1]),"Original order for "+book.label());check(ordered.stream().allMatch(w->Engine.inBooks(w,book.bit)),"Membership for "+book.label());check(words.stream().filter(w->Engine.inBooks(w,book.bit)).count()==book.headwords,"Union mask count for "+book.label());}
        check(words.stream().filter(w->!w.scene.isEmpty()).count()>100,"Offline illustration coverage");
        long selected=BookCatalog.CET6_STAR;List<Engine.Word> active=books.get(selected);
        long now=System.currentTimeMillis();Map<String,Engine.Progress> all=new HashMap<>();
        Engine.Session s=Engine.start(active,all,selected,20,false,now,10,false);
        check(s.queue.size()==10&&s.learn.size()==10,"Fresh batch contains 10 study cards");check(s.studying(),"Study new words before quiz");
        check(Engine.answer(s,all,true,s.current(),now)==null,"Study screen cannot grade");s.learning=s.learn.size();
        String first=s.current();Engine.Progress p=Engine.answer(s,all,false,"bad",now);
        check(p.trouble&&p.due==now+10*Engine.MINUTE,"Wrong answer due in ten minutes");check(s.queue.size()==11&&s.queue.get(4).equals(first),"Mistake reappears after three questions");
        check(Engine.answer(s,all,true,first,now)==null&&p.attempts==1,"Double taps cannot grade twice");
        check(Engine.studiedToday(all,now)==1&&Engine.newToday(all,now)==1,"Unique daily count");
        Engine.next(s);for(int i=0;i<3;i++){Engine.answer(s,all,true,s.current(),now);Engine.next(s);}
        check(s.current().equals(first),"Retry is reachable");Engine.answer(s,all,true,first,now);
        check(p.trouble&&p.stage==0&&p.attempts==1,"Immediate retry cannot erase lapse or promote mastery");
        Engine.next(s);while(!s.complete()){Engine.answer(s,all,true,s.current(),now);Engine.next(s);}
        check(s.correct==9&&s.answered.size()==10,"Retry excluded from first-attempt accuracy");
        check(all.size()==10&&Engine.newToday(all,now)==10,"Ten new words stored");
        Engine.Session capped=Engine.start(active,all,selected,10,false,now,10,false);
        check(capped.complete(),"Daily new-word limit enforced");
        Engine.Session review=Engine.start(active,all,selected,10,false,now+11*Engine.MINUTE,10,false);
        check(review.queue.size()==1&&review.learn.isEmpty(),"Due review allowed after daily cap");
        Engine.answer(review,all,true,review.current(),now+11*Engine.MINUTE);
        check(!p.trouble&&p.stage==1,"Later successful recall clears trouble");
        check(p.due==now+11*Engine.MINUTE+Engine.DAY,"Correct review schedules one day");
        Engine.Session wrong=Engine.start(active,all,selected,20,true,now,10,false);check(wrong.complete(),"Resolved trouble no longer in wrong list");
        p.trouble=true;wrong=Engine.start(active,all,selected,20,true,now,10,false);check(wrong.queue.contains(p.id)&&wrong.learn.isEmpty(),"Wrong list ignores due date and contains no new words");
        check(Engine.newToday(all,now+2*Engine.DAY)==0,"Daily quota resets by local date");
        // Exhaustively validate every word's generated quiz choices.
        for(Engine.Word w:words){List<String> choices=Engine.choices(w,words,w.books,w.id.hashCode());check(choices.size()==3,"Three choices: "+w.id);check(new HashSet<>(choices).size()==3,"Distinct choices: "+w.id);check(choices.contains(w.id),"Correct answer exists: "+w.id);Set<String> meanings=new HashSet<>();for(String id:choices)meanings.add(by.get(id).meaning);check(meanings.size()==3,"Distinct definitions: "+w.id);check(!w.meaning.isBlank()&&w.meaning.length()<=24,"Short nonempty meaning: "+w.id);}
        // Full randomized sessions must terminate and never leak a bank or update repeated answers.
        Random r=new Random(73);for(int run=0;run<80;run++){
            Map<String,Engine.Progress> m=new HashMap<>();Engine.Session q=Engine.start(active,m,selected,20,false,now+run,10,false);q.learning=q.learn.size();int n=0;
            while(!q.complete()&&n<100){Engine.answer(q,m,r.nextBoolean(),"choice",now);Engine.next(q);n++;}
            check(q.complete()&&n<=20,"Bounded retries");check(m.size()==10,"Ten unique progress records");check(q.queue.stream().allMatch(id->Engine.inBooks(by.get(id),selected)),"No book leakage");
        }
        for(int size:new int[]{5,10,20}){
            Map<String,Engine.Progress> m=new HashMap<>();Engine.Session q=Engine.start(active,m,selected,50,false,now,size,true);check(q.queue.size()==size&&q.learn.size()==size,"Configurable batch size");check(q.recall&&q.books==selected,"Recall mode and books belong to the saved group");q.learning=q.learn.size();
            check(Engine.answer(q,m,true,q.current(),now)==null&&m.isEmpty(),"Cannot self-grade before revealing");q.revealed=true;String missed=q.current();Engine.Progress wrongRecall=Engine.answer(q,m,false,"",now);check(wrongRecall.trouble,"Forgotten recall enters mistake list");Engine.next(q);check(!q.revealed,"Next word hides the meaning again");int steps=0;
            while(!q.complete()&&steps<50){q.revealed=true;Engine.answer(q,m,true,q.current(),now);Engine.next(q);steps++;}
            check(q.complete(),"Recall group terminates");check(q.correct==size-1&&q.answered.size()==size,"Recall retry cannot inflate results");check(m.get(missed).attempts==1&&m.get(missed).trouble,"Immediate recall retry keeps the lapse");
            Engine.Session limited=Engine.start(active,new HashMap<>(),selected,3,false,now,size,true);check(limited.queue.size()==3,"Batch size still respects remaining new-word budget");
        }
        check(Engine.start(active,new HashMap<>(),selected,50,false,now,100,true).queue.size()==20,"Oversized batches are bounded");
        check(Engine.start(active,new HashMap<>(),selected,50,false,now,0,false).queue.size()==1,"Empty batch preference cannot break scheduling");
        Engine.Session quiet=Engine.startQuiet(active,new HashMap<>(),selected,50,now);
        check(quiet.quiet&&quiet.recall,"Silent quick study is an explicit self-rated session");
        check(quiet.queue.size()==5&&quiet.learn.size()==5,"Silent quick study is capped at five cards");
        check(!quiet.studying()&&!quiet.revealed,"The reveal is the first exposure in silent quick study");
        check(Engine.answer(quiet,new HashMap<>(),true,quiet.current(),now)==null,"Silent cards cannot be graded before reveal");
        Engine.Progress curve=new Engine.Progress("curve");curve.stage=3;curve.due=now+Engine.INTERVALS[curve.stage];
        check(Math.abs(MemoryCurve.retention(curve,now)-1)<.0001,"Fresh review starts at full predicted retention");
        check(Math.abs(MemoryCurve.retention(curve,curve.due)-MemoryCurve.REVIEW_THRESHOLD)<.0001,"Due time meets the review threshold");
        check(MemoryCurve.retention(curve,now+Engine.DAY)>MemoryCurve.retention(curve,now+3*Engine.DAY),"Retention falls over time");
        check(MemoryCurve.entering(java.util.List.of(curve),now,curve.due)==1,"Forecast counts words entering the review zone");
        check(MemoryCurve.dueWithin(java.util.List.of(curve),now,curve.due)==1,"Daily plan counts the exact due time");
        curve.trouble=true;check(MemoryCurve.percent(curve,now)==62,"A forgotten word immediately enters the low-retention zone");
        check(MemoryCurve.forecast(java.util.List.of(curve),now).length==8,"Seven-day forecast includes today");
        check(by.get("disrupt").pos.equals("v.")&&by.get("omelete").meaning.equals("煎蛋卷"),"Reviewed common senses are bundled");
        System.out.println("PASS: "+count+" assertions; six separate books, exact order, scheduling, recall, batch limits, retries and 80 simulated sessions.");
    }
}
