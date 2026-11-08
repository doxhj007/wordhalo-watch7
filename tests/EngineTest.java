package cn.wordhalo.watch;
import java.util.*;
import java.nio.file.*;
public class EngineTest {
    static int count=0;
    static void check(boolean b,String message){count++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<Engine.Word> words=new ArrayList<>();for(String line:Files.readAllLines(Path.of(args[0]))){String[] a=line.split("\t",-1);words.add(new Engine.Word(a[0],a[1],a[2],Integer.parseInt(a[3]),a[4],a[5]));}
        Map<String,Engine.Word> by=new HashMap<>();for(Engine.Word w:words)by.put(w.id,w);
        check(words.size()==1945,"Bundled CET6 extension dataset");check(by.size()==words.size(),"No duplicate word IDs");
        check(words.stream().filter(w->Engine.inBank(w,6)).count()==1945,"CET6 extension distinct from CET4");
        check(words.stream().filter(w->Engine.inBank(w,4)).count()==0,"CET4 excluded");
        check(words.stream().filter(w->!w.scene.isEmpty()).count()>40,"Offline illustration coverage");
        long now=System.currentTimeMillis();Map<String,Engine.Progress> all=new HashMap<>();
        Engine.Session s=Engine.start(words,all,6,20,false,now);
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
        Engine.Session capped=Engine.start(words,all,6,10,false,now);
        check(capped.complete(),"Daily new-word limit enforced");
        Engine.Session review=Engine.start(words,all,6,10,false,now+11*Engine.MINUTE);
        check(review.queue.size()==1&&review.learn.isEmpty(),"Due review allowed after daily cap");
        Engine.answer(review,all,true,review.current(),now+11*Engine.MINUTE);
        check(!p.trouble&&p.stage==1,"Later successful recall clears trouble");
        check(p.due==now+11*Engine.MINUTE+Engine.DAY,"Correct review schedules one day");
        Engine.Session wrong=Engine.start(words,all,6,20,true,now);check(wrong.complete(),"Resolved trouble no longer in wrong list");
        p.trouble=true;wrong=Engine.start(words,all,6,20,true,now);check(wrong.queue.contains(p.id)&&wrong.learn.isEmpty(),"Wrong list ignores due date and contains no new words");
        check(Engine.newToday(all,now+2*Engine.DAY)==0,"Daily quota resets by local date");
        // Exhaustively validate every word's generated quiz choices.
        for(Engine.Word w:words){List<String> choices=Engine.choices(w,words,w.id.hashCode());check(choices.size()==3,"Three choices: "+w.id);check(new HashSet<>(choices).size()==3,"Distinct choices: "+w.id);check(choices.contains(w.id),"Correct answer exists: "+w.id);Set<String> meanings=new HashSet<>();for(String id:choices)meanings.add(by.get(id).meaning);check(meanings.size()==3,"Distinct definitions: "+w.id);check(!w.meaning.isBlank()&&w.meaning.length()<=24,"Short nonempty meaning: "+w.id);}
        // Full randomized sessions must terminate and never leak a bank or update repeated answers.
        Random r=new Random(73);for(int run=0;run<80;run++){
            Map<String,Engine.Progress> m=new HashMap<>();Engine.Session q=Engine.start(words,m,6,20,false,now+run);q.learning=q.learn.size();int n=0;
            while(!q.complete()&&n<100){Engine.answer(q,m,r.nextBoolean(),"choice",now);Engine.next(q);n++;}
            check(q.complete()&&n<=20,"Bounded retries");check(m.size()==10,"Ten unique progress records");check(q.queue.stream().allMatch(id->Engine.inBank(by.get(id),6)),"No bank leakage");
        }
        for(int size:new int[]{5,10,20}){
            Map<String,Engine.Progress> m=new HashMap<>();Engine.Session q=Engine.start(words,m,6,50,false,now,size,true);check(q.queue.size()==size&&q.learn.size()==size,"Configurable batch size");check(q.recall,"Recall mode belongs to the saved group");q.learning=q.learn.size();
            check(Engine.answer(q,m,true,q.current(),now)==null&&m.isEmpty(),"Cannot self-grade before revealing");q.revealed=true;String missed=q.current();Engine.Progress wrongRecall=Engine.answer(q,m,false,"",now);check(wrongRecall.trouble,"Forgotten recall enters mistake list");Engine.next(q);check(!q.revealed,"Next word hides the meaning again");int steps=0;
            while(!q.complete()&&steps<50){q.revealed=true;Engine.answer(q,m,true,q.current(),now);Engine.next(q);steps++;}
            check(q.complete(),"Recall group terminates");check(q.correct==size-1&&q.answered.size()==size,"Recall retry cannot inflate results");check(m.get(missed).attempts==1&&m.get(missed).trouble,"Immediate recall retry keeps the lapse");
            Engine.Session limited=Engine.start(words,new HashMap<>(),6,3,false,now,size,true);check(limited.queue.size()==3,"Batch size still respects remaining new-word budget");
        }
        check(Engine.start(words,new HashMap<>(),6,50,false,now,100,true).queue.size()==20,"Oversized batches are bounded");
        check(Engine.start(words,new HashMap<>(),6,50,false,now,0,false).queue.size()==1,"Empty batch preference cannot break scheduling");
        Engine.Progress curve=new Engine.Progress("curve");curve.stage=3;curve.due=now+Engine.INTERVALS[curve.stage];
        check(Math.abs(MemoryCurve.retention(curve,now)-1)<.0001,"Fresh review starts at full predicted retention");
        check(Math.abs(MemoryCurve.retention(curve,curve.due)-MemoryCurve.REVIEW_THRESHOLD)<.0001,"Due time meets the review threshold");
        check(MemoryCurve.retention(curve,now+Engine.DAY)>MemoryCurve.retention(curve,now+3*Engine.DAY),"Retention falls over time");
        check(MemoryCurve.entering(java.util.List.of(curve),now,curve.due)==1,"Forecast counts words entering the review zone");
        check(MemoryCurve.dueWithin(java.util.List.of(curve),now,curve.due)==1,"Daily plan counts the exact due time");
        curve.trouble=true;check(MemoryCurve.percent(curve,now)==62,"A forgotten word immediately enters the low-retention zone");
        check(MemoryCurve.forecast(java.util.List.of(curve),now).length==8,"Seven-day forecast includes today");
        check(by.get("disrupt").pos.equals("v.")&&by.get("serpent").meaning.equals("蛇"),"Reviewed common senses are bundled");
        System.out.println("PASS: "+count+" assertions; full vocabulary, scheduling, recall, batch limits, retries and 80 simulated sessions.");
    }
}
