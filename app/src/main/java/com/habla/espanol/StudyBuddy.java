package com.habla.espanol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** A tiny original paper-plane buddy drawn with Canvas, with no downloaded artwork. */
final class StudyBuddy extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    StudyBuddy(Context context) { super(context); setContentDescription("شخصية هابلا الكرتونية"); }
    private void fill(Canvas c, int color) { paint.setColor(color); paint.setStyle(Paint.Style.FILL); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float s = Math.min(getWidth(), getHeight()) / 100f;
        canvas.save(); canvas.translate((getWidth()-100*s)/2f, (getHeight()-100*s)/2f); canvas.scale(s,s);
        fill(canvas, 0xFFFFE6A8); canvas.drawCircle(50,51,45,paint);
        fill(canvas, 0xFF0E9470);
        Path plane=new Path(); plane.moveTo(13,45); plane.lineTo(89,20); plane.lineTo(68,83); plane.lineTo(49,62); plane.lineTo(34,74); plane.lineTo(37,55); plane.close(); canvas.drawPath(plane,paint);
        fill(canvas,0xFFB9F0D9); Path fold=new Path(); fold.moveTo(37,55); fold.lineTo(89,20); fold.lineTo(52,64); fold.close(); canvas.drawPath(fold,paint);
        fill(canvas,0xFFFFFFFF); canvas.drawCircle(53,49,5,paint); canvas.drawCircle(69,43,5,paint);
        fill(canvas,0xFF17334A); canvas.drawCircle(54,50,2.4f,paint); canvas.drawCircle(70,44,2.4f,paint);
        fill(canvas,0xFFFFA06A); canvas.drawCircle(46,58,4,paint); canvas.drawCircle(77,51,4,paint);
        paint.setColor(0xFF17334A); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.3f);
        canvas.drawArc(new RectF(55,47,68,62),15,95,false,paint); paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
}
