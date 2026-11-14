package app.nitro.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lista de TV aberta (M3U). Troque M3U_URL pela sua lista preferida. */
final class Channels {
    private Channels() {}

    static final String M3U_URL = "https://iptv-org.github.io/iptv/countries/br.m3u";
    static final int MAX = 80;
    private static final Pattern LOGO = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    // ordem de prioridade na tela (até 3 de cada)
    private static final String[] PRIO = {"globo", "sbt", "record", "band", "redetv", "jovem pan", "cnn brasil",
            "tv brasil", "cultura", "aparecida", "canal rural", "gazeta", "rede vida", "senado", "camara",
            "justica", "novo tempo", "tv escola"};
    private static final long TTL = 12L * 60 * 60 * 1000;

    static final class Item {
        final String name;
        final String url;
        final String logo;
        int rank = 1000;
        Item(String n, String u, String l) { name = n; url = u; logo = l; }
    }

    static volatile List<Item> items = Collections.emptyList();
    static volatile boolean loading = false;

    static void load(final Context ctx, final boolean force, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                File f = new File(app.getFilesDir(), "ch.m3u");
                try {
                    if (items.isEmpty() && f.exists()) {
                        List<Item> p = parse(new String(readAll(new FileInputStream(f)), "UTF-8"));
                        if (!p.isEmpty()) items = p;
                        post(done);
                    }
                    boolean stale = force || !f.exists() || System.currentTimeMillis() - f.lastModified() > TTL;
                    if (stale) {
                        String t = http(M3U_URL);
                        List<Item> p = parse(t);
                        if (!p.isEmpty()) {
                            FileOutputStream o = new FileOutputStream(f);
                            o.write(t.getBytes("UTF-8"));
                            o.close();
                            items = p;
                        }
                    }
                } catch (Throwable ignored) {
                }
                loading = false;
                post(done);
            }
        }, "nitro-channels").start();
    }

    private static void post(final Runnable r) {
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }

    private static String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "Nitro/1.0");
        try {
            return new String(readAll(c.getInputStream()), "UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toByteArray();
    }

    static List<Item> parse(String text) {
        List<Item> all = new ArrayList<Item>();
        String name = null, logo = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                int i = line.lastIndexOf(',');
                name = i >= 0 ? line.substring(i + 1).trim() : "Canal";
                if (name.length() == 0) name = "Canal";
                Matcher m = LOGO.matcher(line);
                logo = m.find() ? m.group(1) : null;
            } else if (line.length() > 0 && !line.startsWith("#")) {
                if (name != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                    all.add(new Item(name, line, logo));
                }
                name = null;
                logo = null;
            }
        }
        int[] count = new int[PRIO.length];
        for (Item it : all) {
            String n = Normalizer.normalize(it.name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            for (int k = 0; k < PRIO.length; k++) {
                if (n.contains(PRIO[k])) {
                    if (count[k] < 3) {
                        count[k]++;
                        it.rank = k;
                    }
                    break;
                }
            }
        }
        Collections.sort(all, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.rank - b.rank;
            }
        });
        return all.size() > MAX ? new ArrayList<Item>(all.subList(0, MAX)) : all;
    }
}
