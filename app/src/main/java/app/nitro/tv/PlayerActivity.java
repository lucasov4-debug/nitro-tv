package app.nitro.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.List;

public class PlayerActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private VideoView video;
    private TextView osd, msg;
    private ProgressBar spin;
    private int idx, fails;

    private final Runnable hideOsd = new Runnable() {
        @Override
        public void run() {
            osd.setVisibility(View.GONE);
        }
    };
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            fail();
        }
    };
    private final Runnable skip = new Runnable() {
        @Override
        public void run() {
            play(idx + 1);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        idx = getIntent().getIntExtra("i", 0);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        spin = new ProgressBar(this);
        root.addView(spin, new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.CENTER));

        msg = Ui.text(this, "", 20, 0xFFFFFFFF, true, Gravity.CENTER);
        msg.setVisibility(View.GONE);
        root.addView(msg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        osd = Ui.text(this, "", 22, 0xFFFFFFFF, true, Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xE6151C3A);
        g.setCornerRadius(Ui.dp(this, 12));
        osd.setBackground(g);
        osd.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        op.leftMargin = Ui.dp(this, 48);
        op.bottomMargin = Ui.dp(this, 40);
        root.addView(osd, op);
        setContentView(root);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                h.removeCallbacks(watchdog);
                fails = 0;
                spin.setVisibility(View.GONE);
                msg.setVisibility(View.GONE);
                mp.start();
                mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer m, int what, int extra) {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) spin.setVisibility(View.VISIBLE);
                        else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)
                            spin.setVisibility(View.GONE);
                        return true;
                    }
                });
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                fail();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        play(idx);
    }

    @Override
    protected void onPause() {
        h.removeCallbacksAndMessages(null);
        try {
            video.stopPlayback();
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    private void fail() {
        h.removeCallbacks(watchdog);
        spin.setVisibility(View.GONE);
        fails++;
        if (fails < 6) {
            msg.setText("Sem sinal, tentando o próximo...");
            msg.setVisibility(View.VISIBLE);
            h.postDelayed(skip, 2000);
        } else {
            msg.setText("Canais indisponíveis agora.\nUse CIMA/BAIXO ou Voltar.");
            msg.setVisibility(View.VISIBLE);
        }
    }

    private void play(int i) {
        List<Channels.Item> l = Channels.items;
        if (l.isEmpty()) {
            finish();
            return;
        }
        idx = ((i % l.size()) + l.size()) % l.size();
        Channels.Item c = l.get(idx);
        h.removeCallbacks(skip);
        h.removeCallbacks(watchdog);
        SharedPreferences p = getSharedPreferences("nitro", MODE_PRIVATE);
        p.edit().putInt("last", idx).apply();
        msg.setVisibility(View.GONE);
        spin.setVisibility(View.VISIBLE);
        try {
            video.stopPlayback();
            video.setVideoURI(Uri.parse(c.url));
        } catch (Throwable t) {
            fail();
            return;
        }
        h.postDelayed(watchdog, 15000);
        osd.setText((idx + 1) + "  ·  " + c.name);
        osd.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideOsd);
        h.postDelayed(hideOsd, 3500);
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (e.getRepeatCount() == 0) {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_CHANNEL_UP) {
                fails = 0;
                play(idx + 1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                fails = 0;
                play(idx - 1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                osd.setVisibility(View.VISIBLE);
                h.removeCallbacks(hideOsd);
                h.postDelayed(hideOsd, 3500);
                return true;
            }
        }
        return super.onKeyDown(k, e);
    }
}
