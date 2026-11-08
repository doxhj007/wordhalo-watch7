package cn.wordhalo.watch;

import java.util.*;

/** Personal retention forecast used by both scheduling and the dashboard. */
final class MemoryCurve {
    static final double REVIEW_THRESHOLD=0.85;
    static final double TROUBLE_START=0.62;

    static long interval(Engine.Progress p){
        return Engine.INTERVALS[Math.max(0,Math.min(Engine.INTERVALS.length-1,p.stage))];
    }
    static long reviewedAt(Engine.Progress p){return p.due-interval(p);}
    static double retention(Engine.Progress p,long when){
        if(p==null)return 0;
        long span=interval(p),elapsed=Math.max(0,when-reviewedAt(p));
        double start=p.trouble?TROUBLE_START:1.0;
        return Math.max(.01,Math.min(1,start*Math.pow(REVIEW_THRESHOLD,elapsed/(double)span)));
    }
    static int percent(Engine.Progress p,long when){return (int)Math.round(retention(p,when)*100);}
    static double average(Collection<Engine.Progress> items,long when){
        if(items.isEmpty())return 1;
        double total=0;for(Engine.Progress p:items)total+=retention(p,when);
        return total/items.size();
    }
    static int below(Collection<Engine.Progress> items,long when){
        int count=0;for(Engine.Progress p:items)if(retention(p,when)<=REVIEW_THRESHOLD)count++;
        return count;
    }
    static int entering(Collection<Engine.Progress> items,long from,long until){
        int count=0;for(Engine.Progress p:items)
            if(retention(p,from)>REVIEW_THRESHOLD&&retention(p,until)<=REVIEW_THRESHOLD)count++;
        return count;
    }
    static int dueWithin(Collection<Engine.Progress> items,long from,long until){
        int count=0;for(Engine.Progress p:items)if(p.due>from&&p.due<=until)count++;
        return count;
    }
    static double[] forecast(Collection<Engine.Progress> items,long now){
        double[] values=new double[8];
        for(int day=0;day<values.length;day++)values[day]=average(items,now+day*Engine.DAY);
        return values;
    }
    static Comparator<Engine.Word> priority(Map<String,Engine.Progress> progress,long now){
        return Comparator.<Engine.Word>comparingDouble(w->retention(progress.get(w.id),now))
            .thenComparingLong(w->progress.get(w.id).due)
            .thenComparing(w->w.id);
    }
    private MemoryCurve(){}
}
