package com.gennis.admin3force;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;

/** Notificaciones de ANDROID de los avisos de la
 *  campana (v1.5.30, su orden: «aplica ya las
 *  notificaciones de Android que teníamos planeadas
 *  y agrega botones de acciones como mismo salen en
 *  la app dentro»).
 *
 *  Cada aviso que nace en la campana (addNotice en la
 *  app, o el Vigía en segundo plano) se espeja aquí
 *  como notificación del sistema, con los MISMOS
 *  botones que su card trae dentro de la app. Los
 *  avisos de pago (pago/sinid/invalido) NO pasan por
 *  aquí desde la app: esos ya los anuncia SmsAlerts
 *  al llegar el SMS, y espejarlos duplicaría.
 *
 *  El toque y los botones abren MainActivity con un
 *  extra de ruta que el onResume consume y lleva al
 *  mismo sitio que el botón del panel. */
final class SysNotices {

    static final String CHANNEL_ID = "avisos_admin3force";

    private SysNotices() {
    }

    static void ensureChannel(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager)
                        ctx.getSystemService(
                                Context.NOTIFICATION_SERVICE);
                if (nm != null
                        && nm.getNotificationChannel(CHANNEL_ID)
                                == null) {
                    NotificationChannel ch =
                            new NotificationChannel(CHANNEL_ID,
                                    "Avisos",
                                    NotificationManager
                                            .IMPORTANCE_DEFAULT);
                    ch.setDescription("Los avisos de la "
                            + "campana, también fuera de la "
                            + "app: colas, servidores, cobros "
                            + "y cambios en la web");
                    nm.createNotificationChannel(ch);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Intent base(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    }

    private static PendingIntent pi(Context ctx, int notifId,
                                    int which, Intent i) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(ctx,
                notifId * 8 + which, i, flags);
    }

    /** A dónde lleva el TOQUE de la notificación: el
     *  mismo sitio que abrir el aviso en la app. */
    private static Intent tapIntent(Context ctx, String kind,
                                    String target, long uid) {
        Intent i = base(ctx);
        if (kind.equals("pago") || kind.equals("sinid")
                || kind.equals("invalido")
                || kind.equals("ilegible")
                || kind.equals("permiso")) {
            i.putExtra("open_pagos_tab", 0);
        } else if (kind.equals("sinenviar")) {
            if ("tuneles".equals(target)) {
                i.putExtra("open_tuneles", true);
            } else {
                i.putExtra("open_pagos_tab", 2);
            }
        } else if (kind.equals("cobro")) {
            i.putExtra("open_pagos_tab", 1);
        } else if (kind.equals("webmark")) {
            i.putExtra("open_pagos_tab", 2);
        } else if (kind.equals("prueba")
                || kind.equals("tunel")) {
            i.putExtra("open_tuneles", true);
        } else if (kind.equals("servidor")) {
            i.putExtra("open_servidores", true);
        } else if (kind.equals("cola")) {
            i.putExtra("open_cola", true);
        } else if (kind.equals("cuenta")) {
            if (uid >= 0) {
                i.putExtra("ver_cuenta_uid", uid);
            } else {
                i.putExtra("open_cuentas", true);
            }
        } else if (kind.equals("sintunel")) {
            i.putExtra("open_cuentas", true);
        } else {
            // estado, eliminado y otros: por el destino
            // que el propio aviso trae.
            if ("pagos".equals(target)) {
                i.putExtra("open_pagos", true);
            } else if ("tuneles".equals(target)) {
                i.putExtra("open_tuneles", true);
            } else if ("cuentas".equals(target)) {
                i.putExtra("open_cuentas", true);
            } else if ("servidores".equals(target)) {
                i.putExtra("open_servidores", true);
            }
        }
        return i;
    }

    /** Los botones de la card del aviso, tal cual
     *  salen en el panel: los mismos nombres y los
     *  mismos destinos. */
    private static void addActions(Notification.Builder b,
                                   Context ctx, int id,
                                   String kind, long uid,
                                   String uname, String target) {
        int n = 0;
        if ((kind.equals("cuenta") || kind.equals("sintunel"))
                && uid >= 0) {
            Intent c = base(ctx);
            c.putExtra("crear_tunel_uid", uid);
            if (uname != null) {
                c.putExtra("crear_tunel_uname", uname);
            }
            b.addAction(R.drawable.ic_stat_3force,
                    "Crear túnel ›", pi(ctx, id, ++n, c));
            Intent v = base(ctx);
            v.putExtra("ver_cuenta_uid", uid);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver cuenta ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("pago") || kind.equals("sinid")
                || kind.equals("invalido")
                || kind.equals("doble")
                || kind.equals("ilegible")) {
            Intent v = base(ctx);
            v.putExtra("open_pagos_tab", 0);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver pago ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("cobro")) {
            Intent v = base(ctx);
            v.putExtra("open_pagos_tab", 1);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver pendientes ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("webmark")) {
            Intent v = base(ctx);
            v.putExtra("open_pagos_tab", 2);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver pagados ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("prueba")
                || kind.equals("tunel")) {
            Intent v = base(ctx);
            v.putExtra("open_tuneles", true);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver túneles ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("servidor")) {
            Intent v = base(ctx);
            v.putExtra("open_servidores", true);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver servidores ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("cola")) {
            Intent v = base(ctx);
            v.putExtra("open_cola", true);
            b.addAction(R.drawable.ic_stat_3force,
                    "Ver tareas ›", pi(ctx, id, ++n, v));
        } else if (kind.equals("permiso")) {
            Intent v = new Intent(
                    android.provider.Settings
                            .ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:"
                            + ctx.getPackageName()));
            v.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.addAction(R.drawable.ic_stat_3force,
                    "Abrir ajustes ›",
                    pi(ctx, id, ++n, v));
        } else if (kind.equals("sinenviar")) {
            Intent v = base(ctx);
            boolean tuns = "tuneles".equals(target);
            if (tuns) {
                v.putExtra("open_tuneles", true);
            } else {
                v.putExtra("open_pagos_tab", 2);
            }
            b.addAction(R.drawable.ic_stat_3force,
                    tuns ? "Ver túneles ›" : "Ver pagados ›",
                    pi(ctx, id, ++n, v));
        }
        // estado / eliminado: en la app su card no
        // lleva botones; aquí tampoco.
    }

    /** Publica el espejo en Android de un aviso de la
     *  campana. Id determinista por tipo+título+texto:
     *  el mismo aviso repetido REEMPLAZA su
     *  notificación, no apila otra. */
    static void post(Context ctx, String kind, String title,
                     String sub, String target, long uid,
                     String uname) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            int id = 5000 + ((kind + "|" + title + "|"
                    + (sub != null ? sub : "")).hashCode()
                    & 0x3fff);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle(title)
                    .setContentText(sub != null ? sub : "")
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(sub != null ? sub : ""))
                    .setContentIntent(pi(ctx, id, 0,
                            tapIntent(ctx, kind, target, uid)))
                    .setAutoCancel(true);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
                // sin icono grande: la notificación vale igual
            }
            addActions(b, ctx, id, kind, uid, uname,
                    target);
            NotifIcons.attach(b, ctx, kind, title, sub);
            nm.notify(id, b.build());
        } catch (Exception ignored) {
            // un espejo perdido nunca rompe la app
        }
    }
}
