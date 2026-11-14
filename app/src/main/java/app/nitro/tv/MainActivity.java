package app.nitro.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
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
    };

    // atalhos fixos: nome, cor, url (fallback), pacotes possíveis
    private static final String[][] SHORTCUTS = {
            {"Twitch", "#9146FF", "https://www.twitch.tv", "tv.twitch.android.app", "tv.twitch.android.viewer"},
            {"Kick", "#53FC18", "https://kick.com", "com.kick.mobile", "com.kick.tv"},
    };

    private static final int[] PALETTE = {
            0xFF3C5BD8, 0xFFB0398A, 0xFF1E9C84, 0xFFD2761F,
            0xFF6A4FE0, 0xFFD24A4A, 0xFF2D8FC0, 0xFF6E9A22
    };

    private static final class Sec {
        TextView head;
        HorizontalScrollView scroll;
        LinearLayout row;

        void show(boolean v) {
            int vis = v ? View.VISIBLE : View.GONE;
            if (head != null) head.setVisibility(vis);
            scroll.setVisibility(vis);
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScrollView sv;
    private GradientDrawable glowBg;
    private int glowColor = 0xFF3C5BD8;
    private ValueAnimator glowAnim;
    private TextView greeting, memPct;
    private Ui.Bar memBar;
    private Sec secHero, secStream, secTv, secApps, secOther, secSys;
    private List<Apps.App> apps;
    private boolean scanning;
    private boolean updateAvail;
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
        Updater.check(this, new Updater.Cb() {
            @Override
            public void done(boolean available) {
                updateAvail = available;
                if (available) fillTv();
            }
        });
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
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite");
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
        if (glowAnim != null) glowAnim.cancel();
        try {
            unregisterReceiver(pkgRx);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // launcher: Voltar não sai da home
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);

        // brilho ambiente: acompanha a cor do tile focado
        View glow = new View(this);
        glowBg = new GradientDrawable();
        glowBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowBg.setGradientRadius(Ui.dp(this, 820));
        glowBg.setGradientCenter(0.18f, 0.05f);
        glowBg.setColors(new int[]{glowTint(glowColor), 0x00000000});
        glow.setBackground(glowBg);
        root.addView(glow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.dp(this, 56), Ui.dp(this, 26), Ui.dp(this, 56), Ui.dp(this, 44));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // cabeçalho
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        SpannableStringBuilder logo = new SpannableStringBuilder("nitro.");
        logo.setSpan(new ForegroundColorSpan(Ui.AMBER), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView logoTv = Ui.text(this, "", 34, Ui.TEXT, false, Gravity.START);
        logoTv.setTypeface(Ui.black());
        logoTv.setText(logo);
        left.addView(logoTv);
        greeting = Ui.text(this, "", 16, Ui.MUTED, false, Gravity.START);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = Ui.dp(this, 6);
        left.addView(greeting, gp);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        time.setTextColor(Ui.TEXT);
        time.setTypeface(Ui.light());
        time.setGravity(Gravity.END);
        time.setIncludeFontPadding(false);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        date.setTextColor(Ui.MUTED);
        date.setGravity(Gravity.END);
        clockBox.addView(time);
        clockBox.addView(date);
        head.addView(clockBox);
        col.addView(head);

        secHero = sec(col, null);
        secStream = sec(col, "Streaming");
        secTv = sec(col, "TV aberta");
        secApps = sec(col, "Apps");
        secOther = sec(col, "Outros apps");
        secSys = sec(col, "Sistema");
        secStream.show(false);
        secOther.show(false);
        secApps.show(false);

        setContentView(root);

        sv.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (newFocus != null && newFocus.getTag() instanceof Integer) setGlow((Integer) newFocus.getTag());
            }
        });
    }

    private static int glowTint(int c) {
        return (c & 0x00FFFFFF) | 0x66000000;
    }

    private void setGlow(int to) {
        if (to == glowColor) return;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(380);
        glowAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                glowColor = (Integer) a.getAnimatedValue();
                glowBg.setColors(new int[]{glowTint(glowColor), 0x00000000});
            }
        });
        glowAnim.start();
    }

    private Sec sec(LinearLayout col, String title) {
        Sec s = new Sec();
        if (title != null) {
            s.head = Ui.text(this, title, 18, Ui.MUTED, true, Gravity.START);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = Ui.dp(this, 20);
            hp.leftMargin = Ui.dp(this, 6);
            col.addView(s.head, hp);
        }
        s.scroll = new HorizontalScrollView(this);
        s.scroll.setHorizontalScrollBarEnabled(false);
        s.scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.scroll.setClipChildren(false);
        s.scroll.setClipToPadding(false);
        s.row = new LinearLayout(this);
        s.row.setOrientation(LinearLayout.HORIZONTAL);
        s.row.setClipChildren(false);
        s.row.setClipToPadding(false);
        s.row.setPadding(Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16));
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
        for (String[] sc : SHORTCUTS) {
            Apps.App found = null;
            for (int k = 3; k < sc.length && found == null; k++) found = byPkg.get(sc[k]);
            final Apps.App fa = found;
            final String url = sc[2];
            if (fa != null) used.add(fa.pkg);
            View t = Ui.tile(this, sc[0], Color.parseColor(sc[1]), fa != null ? fa.img : null,
                    fa != null && fa.banner, 208, 117, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (fa != null) open(fa.launch);
                            else open(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                        }
                    });
            if (sc[0].equals("Kick") && t instanceof FrameLayout) {
                FrameLayout fl = (FrameLayout) t;
                for (int c = 0; c < fl.getChildCount(); c++) {
                    View ch = fl.getChildAt(c);
                    if (ch instanceof TextView) ((TextView) ch).setTextColor(0xFF0A0F24);
                }
            }
            secStream.row.addView(t);
        }
        for (Apps.App a : apps) {
            if (used.contains(a.pkg)) continue;
            LinearLayout row = a.leanback ? secApps.row : secOther.row;
            row.addView(Ui.tile(this, a.label, Ui.SURFACE, a.img, a.banner, 176, 99, launcher(a)));
        }
        secStream.show(true);
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

    private void play(int idx) {
        prefs.edit().putInt("last", idx).apply();
        startActivity(new Intent(this, PlayerActivity.class).putExtra("i", idx));
    }

    private void fillTv() {
        int fh = focusIdx(secHero.row), fi = focusIdx(secTv.row);
        secHero.row.removeAllViews();
        secTv.row.removeAllViews();
        List<Channels.Item> it = Channels.items;

        if (updateAvail) {
            View u = Ui.tile(this, "Atualização disponível\nToque para instalar", Ui.AMBER, null, false, 200, 150, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Updater.install(MainActivity.this);
                }
            });
            ((TextView) ((FrameLayout) u).getChildAt(0)).setTextColor(0xFF0A0F24);
            secHero.row.addView(u);
        }

        // destaque: continuar assistindo + memória
        if (!it.isEmpty()) {
            int last = prefs.getInt("last", 0);
            if (last < 0 || last >= it.size()) last = 0;
            secHero.row.addView(heroCard(it.get(last), last));
        }
        secHero.row.addView(memoryCard());
        updateRam();

        if (it.isEmpty()) {
            String msg = Channels.loading ? "Carregando canais..." : "Sem canais. Toque para tentar de novo";
            secTv.row.addView(Ui.tile(this, msg, Ui.SURFACE, null, false, 208, 117, new View.OnClickListener() {
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
            for (int i = 0; i < it.size(); i++) secTv.row.addView(channelTile(it.get(i), i));
        }
        restore(secHero.row, fh);
        restore(secTv.row, fi);
        if (getCurrentFocus() == null) focusFirst();
    }

    private static String cleanName(String n) {
        String s = n.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").trim();
        return s.length() == 0 ? n : s;
    }

    private static String initials(String n) {
        String[] p = n.split("\\s+");
        String s = p[0].substring(0, Math.min(2, p[0].length()));
        if (p.length > 1 && p[1].length() > 0) s = p[0].substring(0, 1) + p[1].substring(0, 1);
        return s.toUpperCase();
    }

    /** Painel claro com a logo (ou iniciais enquanto a imagem não chega). */
    private FrameLayout logoPanel(Channels.Item c, int color, float initialsSp) {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackgroundColor(Ui.PANEL);
        TextView ini = Ui.text(this, initials(cleanName(c.name)), initialsSp, color, false, Gravity.CENTER);
        ini.setTypeface(Ui.black());
        panel.addView(ini, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.dp(this, 14);
        iv.setPadding(pad, pad, pad, pad);
        panel.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Logos.load(this, c.logo, iv, ini);
        return panel;
    }

    private View heroCard(final Channels.Item c, final int idx) {
        int color = PALETTE[Math.abs(c.name.hashCode()) % PALETTE.length];
        FrameLayout f = Ui.base(this, Ui.SURFACE2, 520, 150, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(idx);
            }
        });
        f.setTag(Integer.valueOf(color));
        f.addView(logoPanel(c, color, 40), new FrameLayout.LayoutParams(Ui.dp(this, 190), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.CENTER_VERTICAL);
        txt.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        txt.addView(Ui.text(this, "Continuar assistindo", 15, Ui.MUTED, false, Gravity.START));
        TextView name = Ui.text(this, cleanName(c.name), 28, Ui.TEXT, true, Gravity.START);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Ui.dp(this, 6);
        txt.addView(name, np);
        TextView live = Ui.text(this, "● Ao vivo", 15, Ui.LIVE, true, Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        txt.addView(live, lp);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        tp.leftMargin = Ui.dp(this, 190);
        f.addView(txt, tp);
        return f;
    }

    private View memoryCard() {
        FrameLayout f = Ui.base(this, Ui.SURFACE, 280, 150, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TurboActivity.class));
            }
        });
        f.setTag(Integer.valueOf(Ui.AMBER));
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16));
        l.addView(Ui.text(this, "Memória do Stick", 15, Ui.MUTED, false, Gravity.START));
        memPct = Ui.text(this, "", 34, Ui.TEXT, false, Gravity.START);
        memPct.setTypeface(Ui.light());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.topMargin = Ui.dp(this, 6);
        l.addView(memPct, pp);
        memBar = new Ui.Bar(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8));
        bp.topMargin = Ui.dp(this, 12);
        l.addView(memBar, bp);
        TextView hint = Ui.text(this, "Abrir o Turbo", 13, Ui.AMBER, true, Gravity.START);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(this, 10);
        l.addView(hint, hp);
        f.addView(l, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return f;
    }

    private View channelTile(Channels.Item c, final int idx) {
        int color = PALETTE[Math.abs(c.name.hashCode()) % PALETTE.length];
        FrameLayout f = Ui.base(this, Ui.SURFACE, 200, 124, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(idx);
            }
        });
        f.setTag(Integer.valueOf(color));
        f.addView(logoPanel(c, color, 28), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 80), Gravity.TOP));

        SpannableStringBuilder sb = new SpannableStringBuilder("●  " + cleanName(c.name));
        sb.setSpan(new ForegroundColorSpan(Ui.LIVE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = Ui.text(this, "", 14, Ui.TEXT, true, Gravity.CENTER_VERTICAL);
        t.setText(sb);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44), Gravity.BOTTOM));
        return f;
    }

    private void fillSystem() {
        LinearLayout r = secSys.row;
        r.addView(Ui.tile(this, "Turbo", Ui.AMBER, null, false, 176, 99, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TurboActivity.class));
            }
        }));
        ((TextView) ((FrameLayout) r.getChildAt(0)).getChildAt(0)).setTextColor(0xFF0A0F24);
        r.addView(Ui.tile(this, "Configurações", Ui.SURFACE2, null, false, 176, 99, intentClick(Settings.ACTION_SETTINGS)));
        r.addView(Ui.tile(this, "Wi-Fi", Ui.SURFACE2, null, false, 176, 99, intentClick(Settings.ACTION_WIFI_SETTINGS)));
        r.addView(Ui.tile(this, "Buscar atualização", Ui.SURFACE2, null, false, 176, 99, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Verificando...", Toast.LENGTH_SHORT).show();
                Updater.check(MainActivity.this, new Updater.Cb() {
                    @Override
                    public void done(boolean available) {
                        updateAvail = available;
                        Toast.makeText(MainActivity.this, available ? "Atualização disponível" : "Você já está na versão mais recente", Toast.LENGTH_LONG).show();
                        fillTv();
                    }
                });
            }
        }));
        r.addView(Ui.tile(this, "Atualizar canais", Ui.SURFACE2, null, false, 176, 99, new View.OnClickListener() {
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
        Sec[] order = {secHero, secStream, secTv, secApps, secOther, secSys};
        for (Sec s : order) {
            if (s.scroll.getVisibility() == View.VISIBLE && s.row.getChildCount() > 0) {
                s.row.getChildAt(0).requestFocus();
                return;
            }
        }
    }

    private void updateRam() {
        if (memPct == null || memBar == null) return;
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            float used = mi.totalMem > 0 ? 1f - (float) mi.availMem / mi.totalMem : 0f;
            memPct.setText(Math.round(used * 100) + "% em uso");
            memBar.frac = used;
            memBar.invalidate();
        } catch (Throwable ignored) {
        }
    }
}
