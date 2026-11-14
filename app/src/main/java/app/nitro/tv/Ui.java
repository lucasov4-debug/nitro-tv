package app.nitro.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    private Ui() {}

    // Paleta "sala à noite": azul-tinta profundo, luz de abajur âmbar no foco.
    static final int BG = 0xFF0A0F24;
    static final int SURFACE = 0xFF151C3A;
    static final int SURFACE2 = 0xFF222B57;
    static final int TEXT = 0xFFF4F1FF;
    static final int MUTED = 0xFF8E98C4;
    static final int AMBER = 0xFFFFB43B;
    static final int LIVE = 0xFFFF4D5E;
    static final int PANEL = 0xFFEEF0FA;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static Typeface black() {
        return Typeface.create("sans-serif-black", Typeface.NORMAL);
    }

    static Typeface light() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static Drawable tileBg(Context c, int fill, float radiusPx) {
        GradientDrawable on = new GradientDrawable();
        on.setColor(fill);
        on.setCornerRadius(radiusPx);
        on.setStroke(dp(c, 4), AMBER);
        GradientDrawable off = new GradientDrawable();
        off.setColor(fill);
        off.setCornerRadius(radiusPx);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, off);
        return s;
    }

    static void roundClip(View v, final float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    static final View.OnFocusChangeListener FOCUS = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            float s = has ? 1.07f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? 24f : 0f).setDuration(120).start();
        }
    };

    static TextView text(Context c, String s, float sp, int color, boolean bold, int gravity) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(gravity);
        t.setTypeface(bold ? medium() : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Tile focável. A cor vai também em setTag: o fundo da home brilha nessa cor quando focado. */
    static FrameLayout base(Context c, int color, int wDp, int hDp, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp));
        lp.rightMargin = dp(c, 16);
        f.setLayoutParams(lp);
        float r = dp(c, 16);
        f.setBackground(tileBg(c, color, r));
        roundClip(f, r);
        f.setFocusable(true);
        f.setTag(Integer.valueOf(color));
        if (click != null) f.setOnClickListener(click);
        f.setOnFocusChangeListener(FOCUS);
        return f;
    }

    static FrameLayout tile(Context c, String label, int color, Drawable img, boolean banner,
                            int wDp, int hDp, View.OnClickListener click) {
        FrameLayout f = base(c, color, wDp, hDp, click);
        if (img != null && banner) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(img);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return f;
        }
        if (img != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(img);
            int s = dp(c, Math.min(wDp, hDp) * 0.42f);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(s, s, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            ip.topMargin = dp(c, hDp * 0.13f);
            f.addView(iv, ip);
            TextView t = text(c, label, 13, TEXT, true, Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(c, 8), 0, dp(c, 8), dp(c, 10));
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        } else {
            TextView t = text(c, label, 17, TEXT, true, Gravity.CENTER);
            t.setMaxLines(3);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return f;
    }

    /** Barra de memória: verde, âmbar ou vermelho conforme o uso. */
    static final class Bar extends View {
        float frac;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        Bar(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth(), rad = h / 2f;
            p.setColor(0xFF2A3566);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, p);
            p.setColor(frac < 0.6f ? 0xFF2ED47A : frac < 0.8f ? AMBER : LIVE);
            r.set(0, 0, Math.max(h, w * frac), h);
            c.drawRoundRect(r, rad, rad, p);
        }
    }
}
