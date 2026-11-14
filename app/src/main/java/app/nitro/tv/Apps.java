package app.nitro.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Apps {
    private Apps() {}

    static final class App {
        String pkg;
        String label;
        Intent launch;
        Drawable img;
        boolean banner;
        boolean leanback;
    }

    /** Roda em thread de fundo: lista apps lançáveis (leanback primeiro) já com rótulo e imagem. */
    static List<App> scan(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> map = new LinkedHashMap<String, App>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> list;
            try {
                list = pm.queryIntentActivities(q, 0);
            } catch (Throwable t) {
                continue;
            }
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || map.containsKey(pkg)) continue;
                App a = new App();
                a.pkg = pkg;
                a.leanback = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(cat);
                CharSequence l = ri.loadLabel(pm);
                a.label = TextUtils.isEmpty(l) ? pkg : l.toString();
                a.launch = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Drawable b = null;
                try {
                    b = ri.activityInfo.loadBanner(pm);
                } catch (Throwable ignored) {
                }
                if (b != null) {
                    a.banner = true;
                    a.img = b;
                } else {
                    try {
                        a.img = ri.loadIcon(pm);
                    } catch (Throwable ignored) {
                    }
                }
                map.put(pkg, a);
            }
        }
        List<App> out = new ArrayList<App>(map.values());
        Collections.sort(out, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                return x.label.toLowerCase(Locale.getDefault()).compareTo(y.label.toLowerCase(Locale.getDefault()));
            }
        });
        return out;
    }
}
