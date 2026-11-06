package com.gennis.admin3force;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Capa 3 de la detección de pagos (v0.26.0): la alarma periódica
 * (~15 min) despierta este receptor y hace el barrido de la
 * bandeja por encima de la marca de agua (ver SmsAlerts.sweep).
 * Es la red final: aunque el receptor de SMS no haya corrido y
 * la app lleve días sin abrirse, el pago se detecta y se avisa.
 */
public class SmsSweepReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            SmsAlerts.sweep(context);
        } catch (Exception ignored) {
            // el barrido nunca debe tumbar nada
        }
    }
}
