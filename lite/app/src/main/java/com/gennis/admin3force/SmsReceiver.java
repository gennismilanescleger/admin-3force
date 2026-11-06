package com.gennis.admin3force;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Capa 1 de la detección de pagos (v0.26.0): escucha SMS_RECEIVED
 * y, si el mensaje que ACABA de entrar es un pago nuevo, salta la
 * notificación al momento — con la app abierta o cerrada.
 *
 * Es solo el disparador: no guarda el pago ni sustituye la
 * lectura de la bandeja (capa 2). Si este receptor falla, el pago
 * sigue en la bandeja y la app lo levanta al abrirla o en el
 * barrido (capa 3). Rápido y silencioso para cualquier SMS que
 * no sea de PAGOxMOVIL/EnZona (o del modo de prueba).
 *
 * v1.5.21 (su orden: «que apenas llegue un SMS se active y
 * dispare»): el trabajo corre en un HILO con goAsync() — antes
 * corría en el hilo principal del proceso recién despertado y
 * el sistema podía cortar el proceso a mitad de camino, con lo
 * que el aviso (y la marca automática) llegaban tarde o solo
 * con el barrido. Tras avisar, el receptor pasa además el
 * barrido al momento (con su marca de agua, sin coste si no hay
 * nada nuevo) y deja SELLO de su última señal en las
 * preferencias, para poder comprobar que el disparo llegó.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null
                    || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION
                            .equals(intent.getAction())) {
                return;
            }
            final Context ctx = context.getApplicationContext();
            final Intent in = intent;
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    handle(ctx, in);
                } catch (Exception ignored) {
                    // el receptor jamás debe fallar en alto
                } finally {
                    try {
                        pr.finish();
                    } catch (Exception ignored) {
                        // ya cerrado: nada
                    }
                }
            }).start();
        } catch (Exception ignored) {
            // El receptor jamás debe fallar en alto: la bandeja manda.
        }
    }

    private static void handle(Context context, Intent intent) {
        // Sello del disparo: la última vez que el receptor
        // se activó (traiga o no un pago).
        try {
            context.getSharedPreferences("admin3force_sms",
                            Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_rx_ms",
                            System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
            // sin sello, la detección sigue
        }
        SmsMessage[] msgs = Telephony.Sms.Intents
                .getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;
        // Un SMS largo llega en partes: se agrupa por remitente.
        String address = msgs[0].getOriginatingAddress();
        StringBuilder body = new StringBuilder();
        long dateMs = 0;
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String a = m.getOriginatingAddress();
            if (address == null) address = a;
            if (a != null && a.equals(address)
                    && m.getMessageBody() != null) {
                body.append(m.getMessageBody());
            }
            if (m.getTimestampMillis() > dateMs) {
                dateMs = m.getTimestampMillis();
            }
        }
        if (address == null) return;
        boolean testMode = SmsPayments.testModeActive(
                context.getSharedPreferences(
                        "admin3force_sms",
                        Context.MODE_PRIVATE));
        if (!SmsPayments.isPaymentSender(address) && !testMode) {
            return; // no es de pago: ni se mira
        }
        if (dateMs == 0) dateMs = System.currentTimeMillis();
        SmsPayments.SmsPay p = SmsAlerts.detect(
                context, address, body.toString(), dateMs);
        if (SmsPayments.isPaymentSender(address)) {
            SmsAlerts.advanceWatermark(context, dateMs);
        }
        if (p != null) {
            SmsAlerts.notifyPayment(context, p);
            try {
                context.getSharedPreferences("admin3force_sms",
                                Context.MODE_PRIVATE)
                        .edit()
                        .putLong("last_pay_ms",
                                System.currentTimeMillis())
                        .apply();
            } catch (Exception ignored) {
                // sin sello, el aviso ya salió
            }
        }
        // Barrido al momento: si el mensaje venía en partes
        // o quedó otro pago justo antes sin avisar, sale
        // ahora y no dentro de 15 minutos.
        try {
            SmsAlerts.sweep(context);
        } catch (Exception ignored) {
            // el barrido nunca debe tumbar nada
        }
    }
}
