package com.gennis.admin3force;

/** SÍMBOLOS de las notificaciones (v1.5.104; forma
 *  corregida en la v1.5.105, su orden): cada
 *  notificación lleva como ICONO PEQUEÑO — el que
 *  sale a la IZQUIERDA del título, chiquito y
 *  SOLO, sin contenedor — el glifo vectorial de
 *  su contexto, con los dibujos de Lucide que la
 *  app ya usa (res/drawable/ic_n_*): tarjeta para
 *  pagos, persona para cuentas, escudo para
 *  túneles, candado para permiso… Sin símbolo
 *  propio, el de la app. Nada de emojis. */
final class NotifIcons {

    private static int resFor(String kind) {
        if (kind == null) return 0;
        switch (kind) {
            case "pago":
            case "doble":
                return R.drawable.ic_n_card;
            case "invalido":
                return R.drawable.ic_n_card_slash;
            case "sinid":
                return R.drawable.ic_n_help;
            case "ilegible":
                return R.drawable.ic_n_msg;
            case "sinenviar":
                return R.drawable.ic_n_send;
            case "permiso":
                return R.drawable.ic_n_lock;
            case "cola":
                return R.drawable.ic_n_list;
            case "webmark":
                return R.drawable.ic_n_check;
            case "cuenta":
            case "sintunel":
                return R.drawable.ic_n_user;
            case "tunel":
                return R.drawable.ic_n_shield;
            case "prueba":
                return R.drawable.ic_n_clock;
            case "servidor":
                return R.drawable.ic_n_server;
            case "sistema":
                return R.drawable.ic_n_activity;
            case "estado":
                return R.drawable.ic_n_power;
            case "eliminado":
                return R.drawable.ic_n_trash;
            case "cobro":
                return R.drawable.ic_n_bell;
            default:
                return 0;
        }
    }

    /** Adjunta al Builder la VISTA PROPIA de la
     *  notificación (v1.5.109, su diseño
     *  confirmado en la demo): el símbolo va como
     *  imagen al principio del título — así sale
     *  de verdad también en Samsung, que no
     *  dibuja símbolos dentro del texto del
     *  título. El sistema pone su cabecera
     *  (DecoratedCustomViewStyle: icono de la
     *  app, nombre y hora) y arriba, en la barra,
     *  el icono pequeño sigue siendo el de la
     *  app. Sin vista propia aplicable, la
     *  notificación estándar vale igual. */
    static void attach(
            android.app.Notification.Builder b,
            android.content.Context ctx, String kind,
            String title, String body) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 24) {
                return;
            }
            // Como la v1.5.112 (estilo decorado,
            // cabecera del sistema) pero SIN HORA
            // (v1.5.114, su orden tras la doble
            // hora de la .113): setShowWhen(false)
            // apaga la hora en TODAS las vistas de
            // la notificación. Dónde va la hora lo
            // decide él al verla así.
            b.setStyle(new android.app.Notification
                    .DecoratedCustomViewStyle());
            b.setShowWhen(false);
            b.setCustomContentView(
                    content(ctx, kind, title, body, false));
            b.setCustomBigContentView(
                    content(ctx, kind, title, body, true));
        } catch (Exception ignored) {
            // sin vista propia: vale la estándar
        }
    }

    private static android.widget.RemoteViews content(
            android.content.Context ctx, String kind,
            String title, String body, boolean big) {
        android.widget.RemoteViews rv =
                new android.widget.RemoteViews(
                        ctx.getPackageName(),
                        big ? R.layout.notif_sym_big
                                : R.layout.notif_sym);
        int res = resFor(kind);
        if (res != 0) {
            rv.setImageViewResource(R.id.n_sym, res);
            rv.setViewVisibility(R.id.n_sym,
                    android.view.View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.n_sym,
                    android.view.View.GONE);
        }
        rv.setTextViewText(R.id.n_title,
                title != null ? title : "");
        rv.setTextViewText(R.id.n_body,
                body != null ? body : "");
        return rv;
    }
}
