package cn.wordhalo.watch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.PathInterpolator;

/** One continuous outline follows focus; rapid turns retarget from its CURRENT bounds. */
final class FocusHalo extends View {
    final Paint p=new Paint(3);final RectF current=new RectF();
    ValueAnimator motion;float stroke=2,energy=0;boolean placed=false;
    FocusHalo(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);}
    void move(RectF target,boolean animate){
        if(motion!=null)motion.cancel();
        if(!placed||!animate||!ValueAnimator.areAnimatorsEnabled()){current.set(target);placed=true;energy=0;invalidate();return;}
        RectF start=new RectF(current);motion=ValueAnimator.ofFloat(0,1);motion.setDuration(230);motion.setInterpolator(new PathInterpolator(.16f,.76f,.18f,1));
        motion.addUpdateListener(v->{float f=(float)v.getAnimatedValue();energy=1-f;current.set(start.left+(target.left-start.left)*f,start.top+(target.top-start.top)*f,start.right+(target.right-start.right)*f,start.bottom+(target.bottom-start.bottom)*f);invalidate();});motion.start();
    }
    void stop(){if(motion!=null)motion.cancel();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){if(!placed)return;float radius=26*stroke/2;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke*(3.2f+energy));p.setColor(0x24b8f6d2);c.drawRoundRect(current,radius,radius,p);p.setStyle(Paint.Style.FILL);p.setColor(0x12b8f6d2);c.drawRoundRect(current,radius,radius,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(0xffb8f6d2);c.drawRoundRect(current,radius,radius,p);}
}
