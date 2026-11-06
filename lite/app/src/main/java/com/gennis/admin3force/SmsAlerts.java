package com.gennis.admin3force;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

/**
 * Detección automática de pagos por SMS (v0.26.0) — el mecanismo
 * de 3 capas que Gennis aprobó:
 *
 *  Capa 1 (aviso al momento): SmsReceiver escucha SMS_RECEIVED y
 *  llama a detect() con el mensaje que acaba de entrar; si es un
 *  pago nuevo, salta la notificación.
 *  Capa 2 (la bandeja manda): la lectura de la bandeja al abrir
 *  la app sigue existiendo tal cual (MainActivity) — si el
 *  receptor falló, la lectura lo recupera.
 *  Capa 3 (barrido con marca de agua): una alarma inexacta cada
 *  ~15 min revisa si entró algo más nuevo que la marca guardada
 *  y avisa de lo que encuentre sin procesar.
 *
 * Todo lo que detecta pasa por el MISMO tubo que la vista:
 * parseo (SmsPayments), clasificación por tarjetas permitidas y
 * los registros de tratados/en cola — nada se avisa dos veces.
 * Estático y con Context: vive fuera de la actividad.
 */
public final class SmsAlerts {

    private SmsAlerts() {}

    private static final String PREFS_SMS = "admin3force_sms";
    private static final String PREFS_CARDS = "admin3force_cards";
    private static final String CHANNEL_ID = "pagos";
    private static final String SWEEP_ACTION =
            "com.gennis.admin3force.SMS_SWEEP";
    private static final long SWEEP_MS = 15 * 60 * 1000L;

    // ---------------- piezas compartidas con la actividad ----------------

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SMS, Context.MODE_PRIVATE);
    }

    /** La misma clave de pago que usa la actividad. */
    static String smsKeyOf(SmsPayments.SmsPay p) {
        if (p.txnId != null && !p.txnId.isEmpty()) return "t:" + p.txnId;
        return "d:" + p.dateMs + ":" + (p.amount != null ? p.amount : "");
    }

    private static Set<String> loadSet(Context ctx, String key) {
        Set<String> out = new HashSet<>();
        String s = prefs(ctx).getString(key, null);
        if (s == null) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (Exception ignored) {
            // registro ilegible: se trata como vacío
        }
        return out;
    }

    private static JSONArray loadCards(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS_CARDS, Context.MODE_PRIVATE)
                .getString("cards_json", null);
        if (s == null) return new JSONArray();
        try {
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** ¿Este pago ya salió de Recibidos (asignado/descartado) o
     *  tiene una marca en cola? Entonces no se avisa otra vez. */
    private static boolean yaTratado(Context ctx, SmsPayments.SmsPay p) {
        String k = smsKeyOf(p);
        if (loadSet(ctx, "assigned_json").contains(k)) return true;
        if (loadSet(ctx, "discarded_json").contains(k)) return true;
        try {
            JSONArray q = new JSONArray(
                    prefs(ctx).getString("mark_queue_json", "[]"));
            for (int i = 0; i < q.length(); i++) {
                if (k.equals(q.optJSONObject(i) != null
                        ? q.optJSONObject(i).optString("sms_key", "")
                        : "")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // cola ilegible: no bloquea el aviso
        }
        return false;
    }

    // ---------------- detección (el tubo único) ----------------

    /**
     * Pasa un SMS por el tubo completo. Devuelve el pago SOLO si
     * es un pago recibido nuevo que merece aviso (clasificado
     * válido o sin clasificar; los inválidos no avisan), o null.
     */
    public static SmsPayments.SmsPay detect(Context ctx, String address,
                                            String body, long dateMs) {
        try {
            boolean testMode = SmsPayments.testModeActive(prefs(ctx));
            SmsPayments.SmsPay p = SmsPayments.parse(
                    address, body, dateMs, testMode);
            if (p == null) {
                // Pago que no se pudo leer
                // (v1.5.95, revisión profunda):
                // parece transferencia y no
                // salió importe — dinero que
                // la app no vio; que avise.
                if (SmsPayments.looksUnreadable(
                        address, body)) {
                    notifyUnreadable(ctx, body, dateMs);
                }
                return null;
            }
            // Compuertas por canal (v0.39.0): los interruptores
            // de Ajustes › Detección de pagos por SMS viven en
            // las preferencias de ajustes de la app.
            android.content.SharedPreferences stg =
                    ctx.getSharedPreferences("admin3force_settings",
                            Context.MODE_PRIVATE);
            if (SmsPayments.CH_TM.equals(p.channel)
                    && !stg.getBoolean("sms_tm", true)) return null;
            if (SmsPayments.CH_EZ.equals(p.channel)
                    && !stg.getBoolean("sms_ez", true)) return null;
            SmsPayments.classify(p, loadCards(ctx));
            if (p.cls == 2) return null;
            if (yaTratado(ctx, p)) return null;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /** Aviso del SMS de pago que no se
     *  pudo leer (v1.5.95): una sola vez
     *  por mensaje (su clave queda
     *  guardada; la app la espeja en la
     *  campana al abrir). */
    private static void notifyUnreadable(Context ctx,
            String body, long dateMs) {
        try {
            android.content.SharedPreferences sp =
                    prefs(ctx);
            String key = dateMs + ":"
                    + (body != null ? body.hashCode() : 0);
            org.json.JSONArray seen;
            try {
                seen = new org.json.JSONArray(
                        sp.getString("ilegible_json", "[]"));
            } catch (Exception e) {
                seen = new org.json.JSONArray();
            }
            for (int i = 0; i < seen.length(); i++) {
                if (key.equals(seen.optString(i))) return;
            }
            seen.put(key);
            while (seen.length() > 100) seen.remove(0);
            sp.edit().putString("ilegible_json",
                    seen.toString()).apply();
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("open_pagos", true);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 6001, open, flags);
            String text = "Llegó un SMS de pago que la "
                    + "app no pudo leer · ábrelo en tu "
                    + "bandeja de mensajes";
            Notification.Builder b =
                    Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(
                                    ctx, CHANNEL_ID)
                            : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle("SMS de pago no leído")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
            }
            NotifIcons.attach(b, ctx, "ilegible",
                    "SMS de pago no leído", text);
            nm.notify(6000 + (key.hashCode() & 0x3fff),
                    b.build());
        } catch (Exception ignored) {
            // un aviso fallido no pierde el SMS:
            // sigue en la bandeja del teléfono
        }
    }

    /** Sin permiso de mensajes
     *  (v1.5.97): aviso en Android una
     *  vez al día mientras falte (la
     *  misma marca diaria que usa la
     *  app al abrir: un solo aviso
     *  entre los dos caminos). */
    private static void notifyNoPermission(Context ctx) {
        try {
            android.content.SharedPreferences stg =
                    ctx.getSharedPreferences(
                            "admin3force_settings",
                            Context.MODE_PRIVATE);
            if (!stg.getBoolean("n_permiso", true)) {
                return;
            }
            String today =
                    new java.text.SimpleDateFormat(
                            "yyyy-MM-dd",
                            java.util.Locale.US)
                            .format(new java.util.Date());
            if (today.equals(stg.getString(
                    "permiso_day", ""))) {
                return;
            }
            stg.edit().putString("permiso_day", today)
                    .apply();
            ensureChannel(ctx);
            NotificationManager nm =
                    (NotificationManager)
                            ctx.getSystemService(
                                    Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx,
                    MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("open_pagos", true);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 6003, open, flags);
            String text = "El teléfono no deja leer "
                    + "los SMS: la app no puede ver "
                    + "ningún pago · actívalo en los "
                    + "ajustes del teléfono";
            Notification.Builder b =
                    Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(
                                    ctx, CHANNEL_ID)
                            : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle(
                            "Falta el permiso de mensajes")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
            }
            NotifIcons.attach(b, ctx, "permiso",
                    "Falta el permiso de mensajes", text);
            nm.notify(6002, b.build());
        } catch (Exception ignored) {
        }
    }

    // ---------------- notificaciones ----------------

    public static void ensureChannel(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager)
                        ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID, "Pagos recibidos",
                            NotificationManager.IMPORTANCE_HIGH);
                    ch.setDescription(
                            "Aviso cuando llega un pago por SMS");
                    nm.createNotificationChannel(ch);
                }
            }
        } catch (Exception ignored) {
            // sin canal, la app sigue; el aviso simplemente no sale
        }
    }

    public static void notifyPayment(Context ctx, SmsPayments.SmsPay p) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            boolean ez = SmsPayments.CH_EZ.equals(p.channel);
            // Información COMPLETA (v1.5.92, su
            // orden: «si fue Pepe, que la
            // notificación diga su nombre»): el
            // título distingue el pago inválido,
            // y el texto nombra al cliente/túnel
            // cuando el remitente se identifica
            // por las FICHAS guardadas (la misma
            // identificación del fondo; la de
            // contactos llega después, en la
            // pasada de avisos de la app).
            boolean invalid = p.cls == 2;
            // Forma SUYA (v1.5.94): el título
            // dice el hecho con el nombre —
            // «Pepe te pagó» — y ABAJO va la
            // información: el monto, el canal
            // y el túnel si hace falta.
            String[] parts = invalid ? null
                    : identifyParts(ctx, p.senderPhone);
            String amt = p.amount != null
                    ? p.amount + " CUP" : "";
            String title;
            StringBuilder text = new StringBuilder();
            if (!amt.isEmpty()) text.append(amt);
            if (invalid) {
                title = "Pago inválido";
                if (!amt.isEmpty()) text.append(" · ");
                text.append("a otra tarjeta");
                if (p.destLast4 != null) {
                    text.append(" (••••").append(p.destLast4)
                            .append(")");
                }
            } else if (parts != null) {
                title = parts[0] + " te pagó";
                if (parts[1] != null
                        && !parts[1].equalsIgnoreCase(
                                parts[0])) {
                    text.append(" · túnel ")
                            .append(parts[1]);
                }
                // Mes objetivo (v1.5.103, su
                // orden): a qué mes(es) apunta
                // el pago según la caché —
                // «apunta a Oct», «apunta a
                // Oct + Nov», «adelantado:
                // Nov». Sin caché no se dice.
                String aim = aimTextBg(ctx,
                        p.senderPhone, p.amount);
                if (aim != null) {
                    text.append(" · ").append(aim);
                }
            } else {
                title = "Pago recibido";
                if (p.senderPhone != null) {
                    if (text.length() > 0) {
                        text.append(" · ");
                    }
                    text.append(p.senderPhone);
                }
            }
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("open_pagos", true);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 7, open, flags);
            PendingIntent piVer = PendingIntent.getActivity(
                    ctx, 7001, open, flags);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle(title)
                    .setContentText(text.toString())
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(text.toString()))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_stat_3force,
                            "Ver pago ›", piVer);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
            }
            int id = p.txnId != null
                    ? (p.txnId.hashCode() & 0x3fffffff) + 1000
                    : (int) (p.dateMs % 100000000L) + 1000;
            NotifIcons.attach(b, ctx,
                    invalid ? "invalido"
                            : (parts != null ? "pago"
                                    : "sinid"),
                    title, text.toString());
            nm.notify(id, b.build());
            HistoryLog.add(ctx, "sms", "Pago detectado",
                    text.toString(), null);
            // Pago automático en segundo plano (v0.58.0):
            // si sus dos interruptores están encendidos, el
            // motor de fondo intenta marcarlo ya, con la
            // app cerrada y las mismas reglas de siempre.
            // Este es el único punto por el que pasa TODO
            // pago nuevo detectado fuera de la app (el
            // receptor al momento y el barrido).
            AutoPayBg.maybeMark(ctx, p);
        } catch (Exception ignored) {
            // un aviso fallido no pierde el pago: la bandeja manda
        }
    }


    /** Últimos 8 dígitos de un teléfono (o "" ). */
    private static String last8(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("\\D", "");
        return d.length() >= 8 ? d.substring(d.length() - 8) : d;
    }

    /** Cuenta dueña de un número según las
     *  FICHAS guardadas (principal o pagador),
     *  con la guardia de cruce del fondo: el
     *  mismo número en DOS fichas no
     *  identifica (-1). */
    private static long uidForPhone(Context ctx, String phone) {
        if (phone == null) return -1;
        String l8 = last8(phone);
        if (l8.length() < 8) return -1;
        try {
            String s = ctx.getSharedPreferences(
                    "admin3force_links", Context.MODE_PRIVATE)
                    .getString("links_json", null);
            if (s == null) return -1;
            org.json.JSONObject links =
                    new org.json.JSONObject(s);
            long found = -1;
            java.util.Iterator<String> it = links.keys();
            while (it.hasNext()) {
                String uidS = it.next();
                org.json.JSONObject o =
                        links.optJSONObject(uidS);
                if (o == null) continue;
                boolean hit = l8.equals(last8(
                        o.optString("main", "")));
                if (!hit) {
                    org.json.JSONArray payers =
                            o.optJSONArray("payers");
                    if (payers != null) {
                        for (int i = 0; i < payers.length();
                                i++) {
                            if (l8.equals(last8(
                                    payers.optString(i)))) {
                                hit = true;
                                break;
                            }
                        }
                    }
                }
                if (hit) {
                    long uid;
                    try {
                        uid = Long.parseLong(uidS);
                    } catch (Exception e) {
                        continue;
                    }
                    if (found >= 0 && found != uid) return -1;
                    found = uid;
                }
            }
            return found;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Dueño de un túnel de la caché (campo
     *  user como número u objeto). */
    private static long ownerOf(org.json.JSONObject t) {
        Object u = t.opt("user");
        if (u instanceof Number) return ((Number) u).longValue();
        if (u instanceof org.json.JSONObject) {
            Object i = ((org.json.JSONObject) u).opt("id");
            if (i instanceof Number) {
                return ((Number) i).longValue();
            }
        }
        return -1;
    }

    /** Identificación por ficha para el
     *  título de la notificación (v1.5.94):
     *  [0] = la PERSONA (la cuenta; si no
     *  hay cuenta a mano, el túnel único),
     *  [1] = el túnel único o null. Null si
     *  el número no identifica o no hay
     *  caché. */
    private static String[] identifyParts(Context ctx,
            String phone) {
        try {
            long uid = uidForPhone(ctx, phone);
            if (uid < 0) return null;
            String uname = null;
            org.json.JSONArray users =
                    DataCache.load(ctx, "users");
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    org.json.JSONObject u =
                            users.optJSONObject(i);
                    if (u != null
                            && u.optLong("id", -1) == uid) {
                        uname = u.optString("username", "");
                        break;
                    }
                }
            }
            org.json.JSONArray tunnels =
                    DataCache.load(ctx, "tunnels");
            String tname = null;
            int count = 0;
            if (tunnels != null) {
                for (int i = 0; i < tunnels.length(); i++) {
                    org.json.JSONObject t =
                            tunnels.optJSONObject(i);
                    if (t != null && ownerOf(t) == uid) {
                        count++;
                        tname = t.optString("name", "");
                    }
                }
            }
            if (count != 1) tname = null;
            String persona = uname != null
                    && !uname.isEmpty() ? uname : tname;
            if (persona == null) return null;
            return new String[]{persona, tname};
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- marca de agua y barrido (capa 3) ----------------

    public static void advanceWatermark(Context ctx, long dateMs) {
        SharedPreferences sp = prefs(ctx);
        long wm = sp.getLong("watch_watermark_ms", 0);
        if (dateMs > wm) {
            sp.edit().putLong("watch_watermark_ms", dateMs).apply();
        }
    }

    /** MES OBJETIVO con la caché (v1.5.103):
     *  el segmento que dice a qué mes o meses
     *  apunta el pago identificado por ficha —
     *  la deuda en orden («apunta a Oct»,
     *  «apunta a Oct + Nov») o el adelantado
     *  («adelantado: Nov»). Null si no se puede
     *  saber (sin caché, túnel no único o
     *  deshabilitado): no se inventa. */
    private static String aimTextBg(Context ctx,
            String phone, String amountStr) {
        try {
            long uid = uidForPhone(ctx, phone);
            if (uid < 0) return null;
            org.json.JSONArray tunnels =
                    DataCache.load(ctx, "tunnels");
            if (tunnels == null) return null;
            org.json.JSONObject tun = null;
            int count = 0;
            for (int i = 0; i < tunnels.length(); i++) {
                org.json.JSONObject x =
                        tunnels.optJSONObject(i);
                if (x != null && ownerOf(x) == uid) {
                    count++;
                    tun = x;
                }
            }
            if (count != 1 || tun == null) return null;
            if (HomeStats.statusOf(tun) != 1) {
                return null;
            }
            long tid = tun.optLong("id", -1);
            if (tid < 0) return null;
            org.json.JSONArray matrix =
                    DataCache.load(ctx, "matrix");
            if (matrix == null) return null;
            java.util.Calendar now =
                    java.util.Calendar.getInstance();
            int cy = now.get(java.util.Calendar.YEAR);
            int cm = now.get(
                    java.util.Calendar.MONTH) + 1;
            java.util.Map<Long, Integer> ckm =
                    new java.util.HashMap<>();
            int ck = HomeStats.creationKeyOf(
                    tun.optString("created_at", ""));
            if (ck > 0) ckm.put(tid, ck);
            java.util.List<HomeStats.PayEntry> pend =
                    HomeStats.pendingEntries(matrix, cy,
                            cm, ckm);
            java.util.List<int[]> ms =
                    new java.util.ArrayList<>();
            for (HomeStats.PayEntry e : pend) {
                if (e.tunnelId == tid && !e.paid) {
                    ms.add(new int[]{e.month, e.year});
                }
            }
            java.util.Collections.sort(ms,
                    (a, b) -> a[1] != b[1]
                            ? a[1] - b[1] : a[0] - b[0]);
            // ¿Doble? (la misma banda de siempre,
            // con la tasa guardada si es fresca)
            int want = 1;
            try {
                android.content.SharedPreferences stg =
                        ctx.getSharedPreferences(
                                "admin3force_settings",
                                Context.MODE_PRIVATE);
                double price = stg.getFloat(
                        "price_usdt", 0f);
                long rate = 0;
                long rateAt = stg.getLong("rate_at", 0);
                if (rateAt > 0
                        && System.currentTimeMillis()
                                - rateAt
                                < 48L * 3600L * 1000L) {
                    rate = stg.getInt("rate_cup", 0);
                }
                double amt = Double.parseDouble(
                        amountStr.replace(",", "."));
                if (price > 0 && rate > 0) {
                    double full = price * rate;
                    double tol = stg.getInt(
                            "auto_tol_pct", 3) / 100.0;
                    if (amt >= 2 * full * (1 - tol)
                            && amt <= 2 * full
                                    * (1 + tol)) {
                        want = 2;
                    }
                }
            } catch (Exception ignored) {
            }
            java.util.Set<String> paidKeys =
                    new java.util.HashSet<>();
            for (HomeStats.PayEntry pe
                    : HomeStats.paidEntries(matrix)) {
                if (pe.tunnelId == tid) {
                    paidKeys.add(pe.year + "-"
                            + pe.month);
                }
            }
            boolean advance = ms.isEmpty();
            int fm = cm;
            int fy = cy;
            if (!ms.isEmpty()) {
                int[] last = ms.get(ms.size() - 1);
                if (last[1] > fy || (last[1] == fy
                        && last[0] > fm)) {
                    fm = last[0];
                    fy = last[1];
                }
            }
            int guard = 0;
            while (ms.size() < want && guard++ < 14) {
                fm++;
                if (fm > 12) {
                    fm = 1;
                    fy++;
                }
                if (paidKeys.contains(fy + "-"
                        + fm)) {
                    continue;
                }
                boolean taken = false;
                for (int[] tp : ms) {
                    if (tp[0] == fm && tp[1] == fy) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    ms.add(new int[]{fm, fy});
                }
            }
            if (ms.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            int n = Math.min(want, ms.size());
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(" + ");
                sb.append(shortMonth(ms.get(i)[0]));
            }
            return advance
                    ? "Adelantado: " + sb.toString()
                    : (want > 1
                            ? "Meses: " + sb.toString()
                            : "Mes: " + sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortMonth(int m) {
        String[] sm = {"Ene", "Feb", "Mar", "Abr",
                "May", "Jun", "Jul", "Ago", "Sep",
                "Oct", "Nov", "Dic"};
        return (m >= 1 && m <= 12) ? sm[m - 1] : "?";
    }

    /** Programa el barrido periódico (idempotente). */
    public static void scheduleSweep(Context ctx) {
        try {
            AlarmManager am = (AlarmManager)
                    ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, SmsSweepReceiver.class);
            i.setAction(SWEEP_ACTION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 11, i, flags);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + SWEEP_MS, SWEEP_MS, pi);
        } catch (Exception ignored) {
            // sin barrido, quedan las capas 1 y 2
        }
    }

    /**
     * Barrido: revisa la bandeja por encima de la marca de agua.
     * Lo nuevo que sea pago y no esté tratado → aviso (máx. 3
     * individuales; si hay más, un aviso resumen). La marca avanza
     * sobre TODO lo revisado, sea pago o no.
     */
    public static void sweep(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 23
                    && ctx.checkSelfPermission(
                            android.Manifest.permission.READ_SMS)
                            != PackageManager.PERMISSION_GRANTED) {
                // Que no sea silencioso
                // (v1.5.97, su #2): sin el
                // permiso no entra NINGÚN
                // pago y él no se entera.
                notifyNoPermission(ctx);
                return;
            }
            SharedPreferences sp = prefs(ctx);
            long wm = sp.getLong("watch_watermark_ms", 0);
            if (wm == 0) {
                // Primera vez: la marca nace ahora — no se avisa
                // el pasado; la vista ya lo muestra al abrir.
                sp.edit().putLong("watch_watermark_ms",
                        System.currentTimeMillis()).apply();
                return;
            }
            Cursor c = null;
            int alerts = 0;
            long maxDate = wm;
            try {
                c = ctx.getContentResolver().query(
                        Uri.parse("content://sms/inbox"),
                        new String[]{"address", "body", "date"},
                        "date > ?", new String[]{String.valueOf(wm)},
                        "date ASC");
                if (c == null) return;
                int ia = c.getColumnIndex("address");
                int ib = c.getColumnIndex("body");
                int id = c.getColumnIndex("date");
                int seen = 0;
                while (c.moveToNext() && seen < 200) {
                    seen++;
                    String addr = ia >= 0 ? c.getString(ia) : null;
                    String body = ib >= 0 ? c.getString(ib) : null;
                    long date = id >= 0 ? c.getLong(id) : 0;
                    if (date > maxDate) maxDate = date;
                    if (!SmsPayments.isPaymentSender(addr)
                            && !SmsPayments.testModeActive(sp)) {
                        continue;
                    }
                    SmsPayments.SmsPay p = detect(ctx, addr, body, date);
                    if (p != null) {
                        alerts++;
                        if (alerts <= 3) notifyPayment(ctx, p);
                    }
                }
            } finally {
                if (c != null) c.close();
            }
            if (alerts > 3) {
                notifySummary(ctx, alerts);
            }
            if (maxDate > wm) {
                sp.edit().putLong("watch_watermark_ms", maxDate).apply();
            }
        } catch (Exception ignored) {
            // el barrido nunca debe tumbar nada
        }
    }

    private static void notifySummary(Context ctx, int count) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("open_pagos", true);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 8, open, flags);
            PendingIntent piVer = PendingIntent.getActivity(
                    ctx, 8002, open, flags);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle("Pagos recibidos")
                    .setContentText("Llegaron " + count
                            + " pagos por SMS. Toca para verlos en Pagos.")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_stat_3force,
                            "Ver pago ›", piVer);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
            }
            NotifIcons.attach(b, ctx, "pago",
                    "Pagos recibidos",
                    "Llegaron " + count
                            + " pagos por SMS. Toca para "
                            + "verlos en Pagos.");
            nm.notify(999, b.build());
            HistoryLog.add(ctx, "sms",
                    "Barrido: " + count + " pagos nuevos detectados");
        } catch (Exception ignored) {
            // sin aviso resumen; los pagos siguen en la bandeja
        }
    }
}
