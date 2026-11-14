package app.nitro.tv;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Atualização pelo release "latest" do GitHub: detecta versão nova e instala com 1 confirmação. */
final class Updater {
    private Updater() {}

    private static final String REPO = "lucasov4-debug/nitro-tv";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/tags/latest";
    private static final String APK = "https://github.com/" + REPO + "/releases/download/latest/nitro.apk";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Cb {
        void done(boolean available);
    }

    @SuppressWarnings("deprecation")
    static int localCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    static void check(Context ctx, final Cb cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean av = false;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "Nitro");
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    String json = new String(readAll(c.getInputStream()), "UTF-8");
                    c.disconnect();
                    Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(json);
                    if (m.find()) av = Integer.parseInt(m.group(1)) > localCode(app);
                } catch (Throwable ignored) {
                }
                final boolean r = av;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(r);
                    }
                });
            }
        }, "nitro-update-check").start();
    }

    static void install(final Activity a) {
        final Context app = a.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            toast(app, "Permita instalar apps pelo Nitro e volte aqui");
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Throwable t) {
                toast(app, "Ative Fontes desconhecidas para o Nitro em Configurações > Segurança");
            }
            return;
        }
        toast(app, "Baixando atualização...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(app.getCacheDir(), "update.apk");
                    HttpURLConnection c = (HttpURLConnection) new URL(APK).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "Nitro");
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(f);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                    fo.close();
                    in.close();
                    c.disconnect();

                    PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int id = pi.createSession(sp);
                    PackageInstaller.Session s = pi.openSession(id);
                    OutputStream out = s.openWrite("nitro.apk", 0, f.length());
                    FileInputStream fi = new FileInputStream(f);
                    while ((n = fi.read(buf)) > 0) out.write(buf, 0, n);
                    fi.close();
                    s.fsync(out);
                    out.close();
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pend = PendingIntent.getBroadcast(app, id, new Intent(app, InstallReceiver.class), flags);
                    s.commit(pend.getIntentSender());
                    s.close();
                } catch (Throwable t) {
                    toast(app, "Falha ao baixar a atualização");
                }
            }
        }, "nitro-update-install").start();
    }

    private static void toast(final Context c, final String m) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, m, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toByteArray();
    }
}
