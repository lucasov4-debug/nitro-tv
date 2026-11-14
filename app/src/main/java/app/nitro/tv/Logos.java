package app.nitro.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Logos dos canais: cache em memória + disco, 2 threads, imagem reduzida (leve para o Stick). */
final class Logos {
    private Logos() {}

    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(48);
    private static final ExecutorService EX = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void load(Context c, final String url, final ImageView iv, final View hideOnLoad) {
        if (url == null || url.length() == 0) return;
        iv.setTag(url);
        Bitmap b = MEM.get(url);
        if (b != null) {
            show(iv, hideOnLoad, b);
            return;
        }
        final Context app = c.getApplicationContext();
        EX.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = fetch(app, url);
                if (bm == null) return;
                MEM.put(url, bm);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(iv.getTag())) show(iv, hideOnLoad, bm);
                    }
                });
            }
        });
    }

    private static void show(ImageView iv, View hide, Bitmap b) {
        iv.setImageBitmap(b);
        if (hide != null) hide.setVisibility(View.GONE);
    }

    private static Bitmap fetch(Context app, String url) {
        try {
            File dir = new File(app.getCacheDir(), "logos");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, Integer.toHexString(url.hashCode()));
            if (!f.exists() || f.length() == 0) {
                String u = url;
                HttpURLConnection c = null;
                for (int i = 0; i < 4; i++) {
                    c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(8000);
                    c.setInstanceFollowRedirects(false);
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = c.getHeaderField("Location");
                        c.disconnect();
                        if (loc == null) return null;
                        u = new URL(new URL(u), loc).toString();
                        continue;
                    }
                    break;
                }
                if (c == null || c.getResponseCode() != 200) return null;
                InputStream in = c.getInputStream();
                File tmp = new File(dir, f.getName() + ".tmp");
                FileOutputStream o = new FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 2000000) break;
                    o.write(buf, 0, n);
                }
                o.close();
                in.close();
                c.disconnect();
                if (!tmp.renameTo(f)) return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int max = Math.max(o.outWidth, o.outHeight), sample = 1;
            while (max / sample > 320) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b == null) f.delete();
            return b;
        } catch (Throwable t) {
            return null;
        }
    }
}
