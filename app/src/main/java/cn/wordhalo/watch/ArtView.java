package cn.wordhalo.watch;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Tiny original vector scenes: resolution independent and entirely offline. */
final class ArtView extends View {
    final Paint p=new Paint(3);final String scene;final int ink;
    ArtView(Context c,String scene,int ink){super(c);this.scene=scene;this.ink=ink;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void line(Canvas c,float... xy){for(int i=0;i<xy.length-2;i+=2)c.drawLine(xy[i],xy[i+1],xy[i+2],xy[i+3],p);}
    void circle(Canvas c,float x,float y,float r){c.drawCircle(x,y,r,p);}
    void arc(Canvas c,float x,float y,float w,float h,float a,float b){c.drawArc(x,y,x+w,y+h,a,b,false,p);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();float s=Math.min(getWidth()/120f,getHeight()/76f);c.translate((getWidth()-120*s)/2,(getHeight()-76*s)/2);c.scale(s,s);p.setColor(ink);p.setStrokeWidth(2.6f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(scene){
        case "sprout":line(c,30,66,90,66);line(c,60,66,60,32);arc(c,33,15,27,25,0,280);arc(c,60,6,27,27,0,290);line(c,60,42,45,29);line(c,60,29,76,16);break;
        case "lamp":circle(c,60,29,19);line(c,49,47,49,54,71,54,71,47);line(c,52,62,68,62);for(int i=0;i<5;i++){double a=(i*45+180)*Math.PI/180;line(c,60+(float)Math.cos(a)*27,29+(float)Math.sin(a)*27,60+(float)Math.cos(a)*33,29+(float)Math.sin(a)*33);}break;
        case "wall":c.drawRect(52,12,89,65,p);for(int y=28;y<65;y+=18)line(c,52,y,89,y);line(c,69,12,69,28);line(c,72,46,72,65);line(c,30,39,46,39,38,31);line(c,46,39,38,47);break;
        case "wave":Path wave=new Path();wave.moveTo(12,40);wave.cubicTo(29,-12,38,90,57,39);wave.cubicTo(75,-12,86,89,108,35);c.drawPath(wave,p);p.setAlpha(75);line(c,12,65,108,65);break;
        case "stack":for(int i=0;i<3;i++){float x=24+i*27;for(int j=0;j<=i;j++)c.drawRoundRect(x,56-j*13,x+23,65-j*13,5,5,p);}line(c,28,34,81,13,76,23);break;
        case "lens":circle(c,52,29,22);line(c,68,46,91,68);line(c,42,29,62,29);line(c,52,19,52,39);break;
        case "planet":circle(c,60,38,21);c.save();c.rotate(-25,60,38);c.drawOval(18,23,102,53,p);c.restore();circle(c,98,15,3);circle(c,25,63,2);break;
        case "glass":line(c,36,9,42,64,78,64,84,9,36,9);line(c,62,9,53,29,65,39,54,57);break;
        case "bridge":line(c,14,55,106,55);line(c,27,65,27,16);line(c,93,65,93,16);arc(c,27,-12,66,59,0,180);line(c,44,39,44,55);line(c,76,39,76,55);break;
        case "scale":line(c,60,14,60,65);line(c,44,65,76,65);line(c,25,26,95,26);line(c,31,26,21,47,41,47,31,26);line(c,89,26,79,47,99,47,89,26);arc(c,21,39,20,18,0,180);arc(c,79,39,20,18,0,180);break;
        case "arrow":line(c,18,39,99,39,80,20);line(c,99,39,80,58);line(c,14,25,37,25);line(c,14,53,37,53);break;
        case "shield":Path shield=new Path();shield.moveTo(60,9);shield.lineTo(86,20);shield.lineTo(83,45);shield.quadTo(74,62,60,69);shield.quadTo(44,60,37,45);shield.lineTo(34,20);shield.close();c.drawPath(shield,p);line(c,46,37,56,47,76,26);break;
        case "mountain":line(c,13,66,48,13,80,66,13,66);line(c,69,47,82,26,106,66,80,66);line(c,37,30,48,37,57,28);break;
        case "drop":Path drop=new Path();drop.moveTo(60,7);drop.cubicTo(54,24,34,37,40,52);drop.cubicTo(47,71,78,69,81,49);drop.cubicTo(83,35,66,21,60,7);c.drawPath(drop,p);arc(c,48,39,18,18,40,100);break;
        case "magnet":arc(c,35,13,50,49,0,180);line(c,35,37,35,12,48,12,48,37);arc(c,48,25,24,25,0,180);line(c,72,37,72,12,85,12,85,37);line(c,31,4,23,0);line(c,88,4,96,0);break;
        case "clock":circle(c,60,38,29);line(c,60,18,60,38,76,47);circle(c,60,38,2);break;
        case "branch":line(c,60,67,60,40,32,14,32,28);line(c,32,14,46,14);line(c,60,40,88,14,88,28);line(c,88,14,74,14);break;
        case "cycle":arc(c,31,9,58,58,205,155);arc(c,31,9,58,58,25,155);line(c,89,35,88,12,74,24);line(c,31,41,32,64,46,52);break;
        default:circle(c,60,38,22);
        }c.restore();
    }
}
