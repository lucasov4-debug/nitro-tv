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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TV aberta: junta listas M3U, testa quais canais respondem de verdade e mostra só os que funcionam.
 * Para adicionar sua própria lista, inclua a URL em SOURCES.
 */
final class Channels {
    private Channels() {}

    static final String[] SOURCES = {
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_brazil.m3u8",
            "https://iptv-org.github.io/iptv/countries/br.m3u",
    };
    static final int MAX = 80;
    private static final int CANDIDATES = 320;
    private static final long RAW_TTL = 12L * 60 * 60 * 1000;
    private static final long ALIVE_TTL = 6L * 60 * 60 * 1000;
    private static final Pattern LOGO = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    // ordem de prioridade na tela (até 3 de cada)
    private static final String[] PRIO = {"globo", "sbt", "record", "band", "redetv", "jovem pan", "cnn brasil",
            "tv brasil", "cultura", "aparecida", "canal rural", "gazeta", "rede vida", "senado", "camara",
            "justica", "novo tempo", "tv escola"};

    static final class Item {
        final String name;
        final String url;
        final String logo;
        String ref = "";
        String ua = "";
        int rank = 1000;

        Item(String n, String u, String l) {
            name = n;
            url = u;
            logo = l == null ? "" : l;
        }
    }

    static volatile List<Item> items = Collections.emptyList();
    static volatile boolean loading = false;

    static void load(final Context ctx, final boolean force, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                File raw = new File(app.getFilesDir(), "ch.m3u");
                File alive = new File(app.getFilesDir(), "alive.txt");
                try {
                    if (items.isEmpty()) {
                        List<Item> first = alive.exists() ? readAlive(alive) : new ArrayList<Item>();
                        if (first.isEmpty() && raw.exists()) {
                            first = parse(new String(readAll(new FileInputStream(raw)), "UTF-8"));
                            first = first.size() > MAX ? new ArrayList<Item>(first.subList(0, MAX)) : first;
                        }
                        if (!first.isEmpty()) {
                            items = first;
                            post(done);
                        }
                    }
                    boolean rawStale = force || !raw.exists() || System.currentTimeMillis() - raw.lastModified() > RAW_TTL;
                    if (rawStale) {
                        StringBuilder sb = new StringBuilder();
                        for (String src : SOURCES) {
                            try {
                                sb.append(http(src)).append('\n');
                            } catch (Throwable ignored) {
                            }
                        }
                        if (sb.length() > 0) {
                            FileOutputStream o = new FileOutputStream(raw);
                            o.write(sb.toString().getBytes("UTF-8"));
                            o.close();
                        }
                    }
                    boolean aliveStale = force || rawStale || !alive.exists()
                            || System.currentTimeMillis() - alive.lastModified() > ALIVE_TTL;
                    if (aliveStale && raw.exists()) {
                        List<Item> cand = parse(new String(readAll(new FileInputStream(raw)), "UTF-8"));
                        if (cand.size() > CANDIDATES) cand = new ArrayList<Item>(cand.subList(0, CANDIDATES));
                        List<Item> ok = check(cand);
                        if (!ok.isEmpty()) {
                            rank(ok, 3);
                            sortByRank(ok);
                            if (ok.size() > MAX) ok = new ArrayList<Item>(ok.subList(0, MAX));
                            writeAlive(alive, ok);
                            items = ok;
                        }
                    }
                } catch (Throwable ignored) {
                }
                loading = false;
                post(done);
            }
        }, "nitro-channels").start();
    }

    // ---------------------------------------------------------------- teste de sinal

    private static List<Item> check(final List<Item> cand) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> fs = new ArrayList<Future<Boolean>>();
        for (final Item it : cand) {
            fs.add(ex.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return isAlive(it);
                }
            }));
        }
        List<Item> ok = new ArrayList<Item>();
        for (int i = 0; i < cand.size(); i++) {
            try {
                if (fs.get(i).get(40, TimeUnit.SECONDS)) ok.add(cand.get(i));
            } catch (Throwable ignored) {
            }
            if (ok.size() >= MAX + 20) break;
        }
        ex.shutdownNow();
        return ok;
    }

    private static boolean isAlive(Item it) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(it.url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestProperty("User-Agent", it.ua.length() > 0 ? it.ua : "Mozilla/5.0 (Linux; Android 9) Nitro");
            if (it.ref.length() > 0) c.setRequestProperty("Referer", it.ref);
            if (c.getResponseCode() != 200) return false;
            InputStream in = c.getInputStream();
            byte[] b = new byte[512];
            int n = in.read(b);
            in.close();
            if (n <= 0) return false;
            boolean m3u = new String(b, 0, n, "UTF-8").trim().startsWith("#EXTM3U");
            return m3u || !it.url.contains(".m3u8");
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---------------------------------------------------------------- cache da lista que funciona

    private static void writeAlive(File f, List<Item> l) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Item it : l) {
            sb.append(clean(it.name)).append('\t').append(it.url).append('\t').append(it.logo).append('\t')
                    .append(it.ref).append('\t').append(it.ua).append('\n');
        }
        FileOutputStream o = new FileOutputStream(f);
        o.write(sb.toString().getBytes("UTF-8"));
        o.close();
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    private static List<Item> readAlive(File f) {
        List<Item> out = new ArrayList<Item>();
        try {
            String t = new String(readAll(new FileInputStream(f)), "UTF-8");
            for (String line : t.split("\n")) {
                String[] p = line.split("\t", -1);
                if (p.length < 5) continue;
                Item it = new Item(p[0], p[1], p[2]);
                it.ref = p[3];
                it.ua = p[4];
                out.add(it);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    // ---------------------------------------------------------------- util

    private static void post(final Runnable r) {
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }

    private static String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "Nitro/1.0");
        try {
            if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
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
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        String name = null, logo = null, ref = "", ua = "";
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                int i = line.lastIndexOf(',');
                name = i >= 0 ? line.substring(i + 1).trim() : "Canal";
                if (name.length() == 0) name = "Canal";
                Matcher m = LOGO.matcher(line);
                logo = m.find() ? m.group(1) : null;
                ref = "";
                ua = "";
            } else if (line.startsWith("#EXTVLCOPT:")) {
                String o = line.substring(11);
                int eq = o.indexOf('=');
                if (eq > 0) {
                    String k = o.substring(0, eq).toLowerCase(Locale.ROOT), v = o.substring(eq + 1).trim();
                    if (k.contains("referrer") || k.contains("referer")) ref = v;
                    else if (k.contains("user-agent")) ua = v;
                }
            } else if (line.length() > 0 && !line.startsWith("#")) {
                if (name != null && (line.startsWith("http://") || line.startsWith("https://")) && seen.add(line)) {
                    Item it = new Item(name, line, logo);
                    it.ref = ref;
                    it.ua = ua;
                    all.add(it);
                }
                name = null;
                logo = null;
                ref = "";
                ua = "";
            }
        }
        rank(all, 6);
        sortByRank(all);
        return all;
    }

    private static void rank(List<Item> l, int cap) {
        int[] count = new int[PRIO.length];
        for (Item it : l) {
            it.rank = 1000;
            String n = Normalizer.normalize(it.name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            for (int k = 0; k < PRIO.length; k++) {
                if (n.contains(PRIO[k])) {
                    if (count[k] < cap) {
                        count[k]++;
                        it.rank = k;
                    }
                    break;
                }
            }
        }
    }

    private static void sortByRank(List<Item> l) {
        Collections.sort(l, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.rank - b.rank;
            }
        });
    }
}
