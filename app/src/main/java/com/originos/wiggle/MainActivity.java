package com.originos.wiggle;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    EditorView ed; int W, H, theme, layout; boolean custom;
    Button bBrush, bTheme, bClock;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = dm.widthPixels; H = dm.heightPixels;
        SharedPreferences p = Cfg.p(this);
        theme = p.getInt("theme", 0); layout = p.getInt("layout", 0); custom = p.getBoolean("custom", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF070B14); root.setFitsSystemWindows(true);
        ed = new EditorView(this);
        root.addView(ed, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout r1 = row(root), r2 = row(root);
        btn(r1, "Photo", v -> pick());
        bBrush = btn(r1, "Brush", v -> { ed.erase = !ed.erase; bBrush.setText(ed.erase ? "Eraser" : "Brush"); });
        btn(r1, "Clear", v -> { ed.mask.eraseColor(0); ed.invalidate(); });
        bTheme = btn(r2, Cfg.NAMES[theme], v -> {
            theme = (theme + 1) % 4; bTheme.setText(Cfg.NAMES[theme]);
            if (!custom) ed.setBg(Cfg.bg(W, H, theme));
        });
        bClock = btn(r2, layout == 0 ? "Clock: row" : "Clock: stack", v -> { layout = 1 - layout; bClock.setText(layout == 0 ? "Clock: row" : "Clock: stack"); });
        btn(r2, "Set wallpaper", v -> apply());
        setContentView(root);

        File fb = new File(getFilesDir(), "bg.jpg"), fm = new File(getFilesDir(), "mask.png");
        Bitmap b = null, m = null;
        if (fb.exists() && fm.exists()) { b = BitmapFactory.decodeFile(fb.getPath()); m = BitmapFactory.decodeFile(fm.getPath()); }
        if (b == null || m == null) { b = Cfg.bg(W, H, theme); m = Cfg.mask(W, H); custom = false; }
        ed.set(b, m.copy(Bitmap.Config.ARGB_8888, true));
        Toast.makeText(this, "Paint over what should sit in front of the clock", Toast.LENGTH_LONG).show();
    }

    LinearLayout row(LinearLayout root) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(r, new LinearLayout.LayoutParams(-1, -2)); return r;
    }

    Button btn(LinearLayout r, String t, View.OnClickListener l) {
        Button b = new Button(this); b.setText(t); b.setAllCaps(false); b.setOnClickListener(l);
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1f)); return b;
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*");
        startActivityForResult(i, 1);
    }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        super.onActivityResult(rc, res, d);
        if (rc != 1 || res != RESULT_OK || d == null || d.getData() == null) return;
        try {
            Bitmap src = decode(d.getData());
            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            float s = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
            float dw = src.getWidth() * s, dh = src.getHeight() * s;
            new Canvas(out).drawBitmap(src, null, new RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
            custom = true;
            ed.set(out, Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888));
        } catch (Exception e) { Toast.makeText(this, "Could not open that photo", Toast.LENGTH_LONG).show(); }
    }

    Bitmap decode(Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int ss = 1; while (Math.max(o.outWidth, o.outHeight) / ss > 2400) ss *= 2;
        o = new BitmapFactory.Options(); o.inSampleSize = ss;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o); }
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(u)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
        } catch (Exception e) { }
        if (rot != 0) { Matrix m = new Matrix(); m.postRotate(rot); b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true); }
        return b;
    }

    void apply() {
        try {
            FileOutputStream o = new FileOutputStream(new File(getFilesDir(), "bg.jpg"));
            ed.bg.compress(Bitmap.CompressFormat.JPEG, 92, o); o.close();
            o = new FileOutputStream(new File(getFilesDir(), "mask.png"));
            ed.mask.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
        } catch (Exception e) { Toast.makeText(this, "Could not save", Toast.LENGTH_LONG).show(); return; }
        Cfg.p(this).edit().putInt("theme", theme).putInt("layout", layout).putBoolean("custom", custom).apply();
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, DepthWallpaperService.class));
        try { startActivity(i); } catch (Exception e) { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
    }
}
