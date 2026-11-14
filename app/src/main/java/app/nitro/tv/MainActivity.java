package app.nitro.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    // pacote, nome, cor
    private static final String[][] BRANDS = {
            {"com.netflix.ninja", "Netflix", "#E50914"},
            {"com.google.android.youtube.tv", "YouTube", "#FF0033"},
            {"com.google.android.youtube.tvunplugged", "YouTube TV", "#FF0033"},
            {"com.amazon.amazonvideo.livingroom", "Prime Video", "#00A8E1"},
            {"com.disney.disneyplus", "Disney+", "#113CCF"},
            {"com.globo.globotv", "Globoplay", "#E8344E"},
            {"com.wbd.stream", "Max", "#5822B4"},
            {"com.hbo.hbonow", "HBO", "#5822B4"},
            {"com.apple.atve.androidtv.appletv", "Apple TV", "#2A2A2E"},
            {"com.cbs.ott", "Paramount+", "#0064FF"},
            {"com.spotify.tv.android", "Spotify", "#1DB954"},
            {"tv.pluto.android", "Pluto TV", "#2B2B52"},
            {"com.crunchyroll.crunchyroid", "Crunchyroll", "#F47521"},
            {"com.plexapp.android", "Plex", "#E5A00D"},
            {"tv.twitch.android.app", "Twitch", "#9146FF"},
    };

    private static final int[] PALETTE = {
            0xFF1F3C88, 0xFF7A1F5C, 0xFF14705F, 0xFF8A4B14,
            0xFF3B2A8C, 0xFF8C2A2A, 0xFF1F6A8C, 0xFF4B6B14
    };

    private static final class Sec {
        TextView head;
        HorizontalScrollView scroll;
        LinearLayout row;

        void show(boolean v) {
            int vis = v ? View.VISIBLE : View.GONE;
            head.setVisibility(vis);
            scroll.setVisibility(vis);
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScrollView sv;
    private TextView ram;
    private Sec secStream, secTv, secApps, secOther, secSys;
    private List<Apps.App> apps;
    private boolean scanning;
    private SharedPreferences prefs;

    private final Runnable rescanRun = new Runnable() {
        @Override
        public void run() {
            rescan();
        }
    };

    private final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ui.removeCallbacks(rescanRun);
            ui.postDelayed(rescanRun, 800);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("nitro", MODE_PRIVATE);
        buildUi();
        fillSystem();
        fillTv();
        rescan();
        Channels.load(this, false, new Runnable() {
            @Override
            public void run() {
                fillTv();
            }
        });
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        try {
            registerReceiver(pkgRx, f);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRam();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        sv.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(pkgRx);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // launcher: Back não sai da home
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.dp(this, 56), Ui.dp(this, 28), Ui.dp(this, 56), Ui.dp(this, 40));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // cabeçalho
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        SpannableStringBuilder logo = new SpannableStringBuilder("nitro.");
        logo.setSpan(new ForegroundColorSpan(0xFF4C8DFF), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView logoTv = Ui.text(this, "", 30, 0xFFFFFFFF, true, Gravity.START);
        logoTv.setText(logo);
        logoTv.setLetterSpacing(0.04f);
        head.addView(logoTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ram = Ui.text(this, "", 14, 0xFF9FB0E0, false, Gravity.END);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.rightMargin = Ui.dp(this, 28);
        head.addView(ram, rp);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        time.setTextColor(0xFFFFFFFF);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setGravity(Gravity.END);
        time.setIncludeFontPadding(false);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEE, d 'de' MMM");
        date.setFormat12Hour("EEE, d 'de' MMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        date.setTextColor(0xFF9FB0E0);
        date.setGravity(Gravity.END);
        clockBox.addView(time);
        clockBox.addView(date);
        head.addView(clockBox);
        col.addView(head);

        secStream = sec(col, "Streaming");
        secTv = sec(col, "TV aberta");
        secApps = sec(col, "Apps");
        secOther = sec(col, "Outros apps");
        secSys = sec(col, "Sistema");
        secStream.show(false);
        secOther.show(false);
        secApps.show(false);

        setContentView(sv);
    }

    private Sec sec(LinearLayout col, String title) {
        Sec s = new Sec();
        s.head = Ui.text(this, title.toUpperCase(), 14, 0xFF9FB0E0, true, Gravity.START);
        s.head.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(this, 22);
        col.addView(s.head, hp);

        s.scroll = new HorizontalScrollView(this);
        s.scroll.setHorizontalScrollBarEnabled(false);
        s.scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.scroll.setClipChildren(false);
        s.scroll.setClipToPadding(false);
        s.row = new LinearLayout(this);
        s.row.setOrientation(LinearLayout.HORIZONTAL);
        s.row.setClipChildren(false);
        s.row.setClipToPadding(false);
        s.row.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14));
        s.scroll.addView(s.row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(s.scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    // ---------------------------------------------------------------- dados

    private void rescan() {
        if (scanning) return;
        scanning = true;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Apps.App> l = Apps.scan(app);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        apps = l;
                        fillApps();
                    }
                });
            }
        }, "nitro-scan").start();
    }

    private void fillApps() {
        int f1 = focusIdx(secStream.row), f2 = focusIdx(secApps.row), f3 = focusIdx(secOther.row);
        secStream.row.removeAllViews();
        secApps.row.removeAllViews();
        secOther.row.removeAllViews();
        if (apps == null) return;

        Map<String, Apps.App> byPkg = new HashMap<String, Apps.App>();
        for (Apps.App a : apps) byPkg.put(a.pkg, a);
        Set<String> used = new HashSet<String>();

        for (String[] br : BRANDS) {
            final Apps.App a = byPkg.get(br[0]);
            if (a == null) continue;
            used.add(a.pkg);
            secStream.row.addView(Ui.tile(this, br[1], Color.parseColor(br[2]), a.img, a.banner, 208, 117, launcher(a)));
        }
        for (Apps.App a : apps) {
            if (used.contains(a.pkg)) continue;
            LinearLayout row = a.leanback ? secApps.row : secOther.row;
            row.addView(Ui.tile(this, a.label, 0xFF1B2447, a.img, a.banner, 176, 99, launcher(a)));
        }
        secStream.show(secStream.row.getChildCount() > 0);
        secApps.show(secApps.row.getChildCount() > 0);
        secOther.show(secOther.row.getChildCount() > 0);
        restore(secStream.row, f1);
        restore(secApps.row, f2);
        restore(secOther.row, f3);
        if (getCurrentFocus() == null) focusFirst();
    }

    private View.OnClickListener launcher(final Apps.App a) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(a.launch);
            }
        };
    }

    private void fillTv() {
        int fi = focusIdx(secTv.row);
        secTv.row.removeAllViews();
        List<Channels.Item> it = Channels.items;
        if (it.isEmpty()) {
            String msg = Channels.loading ? "Carregando canais..." : "Sem canais. Toque para tentar de novo";
            secTv.row.addView(Ui.tile(this, msg, 0xFF1B2447, null, false, 208, 117, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Channels.load(MainActivity.this, true, new Runnable() {
                        @Override
                        public void run() {
                            fillTv();
                        }
                    });
                    fillTv();
                }
            }));
        } else {
            int last = prefs.getInt("last", -1);
            if (last >= 0 && last < it.size()) secTv.row.addView(channelTile(it.get(last), last, true));
            for (int i = 0; i < it.size(); i++) secTv.row.addView(channelTile(it.get(i), i, false));
        }
        restore(secTv.row, fi);
        if (getCurrentFocus() == null) focusFirst();
    }

    private View channelTile(Channels.Item c, final int idx, boolean cont) {
        String name = c.name.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").trim();
        if (name.length() == 0) name = c.name;
        int color = PALETTE[Math.abs(c.name.hashCode()) % PALETTE.length];
        FrameLayout f = Ui.base(this, color, 208, 117, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putInt("last", idx).apply();
                startActivity(new Intent(MainActivity.this, PlayerActivity.class).putExtra("i", idx));
            }
        });
        TextView tag = Ui.text(this, cont ? "CONTINUAR" : "● AO VIVO", 10, cont ? 0xFFFFE9A8 : 0xFFFFC9C9, true, Gravity.START);
        tag.setLetterSpacing(0.1f);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.leftMargin = Ui.dp(this, 12);
        tp.topMargin = Ui.dp(this, 10);
        f.addView(tag, tp);
        TextView t = Ui.text(this, name, 17, 0xFFFFFFFF, true, Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 6));
        f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return f;
    }

    private void fillSystem() {
        LinearLayout r = secSys.row;
        r.addView(Ui.tile(this, "Turbo", 0xFFFF6A00, null, false, 176, 99, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TurboActivity.class));
            }
        }));
        r.addView(Ui.tile(this, "Configurações", 0xFF2A3566, null, false, 176, 99, intentClick(Settings.ACTION_SETTINGS)));
        r.addView(Ui.tile(this, "Wi-Fi", 0xFF2A3566, null, false, 176, 99, intentClick(Settings.ACTION_WIFI_SETTINGS)));
        r.addView(Ui.tile(this, "Atualizar canais", 0xFF2A3566, null, false, 176, 99, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Channels.load(MainActivity.this, true, new Runnable() {
                    @Override
                    public void run() {
                        fillTv();
                    }
                });
                Toast.makeText(MainActivity.this, "Atualizando canais...", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private View.OnClickListener intentClick(final String action) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent(action));
            }
        };
    }

    // ---------------------------------------------------------------- util

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Não foi possível abrir", Toast.LENGTH_SHORT).show();
        }
    }

    private int focusIdx(LinearLayout row) {
        View cf = getCurrentFocus();
        return (cf != null && cf.getParent() == row) ? row.indexOfChild(cf) : -1;
    }

    private void restore(LinearLayout row, int idx) {
        if (idx >= 0 && row.getChildCount() > 0) {
            row.getChildAt(Math.min(idx, row.getChildCount() - 1)).requestFocus();
        }
    }

    private void focusFirst() {
        Sec[] order = {secStream, secTv, secApps, secOther, secSys};
        for (Sec s : order) {
            if (s.scroll.getVisibility() == View.VISIBLE && s.row.getChildCount() > 0) {
                s.row.getChildAt(0).requestFocus();
                return;
            }
        }
    }

    private void updateRam() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram.setText("RAM livre  " + (mi.availMem >> 20) + " MB");
        } catch (Throwable ignored) {
        }
    }
}
