package com.gennis.admin3force;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * El despertador del respaldo automático (v1.0.0):
 * cada aviso rearma el siguiente y la corrida va en
 * un hilo con goAsync, sin abrir la app. Al arrancar
 * el teléfono (BOOT_COMPLETED) solo se rearma.
 */
public class BackupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        AutoBackup.schedule(ctx);
        if (intent == null
                || !AutoBackup.ACTION.equals(
                        intent.getAction())) {
            return;
        }
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                AutoBackup.run(app);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
