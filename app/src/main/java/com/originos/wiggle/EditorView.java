package com.originos.wiggle;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class EditorView extends View {
    Bitmap bg, mask; Canvas mc; boolean erase;
    final Paint pb = new Paint(Paint.FILTER_BITMAP_FLAG), ov = new Paint(Paint.FILTER_BITMAP_FLAG), br = new Paint(Paint.ANTI_ALIAS_FLAG);
    float sc = 1, ox, oy, lx, ly;

    public EditorView(Context c) {
        super(c);
        ov.setAlpha(120);
        br.setStyle(Paint.Style.STROKE); br.setStrokeCap(Paint.Cap.ROUND); br.setStrokeJoin(Paint.Join.ROUND); br.setColor(0xFFFF6BD6);
    }

    void set(Bitmap b, Bitmap m) { bg = b; mask = m; mc = new Canvas(m); br.setStrokeWidth(b.getWidth() * .1f); invalidate(); }
    void setBg(Bitmap b) { bg = b; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        if (bg == null) return;
        sc = Math.min(getWidth() / (float) bg.getWidth(), getHeight() / (float) bg.getHeight());
        ox = (getWidth() - bg.getWidth() * sc) / 2; oy = (getHeight() - bg.getHeight() * sc) / 2;
        c.save(); c.translate(ox, oy); c.scale(sc, sc);
        c.drawBitmap(bg, 0, 0, pb); c.drawBitmap(mask, 0, 0, ov);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (mc == null) return false;
        float x = (e.getX() - ox) / sc, y = (e.getY() - oy) / sc;
        br.setXfermode(erase ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { lx = x; ly = y; mc.drawPoint(x, y, br); }
        else if (a == MotionEvent.ACTION_MOVE) { mc.drawLine(lx, ly, x, y, br); lx = x; ly = y; }
        invalidate();
        return true;
    }
}
