package com.originos.wiggle;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class DepthWallpaperService extends WallpaperService {
    static final int MW = 24, MH = 52;
    static final float R = 130f;   // jelly radius, in a 400-unit-wide space

    static class Wob { int id; float sx, sy, fx, fy, px, py, vx, vy, tx, ty, t0, A; boolean held; }

    @Override public Engine onCreateEngine() { return new E(); }

    class E extends Engine implements SensorEventListener {
        final Handler hd = new Handler(Looper.getMainLooper());
        final ArrayList<Wob> wob = new ArrayList<>();
        final Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[] org = new float[(MW + 1) * (MH + 1) * 2], vt = new float[(MW + 1) * (MH + 1) * 2];
        final long start = System.nanoTime();
        Bitmap bg, sub; int w, h, theme, layout, tapN; boolean vis;
        String stamp = ""; float T, lastMag = -1; long lastShake;
        Shader rowSh, stackSh; SensorManager sm;
        final Runnable tick = new Runnable() { public void run() { frame(); } };

        @Override public void onCreate(SurfaceHolder s) {
            super.onCreate(s);
            setTouchEventsEnabled(true);
            sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Typeface tf = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            fill.setTypeface(tf); line.setTypeface(tf);
            fill.setTextAlign(Paint.Align.CENTER); line.setTextAlign(Paint.Align.CENTER);
            line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1.1f); line.setColor(0xE0FFFFFF);
        }

        @Override public void onSurfaceChanged(SurfaceHolder s, int f, int nw, int nh) {
            super.onSurfaceChanged(s, f, nw, nh);
            w = nw; h = nh; stamp = "";
            for (int j = 0; j <= MH; j++) for (int i = 0; i <= MW; i++) {
                int n = (j * (MW + 1) + i) * 2;
                org[n] = w * (float) i / MW; org[n + 1] = h * (float) j / MH;
            }
            load(); frame();
        }

        void load() {
            if (w <= 0) return;
            android.content.SharedPreferences p = Cfg.p(DepthWallpaperService.this);
            theme = p.getInt("theme", 0); layout = p.getInt("layout", 0);
            File fb = new File(getFilesDir(), "bg.jpg"), fm = new File(getFilesDir(), "mask.png");
            String st = fb.lastModified() + ":" + fm.lastModified() + ":" + theme + ":" + layout + ":" + w + "x" + h;
            if (st.equals(stamp)) return;
            stamp = st;
            Bitmap b = null, m = null;
            if (fb.exists() && fm.exists()) { b = BitmapFactory.decodeFile(fb.getPath()); m = BitmapFactory.decodeFile(fm.getPath()); }
            if (b == null || m == null) { b = Cfg.bg(w, h, theme); m = Cfg.mask(w, h); }
            if (b.getWidth() != w || b.getHeight() != h) b = Bitmap.createScaledBitmap(b, w, h, true);
            if (m.getWidth() != w || m.getHeight() != h) m = Bitmap.createScaledBitmap(m, w, h, true);
            Bitmap s = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(s);
            c.drawBitmap(b, 0, 0, null);
            Paint x = new Paint();
            x.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            c.drawBitmap(m, 0, 0, x);
            bg = b; sub = s;
            int t3 = Cfg.COL[theme][3] & 0xFFFFFF;
            rowSh = new LinearGradient(0, -108, 0, 0, 0x6BFFFFFF, t3 | 0x1F000000, Shader.TileMode.CLAMP);
            stackSh = new LinearGradient(0, -125, 0, 0, 0xCCFFFFFF, t3 | 0x59000000, Shader.TileMode.CLAMP);
        }

        @Override public void onVisibilityChanged(boolean v) {
            vis = v;
            if (v) {
                load();
                if (sm != null) { Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); if (a != null) sm.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME); }
                frame();
            } else {
                if (sm != null) sm.unregisterListener(this);
                hd.removeCallbacks(tick);
            }
        }

        @Override public void onDestroy() {
            hd.removeCallbacks(tick);
            if (sm != null) sm.unregisterListener(this);
            super.onDestroy();
        }

        // shaking the phone makes one big wobble in the upper middle
        @Override public void onSensorChanged(SensorEvent e) {
            float m = Math.abs(e.values[0]) + Math.abs(e.values[1]) + Math.abs(e.values[2]);
            long now = SystemClock.uptimeMillis();
            if (lastMag >= 0 && Math.abs(m - lastMag) > 2.5f && now - lastShake > 500 && w > 0) {
                lastShake = now;
                add(-1, 200, h * (400f / w) * .4f, false, 14);
                frame();
            }
            lastMag = m;
        }
        @Override public void onAccuracyChanged(Sensor s, int a) { }

        void now() { T = (System.nanoTime() - start) / 1e9f; }

        Wob add(int id, float x, float y, boolean held, float A) {
            now();
            if (wob.size() >= 4) wob.remove(0);
            Wob q = new Wob(); q.id = id; q.sx = q.fx = x; q.sy = q.fy = y; q.held = held; q.t0 = T; q.A = A;
            wob.add(q); return q;
        }
        Wob find(int id) { for (Wob q : wob) if (q.id == id && q.held) return q; return null; }
        void release(Wob q) { q.held = false; q.tx = q.ty = 0; q.t0 = T; q.A = 5; }

        @Override public void onTouchEvent(MotionEvent e) {
            if (w <= 0) return;
            float k = 400f / w; now();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i = e.getActionIndex();
                    add(e.getPointerId(i), e.getX(i) * k, e.getY(i) * k, true, 9);
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        Wob q = find(e.getPointerId(i));
                        if (q != null) { q.fx = e.getX(i) * k; q.fy = e.getY(i) * k; }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    Wob q = find(e.getPointerId(e.getActionIndex()));
                    if (q != null) release(q);
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    for (Wob q : wob) if (q.held) release(q);
                    break;
                default: break;
            }
            frame();
        }

        // some launchers only forward taps, not full touches
        @Override public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean res) {
            if (WallpaperManager.COMMAND_TAP.equals(action) && w > 0) {
                float k = 400f / w;
                add(-1000 - (tapN++), x * k, y * k, false, 9);
                frame();
            }
            return null;
        }

        void frame() {
            hd.removeCallbacks(tick);
            if (!vis || bg == null) return;
            now(); step(); draw();
            hd.postDelayed(tick, wob.isEmpty() ? 1000 : 16);
        }

        void step() {
            for (int i = wob.size() - 1; i >= 0; i--) {
                Wob q = wob.get(i);
                if (q.held) {
                    q.sx += (q.fx - q.sx) * .25f; q.sy += (q.fy - q.sy) * .25f;
                    q.tx = Math.max(-30, Math.min(30, (q.fx - q.sx) * 1.4f));
                    q.ty = Math.max(-30, Math.min(30, (q.fy - q.sy) * 1.4f));
                }
                q.vx += (q.tx - q.px) * .16f; q.vy += (q.ty - q.py) * .16f;
                q.vx *= .86f; q.vy *= .86f; q.px += q.vx; q.py += q.vy;
                if (!q.held && T - q.t0 > 2.2f && Math.abs(q.px) + Math.abs(q.py) + Math.abs(q.vx) + Math.abs(q.vy) < .15f) wob.remove(i);
            }
        }

        void mesh() {
            float k = 400f / w;
            for (int j = 0; j <= MH; j++) for (int i = 0; i <= MW; i++) {
                int n = (j * (MW + 1) + i) * 2;
                float x = org[n], y = org[n + 1], lx = x * k, ly = y * k, dx = 0, dy = 0;
                for (Wob q : wob) {
                    float ox = lx - q.sx, oy = ly - q.sy, r = (float) Math.hypot(ox, oy);
                    if (r > R) continue;
                    float f = 1 - r / R, kk = f * f;
                    dx += q.px * kk; dy += q.py * kk;
                    if (r > .5f) {
                        float age = T - q.t0;
                        float rp = (float) (q.A * Math.exp(-age * 3) * Math.sin(r * .11f - age * 15)) * kk;
                        dx += rp * ox / r; dy += rp * oy / r;
                    }
                }
                vt[n] = x + dx / k; vt[n + 1] = y + dy / k;
            }
        }

        void draw() {
            SurfaceHolder sh = getSurfaceHolder(); Canvas c = null;
            try {
                c = sh.lockCanvas();
                if (c == null || bg == null) return;
                boolean a = !wob.isEmpty();
                if (a) { mesh(); c.drawBitmapMesh(bg, MW, MH, vt, 0, null, 0, pt); } else c.drawBitmap(bg, 0, 0, pt);
                drawClock(c);
                if (a) c.drawBitmapMesh(sub, MW, MH, vt, 0, null, 0, pt); else c.drawBitmap(sub, 0, 0, pt);
            } finally { if (c != null) sh.unlockCanvasAndPost(c); }
        }

        void drawClock(Canvas c) {
            Calendar cal = Calendar.getInstance();
            boolean f = DateFormat.is24HourFormat(DepthWallpaperService.this);
            int hr = cal.get(f ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
            if (!f && hr == 0) hr = 12;
            String hh = String.format(Locale.US, "%02d", hr), mm = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE));
            c.save(); c.scale(w / 400f, w / 400f);
            fill.setShadowLayer(18, 0, 0, 0x4D000000);
            if (layout == 0) {
                fill.setTextSize(150); line.setTextSize(150); fill.setShader(rowSh);
                c.translate(200, 505); c.scale(.88f, 3f);
                String s = hh + ":" + mm;
                c.drawText(s, 0, 0, fill); fill.clearShadowLayer(); c.drawText(s, 0, 0, line);
            } else {
                fill.setTextSize(170); line.setTextSize(170); fill.setShader(stackSh);
                String[] s = {hh, mm}; float[] ys = {232, 412};
                for (int i = 0; i < 2; i++) {
                    c.save(); c.translate(200, ys[i]); c.scale(.82f, 1.35f);
                    c.drawText(s[i], 0, 0, fill); c.drawText(s[i], 0, 0, line); c.restore();
                }
                fill.clearShadowLayer();
            }
            c.restore();
        }
    }
}
