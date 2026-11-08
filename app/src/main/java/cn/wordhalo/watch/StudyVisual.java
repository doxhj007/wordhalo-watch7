package cn.wordhalo.watch;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Static vector graphics: no background timer, bitmap allocation or looping animation. */
final class StudyVisual extends View {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int color;final float portion;final int[] values;final String[] labels;
    StudyVisual(Context c,int color,float portion){super(c);this.color=color;this.portion=Math.max(0,Math.min(1,portion));values=null;labels=null;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    StudyVisual(Context c,int color,int[] values,String[] labels){super(c);this.color=color;this.values=values.clone();this.labels=labels.clone();portion=0;StringBuilder description=new StringBuilder("每日练词数量：");for(int i=0;i<values.length;i++)description.append(labels[i]).append("，").append(values[i]).append("词；");setContentDescription(description);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int save=c.save();if(values==null){float size=Math.min(getWidth(),getHeight());c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/190,size/190);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(3.2f);for(int i=0;i<60;i++){double angle=Math.toRadians(i*6-90);p.setColor(i<Math.round(portion*60)?color:0xff22372e);float outer=87,inner=i%5==0?76:81;c.drawLine(95+(float)Math.cos(angle)*inner,95+(float)Math.sin(angle)*inner,95+(float)Math.cos(angle)*outer,95+(float)Math.sin(angle)*outer,p);}}
        else{c.scale(getWidth()/344f,getHeight()/156f);int max=1;for(int value:values)max=Math.max(max,value);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);for(int i=0;i<values.length;i++){float x=27+i*48.3f,h=88f*values[i]/max;p.setColor(0xff14251d);c.drawRoundRect(x-13,29,x+13,117,9,9,p);if(values[i]>0){p.setColor(i==values.length-1?color:0xff679b83);c.drawRoundRect(x-13,117-Math.max(6,h),x+13,117,9,9,p);}p.setTextSize(18);p.setColor(0xffe6f1e9);c.drawText(Integer.toString(values[i]),x,19,p);p.setTextSize(15);p.setColor(0xffa6b6ad);c.drawText(labels[i],x,145,p);}}
        c.restoreToCount(save);
    }
}
