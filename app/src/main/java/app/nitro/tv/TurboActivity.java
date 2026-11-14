package app.nitro.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class TurboActivity extends Activity {
    private Ui.Bar bar;
    private TextView info, result;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.dp(this, 80), Ui.dp(this, 40), Ui.dp(this, 80), Ui.dp(this, 40));

        col.addView(Ui.text(this, "Turbo", 40, 0xFFFFFFFF, true, Gravity.START));
        TextView sub = Ui.text(this, "Libere memória e acesse os ajustes que mais pesam no Stick.", 15, 0xFF9FB0E0, false, Gravity.START);
        col.addView(sub);

        info = Ui.text(this, "", 18, 0xFFFFFFFF, true, Gravity.START);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = Ui.dp(this, 32);
        col.addView(info, ip);

        bar = new Ui.Bar(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 18));
        bp.topMargin = Ui.dp(this, 12);
        col.addView(bar, bp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 14));
        row.addView(Ui.tile(this, "Liberar memória", Ui.AMBER, null, false, 190, 108, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                free();
            }
        }));
        row.addView(Ui.tile(this, "Gerenciar apps", Ui.SURFACE2, null, false, 190, 108, go(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        row.addView(Ui.tile(this, "Opções do desenvolvedor", Ui.SURFACE2, null, false, 190, 108, go(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        row.addView(Ui.tile(this, "Armazenamento", Ui.SURFACE2, null, false, 190, 108, go(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        col.addView(row);

        result = Ui.text(this, "Dica: em Opções do desenvolvedor, deixe as 3 escalas de animação em 0,5x ou desligadas.", 14, 0xFF9FB0E0, false, Gravity.START);
        col.addView(result);
        setContentView(col);
        refresh();
        row.getChildAt(0).requestFocus();
    }

    private View.OnClickListener go(final String action) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(action));
                } catch (Throwable t) {
                    Toast.makeText(TurboActivity.this, "Não disponível neste aparelho", Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    private long avail() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private void refresh() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long total = mi.totalMem >> 20, free = mi.availMem >> 20;
        info.setText("Memória: " + (total - free) + " MB usados de " + total + " MB  (" + free + " MB livres)");
        bar.frac = total > 0 ? (float) (total - free) / total : 0f;
        bar.invalidate();
    }

    private void free() {
        final long before = avail();
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<Apps.App> l = Apps.scan(this);
            for (Apps.App a : l) {
                if (!a.pkg.equals(getPackageName())) am.killBackgroundProcesses(a.pkg);
            }
        } catch (Throwable ignored) {
        }
        result.setText("Limpando...");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                long gain = Math.max(0, (avail() - before) >> 20);
                result.setText("Pronto: cerca de " + gain + " MB liberados.");
                refresh();
            }
        }, 900);
    }
}
