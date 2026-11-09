package cn.wordhalo.watch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.PathInterpolator;

/** One continuous outline follows focus; rapid turns retarget from its CURRENT bounds. */
final class FocusHalo extends View {
    final Paint p=new Paint(3);final RectF current=new RectF();
    ValueAnimator motion;float stroke=2,focusAlpha=1;boolean placed=false;
    FocusHalo(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);}
    void move(RectF target,boolean animate){
        if(motion!=null){motion.removeAllListeners();motion.cancel();motion=null;setLayerType(LAYER_TYPE_NONE,null);}
        if(!placed||!animate||!ValueAnimator.areAnimatorsEnabled()){current.set(target);focusAlpha=1;placed=true;invalidate();return;}
        RectF start=new RectF(current);float distance=(float)Math.hypot(target.centerX()-start.centerX(),target.centerY()-start.centerY());focusAlpha=.58f;motion=ValueAnimator.ofFloat(0,1);motion.setDuration((long)Math.min(235,165+distance*.18f));motion.setInterpolator(new PathInterpolator(.16f,1f,.3f,1f));setLayerType(LAYER_TYPE_HARDWARE,null);
        ValueAnimator running=motion;motion.addUpdateListener(v->{float f=(float)v.getAnimatedValue();focusAlpha=.58f+.42f*f;current.set(start.left+(target.left-start.left)*f,start.top+(target.top-start.top)*f,start.right+(target.right-start.right)*f,start.bottom+(target.bottom-start.bottom)*f);invalidate();});motion.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){if(motion==running){focusAlpha=1;setLayerType(LAYER_TYPE_NONE,null);invalidate();}}});motion.start();
    }
    void stop(){if(motion!=null){motion.removeAllListeners();motion.cancel();motion=null;}focusAlpha=1;setLayerType(LAYER_TYPE_NONE,null);}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){if(!placed)return;float radius=12*stroke;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(0xffadecc8);p.setAlpha(Math.round(255*focusAlpha));c.drawRoundRect(current,radius,radius,p);}
}
