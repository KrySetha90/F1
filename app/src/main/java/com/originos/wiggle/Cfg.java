package com.originos.wiggle;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;

final class Cfg {
    static final String[] NAMES = {"Liquid Ocean", "Aurora Opal", "Neon Jelly", "Cyber Coral"};
    static final int[][] COL = {
        {0xFF021B3A, 0xFF0A6FB5, 0xFF7FE7FF, 0xFFE8FBFF},
        {0xFF10122E, 0xFF3C7A8F, 0xFF9DFFD0, 0xFFF1E7FF},
        {0xFF1A0536, 0xFFA216D6, 0xFFFF5CE1, 0xFFFFF0FF},
        {0xFF1B0A12, 0xFFD9374F, 0xFFFF9E7A, 0xFFFFF1E8}};

    static SharedPreferences p(Context c) { return c.getSharedPreferences("cfg", 0); }

    static Bitmap bg(int w, int h, int th) {
        int[] c = COL[th];
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w * .4f, h, new int[]{c[0], c[1], c[2]}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        float cx = w / 2f, cy = h * .381f, r = w * .28f;
        p.setShader(new RadialGradient(cx - r * .3f, cy - r * .35f, r * 1.3f, new int[]{0xFFFFFFFF, c[2], c[1]}, new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, p);
        return b;
    }

    static Bitmap mask(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFF6BD6);
        new Canvas(b).drawCircle(w / 2f, h * .381f, w * .28f, p);
        return b;
    }
}
