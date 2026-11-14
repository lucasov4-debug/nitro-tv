package app.nitro.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Recebe o resultado da instalação e abre a confirmação do sistema. */
public class InstallReceiver extends BroadcastReceiver {
    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Throwable ignored) {
                }
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "Não foi possível instalar a atualização", Toast.LENGTH_LONG).show();
        }
    }
}
