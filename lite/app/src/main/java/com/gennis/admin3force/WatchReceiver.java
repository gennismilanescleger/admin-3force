package com.gennis.admin3force;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * El despertador del Vigía de registros (v0.52.0): cada
 * aviso rearma el siguiente (~1 h) y la revisión corre en
 * un hilo con goAsync, sin abrir la app. Al arrancar el
 * teléfono (BOOT_COMPLETED) solo se rearma.
 */
public class WatchReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        AccountWatch.schedule(ctx);
        if (intent == null
                || !AccountWatch.ACTION.equals(
                        intent.getAction())) {
            return;
        }
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                AccountWatch.runCheck(app);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
