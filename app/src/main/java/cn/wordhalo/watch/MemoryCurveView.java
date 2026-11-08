package cn.wordhalo.watch;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** A static seven-day retention forecast sized for the 44 mm round display. */
final class MemoryCurveView extends View {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final double[] values;final int color;
    MemoryCurveView(Context context,int color,double[] values){
        super(context);this.color=color;this.values=values.clone();
        setContentDescription(String.format(Locale.CHINA,
            "个人遗忘曲线：现在百分之%d，一天后百分之%d，七天后百分之%d；百分之85为复习提醒线",
            pct(0),pct(1),pct(7)));
    }
    int pct(int at){return (int)Math.round(values[Math.max(0,Math.min(values.length-1,at))]*100);}
    float y(double value){return 118-(float)((Math.max(.40,Math.min(1,value))-.40)/.60*94);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int save=canvas.save();canvas.scale(getWidth()/344f,getHeight()/154f);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        paint.setStrokeWidth(1.5f);paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff294036);
        for(int percent:new int[]{50,70,85,100}){float yy=y(percent/100.0);canvas.drawLine(28,yy,328,yy,paint);}
        paint.setTextSize(13);paint.setTextAlign(Paint.Align.RIGHT);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff81958a);
        canvas.drawText("100",24,y(1)+5,paint);canvas.drawText("85",24,y(.85)+5,paint);canvas.drawText("50",24,y(.5)+5,paint);
        Path line=new Path(),fill=new Path();float step=300f/(values.length-1);
        for(int i=0;i<values.length;i++){float x=28+i*step,yy=y(values[i]);if(i==0){line.moveTo(x,yy);fill.moveTo(x,118);fill.lineTo(x,yy);}else{line.lineTo(x,yy);fill.lineTo(x,yy);}}
        fill.lineTo(328,118);fill.close();paint.setStyle(Paint.Style.FILL);paint.setColor(0x3329e6a0);canvas.drawPath(fill,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);canvas.drawPath(line,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setPathEffect(new DashPathEffect(new float[]{7,7},0));paint.setColor(0xffe4c487);canvas.drawLine(28,y(.85),328,y(.85),paint);paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(14);paint.setColor(0xffa6b6ad);
        canvas.drawText("现在",28,145,paint);canvas.drawText("3天",28+3*step,145,paint);canvas.drawText("7天",328,145,paint);
        canvas.restoreToCount(save);
    }
}
