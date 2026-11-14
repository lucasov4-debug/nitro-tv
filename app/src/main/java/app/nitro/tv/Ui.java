package app.nitro.tv;

import android.content.Context;
import android.graphics.Outline;
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

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Fundo do tile: cor sólida + anel branco quando focado. */
    static Drawable tileBg(Context c, int fill, float radiusPx) {
        GradientDrawable on = new GradientDrawable();
        on.setColor(fill);
        on.setCornerRadius(radiusPx);
        on.setStroke(dp(c, 4), 0xFFFFFFFF);
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

    /** Foco leve: só escala + elevação (barato para GPUs fracas). */
    static final View.OnFocusChangeListener FOCUS = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            float s = has ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? 24f : 0f).setDuration(110).start();
        }
    };

    static TextView text(Context c, String s, float sp, int color, boolean bold, int gravity) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(gravity);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Tile base focável (para uso dentro de LinearLayout horizontal). */
    static FrameLayout base(Context c, int color, int wDp, int hDp, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp));
        lp.rightMargin = dp(c, 16);
        f.setLayoutParams(lp);
        float r = dp(c, 12);
        f.setBackground(tileBg(c, color, r));
        roundClip(f, r);
        f.setFocusable(true);
        if (click != null) f.setOnClickListener(click);
        f.setOnFocusChangeListener(FOCUS);
        return f;
    }

    /** Tile de app: banner cheio, ou ícone + rótulo, ou só rótulo. */
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
            ip.topMargin = dp(c, hDp * 0.14f);
            f.addView(iv, ip);
            TextView t = text(c, label, 12, 0xFFFFFFFF, true, Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(c, 6), 0, dp(c, 6), dp(c, 8));
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        } else {
            TextView t = text(c, label, 16, 0xFFFFFFFF, true, Gravity.CENTER);
            t.setMaxLines(3);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return f;
    }
}
