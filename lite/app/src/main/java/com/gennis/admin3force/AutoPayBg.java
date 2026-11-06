package com.gennis.admin3force;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Pago automático EN SEGUNDO PLANO (v0.58.0, su orden con
 * su interruptor propio «También con la app cerrada» en
 * Ajustes › Cobro y pagos, apagado por defecto).
 *
 * Cuando llega un SMS de pago nuevo (el receptor y el
 * barrido ya lo detectaron por SmsAlerts), este motor
 * intenta marcarlo SOLO, con la app cerrada, aplicando
 * LAS MISMAS reglas y candados del motor de la app:
 *
 *  - Solo si «Pagos automáticos» Y «También con la app
 *    cerrada» están encendidos; nunca en modo de prueba.
 *  - Solo pagos VÁLIDOS (destino en sus tarjetas), con
 *    menos de 48 h, no asignados/descartados/en duda/en
 *    cola.
 *  - Identificación SOLO por ficha guardada (links): el
 *    cruce por nombre de contacto necesita la app
 *    abierta; si el número está en dos fichas distintas,
 *    se aparta igual que en la app.
 *  - La cuenta debe tener UN solo túnel; el túnel debe
 *    estar activo — o ser un TRIAL ya vencido (lo apagó
 *    el propio trial): entonces se marca, se REACTIVA y
 *    deja de ser trial. Un trial activo que paga también
 *    deja de ser trial al marcarse. Un túnel apagado por
 *    impago NO se toca: sigue mandando la regla de
 *    preguntar.
 *  - Control de importe idéntico: manda el mayor entre
 *    el piso fijo y el del precio × tasa (válida 48 h)
 *    menos la tolerancia; si da para 2 meses o más, no
 *    se marca.
 *  - Se marca UN mes: el pendiente más viejo visible.
 *  - Respuesta perdida = pago APARTADO en el registro
 *    de dudas, como en la app; el automático no lo
 *    vuelve a tocar jamás.
 *  - NUNCA abre el modal de WhatsApp: la confirmación
 *    queda en Pagados, en el botón de la propia card.
 *
 * Todo lo que escribe va a los mismos almacenes que usa
 * la app (sets de SMS, registro de importes, avisos,
 * Historial), así que al abrir la app todo cuadra: el
 * pago ya no está en Recibidos y Pagados enseña su
 * «Pagó» con la marca «Automático».
 */
public final class AutoPayBg {

    private static final String PREFS_SETTINGS =
            "admin3force_settings";
    private static final String PREFS_SMS = "admin3force_sms";
    private static final String PREFS_LINKS =
            "admin3force_links";
    private static final String PREFS_LOCAL =
            "admin3force_local";
    private static final String PREFS_NOTICES =
            "admin3force_notices";

    private static final Object LOCK = new Object();
    private static boolean running = false;

    private AutoPayBg() {
    }

    /** Punto de entrada: lo llama SmsAlerts al avisar un
     *  pago nuevo. Las compuertas baratas se miran sin
     *  hilo; el trabajo va en un hilo propio. */
    public static void maybeMark(final Context ctx,
            final SmsPayments.SmsPay p) {
        try {
            if (ctx == null || p == null) return;
            if (p.cls != 1) return;
            SharedPreferences stg = ctx.getSharedPreferences(
                    PREFS_SETTINGS, Context.MODE_PRIVATE);
            // La desconexión simulada de
            // Desarrollo (v1.5.38) también
            // manda aquí: sin marcas durante
            // la prueba de colas.
            ApiClient.SIMULATE_OFFLINE = stg.getBoolean(
                    "dev_web_off", false);
            // Unificado (v1.5.102): manda solo el
            // interruptor principal.
            if (!stg.getBoolean("auto_pay", false)) return;
            SharedPreferences sms = ctx.getSharedPreferences(
                    PREFS_SMS, Context.MODE_PRIVATE);
            // Modo de prueba (v1.5.110, su orden): ya
            // no frena al motor de fondo tampoco.
            // Canal (v1.5.2): el fondo mira TAMBIÉN la
            // compuerta de su canal — no se fía solo
            // del tubo que le pasa el pago. Cada
            // consumidor defiende su frontera.
            if (SmsPayments.CH_TM.equals(p.channel)
                    && !stg.getBoolean("sms_tm", true)) return;
            if (SmsPayments.CH_EZ.equals(p.channel)
                    && !stg.getBoolean("sms_ez", true)) return;
            final Context app = ctx.getApplicationContext();
            new Thread(() -> run(app, p)).start();
        } catch (Exception ignored) {
            // el fondo nunca tumba la detección
        }
    }

    private static void run(Context ctx,
            SmsPayments.SmsPay p) {
        synchronized (LOCK) {
            if (running) return;
            running = true;
        }
        try {
            runInner(ctx, p);
        } catch (Exception ignored) {
            // silencioso: la app lo resuelve al abrirse
        } finally {
            synchronized (LOCK) {
                running = false;
            }
        }
    }

    private static void runInner(Context ctx,
            SmsPayments.SmsPay p) throws Exception {
        SharedPreferences stg = ctx.getSharedPreferences(
                PREFS_SETTINGS, Context.MODE_PRIVATE);
        SharedPreferences sms = ctx.getSharedPreferences(
                PREFS_SMS, Context.MODE_PRIVATE);
        String key = keyOf(p);

        // Frescura (hueco 2): solo SMS de las últimas 48 h.
        if (p.dateMs <= 0 || System.currentTimeMillis()
                - p.dateMs > 48L * 3600000L) return;
        // Sets de control: asignado, descartado, en duda
        // o ya en cola = no se toca.
        if (loadSet(sms, "assigned_json").contains(key)) return;
        if (loadSet(sms, "discarded_json").contains(key)) return;
        if (doubtSet(stg).contains(key)) return;
        if (isQueued(sms, key)) return;

        if (!new SessionStore(ctx).hasSession()) return;
        if (!hasNetwork(ctx)) return;

        // Identificación por ficha (con su guardia de
        // cruce: un número en dos fichas no identifica).
        long uid = accountIdForPhone(ctx, p.senderPhone);
        if (uid < 0) return;

        ApiClient api = new ApiClient(new SessionStore(ctx))
                .useLoadTimeouts();
        ApiClient.ItemsResult rt = api.getAllItems(
                "Fondo — túneles", PanelConfig.TUNNELS_URL_ALL);
        if (rt.error != null || rt.authFailed
                || rt.items == null) return;
        JSONObject tunnel = null;
        int count = 0;
        for (int i = 0; i < rt.items.length(); i++) {
            JSONObject t = rt.items.optJSONObject(i);
            if (t == null) continue;
            if (ownerIdOf(t) == uid) {
                count++;
                tunnel = t;
            }
        }
        if (count != 1 || tunnel == null) return;
        long tid = tunnel.optLong("id", -1);
        if (tid < 0) return;
        // Sin cobro (v0.69.0): la cuenta es Personal
        // (la marca vive en la cuenta desde Editar
        // cuenta) — el automático en fondo tampoco marca
        // jamás sus túneles. La lista vieja por túnel se
        // respeta mientras la migración no haya corrido.
        try {
            SharedPreferences stgF = ctx.getSharedPreferences(
                    "admin3force_settings", Context.MODE_PRIVATE);
            JSONArray pa = new JSONArray(stgF.getString(
                    "personal_users_json", "[]"));
            for (int i = 0; i < pa.length(); i++) {
                if (pa.optLong(i) == uid) return;
            }
            JSONArray fa = new JSONArray(stgF.getString(
                    "free_tunnels_json", "[]"));
            for (int i = 0; i < fa.length(); i++) {
                if (fa.optLong(i) == tid) return;
            }
        } catch (Exception ignored) {
            // registro ilegible: se sigue el curso normal
        }

        // Estado y trial.
        JSONObject trec = trialRecord(ctx, tid);
        int tst = HomeStats.statusOf(tunnel);
        boolean revive = false;
        if (tst != 1) {
            if (trec != null && trec.optBoolean("done", false)) {
                revive = true;
            } else {
                return;
            }
        }

        // Importe: la misma banda de la app.
        double amt;
        try {
            amt = Double.parseDouble(
                    p.amount.replace(",", "."));
        } catch (Exception e) {
            return;
        }
        double price = stg.getFloat("price_usdt", 0f);
        long rate = lastRate(stg);
        double floorRate = (price > 0 && rate > 0)
                ? price * rate
                        * (1 - stg.getInt("auto_tol_pct", 3)
                                / 100.0)
                : 0;
        double floor = Math.max(
                stg.getInt("min_auto_cup", 0), floorRate);
        if (floor > 0 && amt < floor) return;
        // Meses del pago (v1.5.99): la misma regla
        // que la app — 2 o 3 meses justos solo si
        // «auto_multi» está encendido; lo que no
        // cuadra con meses enteros no se toca.
        int want = 1;
        if (price > 0 && rate > 0) {
            double fullM = price * rate;
            double tolBg = stg.getInt("auto_tol_pct", 3)
                    / 100.0;
            int det = 1;
            for (int n = 3; n >= 2; n--) {
                if (amt >= n * fullM * (1 - tolBg)
                        && amt <= n * fullM
                                * (1 + tolBg)) {
                    det = n;
                    break;
                }
            }
            if (det == 1
                    && amt >= 2 * fullM * (1 - tolBg)) {
                return;
            }
            if (det > 1) {
                if (!stg.getBoolean("auto_multi",
                        false)) {
                    return;
                }
                want = det;
            }
        }

        // El mes pendiente más viejo visible.
        ApiClient.ItemsResult rm = api.getAllItems(
                "Fondo — matriz", PanelConfig.MATRIX_URL);
        if (rm.error != null || rm.authFailed
                || rm.items == null) return;
        java.util.Calendar now =
                java.util.Calendar.getInstance();
        // Regla simple (v1.2.3): los meses cuentan
        // desde la creación del túnel, haya celda o no.
        java.util.Map<Long, Integer> ckMap =
                new java.util.HashMap<>();
        int ckSelf = HomeStats.creationKeyOf(
                tunnel.optString("created_at", ""));
        if (ckSelf > 0) ckMap.put(tid, ckSelf);
        List<HomeStats.PayEntry> pend =
                HomeStats.pendingEntries(rm.items,
                        now.get(java.util.Calendar.YEAR),
                        now.get(java.util.Calendar.MONTH) + 1,
                        ckMap);
        List<int[]> targets = new ArrayList<>();
        for (HomeStats.PayEntry e : pend) {
            if (targets.size() >= want) break;
            if (e.tunnelId != tid || e.paid) continue;
            targets.add(new int[]{e.month, e.year});
        }
        // Adelantados (v1.5.99): sin deuda
        // suficiente y con el interruptor
        // encendido, sigue con los meses que
        // vienen (saltando los ya pagados).
        if (targets.size() < want
                && stg.getBoolean("auto_advance",
                        false)) {
            java.util.Set<String> paidKeys =
                    new java.util.HashSet<>();
            for (HomeStats.PayEntry pe
                    : HomeStats.paidEntries(rm.items)) {
                if (pe.tunnelId == tid) {
                    paidKeys.add(pe.year + "-"
                            + pe.month);
                }
            }
            int fm = now.get(
                    java.util.Calendar.MONTH) + 1;
            int fy = now.get(java.util.Calendar.YEAR);
            int guard = 0;
            while (targets.size() < want
                    && guard++ < 14) {
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
                for (int[] tp : targets) {
                    if (tp[0] == fm && tp[1] == fy) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    targets.add(new int[]{fm, fy});
                }
            }
        }
        if (targets.size() < want) return;

        // Las marcas, una por mes, sin reintentos.
        List<int[]> applied = new ArrayList<>();
        for (int[] my : targets) {
            String body;
            try {
                body = new JSONObject()
                        .put("tunnel_id", tid)
                        .put("month", my[0])
                        .put("year", my[1])
                        .put("is_paid", true)
                        .put("notes", "")
                        .toString();
            } catch (Exception ex) {
                break;
            }
            ApiClient.Result r = api.postWrite(
                    "Escritura — pago automático (fondo)",
                    PanelConfig.UPDATE_URL, body);
            if (r.authFailed) {
                if (!applied.isEmpty()) {
                    recordAmount(ctx, key, amt, tid,
                            applied);
                }
                return;
            }
            if (r.ambiguous) {
                if (!applied.isEmpty()) {
                    recordAmount(ctx, key, amt, tid,
                            applied);
                }
                addDoubt(ctx, key);
                bell(ctx, "pago",
                        "Pago automático en duda: "
                                + tunnel.optString(
                                        "name", ""),
                        "La respuesta se perdió; quedó "
                                + "apartado para "
                                + "resolverlo a mano",
                        "pagos");
                HistoryLog.add(ctx, "pago",
                        "Pago automático en duda (fondo)",
                        tunnel.optString("name", ""), key);
                return;
            }
            if (r.error != null || r.code < 200
                    || r.code >= 300) {
                if (!applied.isEmpty()) {
                    recordAmount(ctx, key, amt, tid,
                            applied);
                }
                return;
            }
            applied.add(my);
            noteSelfMark(ctx, tid, my[0], my[1]);
        }
        if (applied.isEmpty()) return;
        // Aplicada(s): si se completaron todos los
        // meses, el SMS se asigna; si faltó alguno,
        // lo aplicado queda registrado y el SMS
        // sigue en Recibidos para resolverlo a mano.
        if (applied.size() == targets.size()) {
            addToSet(sms, "assigned_json", key);
        }
        recordAmount(ctx, key, amt, tid, applied);
        String tName = tunnel.optString("name", "");
        StringBuilder lbg = new StringBuilder();
        for (int li = 0; li < applied.size(); li++) {
            if (li > 0) lbg.append(" + ");
            lbg.append(HomeStats.monthLabel(
                    applied.get(li)[1],
                    applied.get(li)[0]));
        }
        String monthLab = lbg.toString();
                boolean wasTrial = trec != null;
        if (wasTrial) clearTrial(ctx, tid);
        HistoryLog.add(ctx, "pago",
                "Pago automático: " + tName,
                monthLab + " · con la app cerrada", null);
        bell(ctx, "pago", "Pago automático · " + tName,
                monthLab + " marcado solo, con la app "
                        + "cerrada · Confirmación en Pagados",
                "pagos");
        if (wasTrial && !revive) {
            HistoryLog.add(ctx, "pago",
                    "Prueba convertida: " + tName,
                    "Pagó y dejó de ser prueba", null);
            // Aviso de prueba convertida (v0.67.0, su
            // orden): también con la app cerrada.
            bell(ctx, "prueba",
                    "Prueba convertida: " + tName,
                    monthLab + " pagado · ya es plan normal",
                    "tuneles");
        }
        if (revive) {
            // Hecho en casa (v0.68.0): la detección de
            // estados no debe sonar por esta reactivación.
            noteSelfStatus(ctx, tid, 1);
            String eb = new JSONObject()
                    .put("status", "active").toString();
            ApiClient.Result er = api.write(
                    "Escritura — reactivar prueba (fondo)",
                    "PATCH",
                    PanelConfig.tunnelDetailUrl(tid), eb);
            if (er.error == null && er.code >= 200
                    && er.code < 300) {
                HistoryLog.add(ctx, "pago",
                        "Prueba reactivada: " + tName,
                        "Pagó su mes y volvió a estar activo",
                        null);
                bell(ctx, "pago",
                        "Prueba reactivada · " + tName,
                        "Pagó, se reactivó y dejó de ser "
                                + "prueba",
                        "tuneles");
            } else {
                bell(ctx, "pago",
                        "No se pudo reactivar " + tName,
                        "El mes quedó pagado; actívalo "
                                + "a mano",
                        "tuneles");
            }
        }
    }

    // ---------------- piezas compartidas ----------------

    private static String keyOf(SmsPayments.SmsPay p) {
        if (p.txnId != null && !p.txnId.isEmpty()) {
            return "t:" + p.txnId;
        }
        return "d:" + p.dateMs + ":"
                + (p.amount != null ? p.amount : "");
    }

    private static Set<String> loadSet(SharedPreferences sp,
            String key) {
        Set<String> out = new HashSet<>();
        try {
            String s = sp.getString(key, null);
            if (s != null) {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) {
                    out.add(a.optString(i));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void addToSet(SharedPreferences sp,
            String key, String value) {
        Set<String> set = loadSet(sp, key);
        if (!set.add(value)) return;
        JSONArray a = new JSONArray();
        for (String v : set) a.put(v);
        sp.edit().putString(key, a.toString()).apply();
    }

    private static Set<String> doubtSet(
            SharedPreferences stg) {
        return loadSet(stg, "auto_doubt_json");
    }

    private static void addDoubt(Context ctx, String key) {
        SharedPreferences stg = ctx.getSharedPreferences(
                PREFS_SETTINGS, Context.MODE_PRIVATE);
        try {
            JSONArray a;
            String s = stg.getString("auto_doubt_json", null);
            a = s != null ? new JSONArray(s) : new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                if (key.equals(a.optString(i))) return;
            }
            a.put(key);
            while (a.length() > 200) a.remove(0);
            stg.edit().putString("auto_doubt_json",
                    a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static boolean isQueued(SharedPreferences sms,
            String key) {
        try {
            String s = sms.getString("mark_queue_json", null);
            if (s == null) return false;
            JSONArray q = new JSONArray(s);
            for (int i = 0; i < q.length(); i++) {
                JSONObject e = q.optJSONObject(i);
                if (e != null && key.equals(
                        e.optString("sms_key", ""))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Identificación SOLO por fichas guardadas, con la
     *  guardia de cruce de la app: si el número está en
     *  dos cuentas distintas, no identifica (-1). */
    private static long accountIdForPhone(Context ctx,
            String phone) {
        if (phone == null) return -1;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) return -1;
        String last8 = digits.substring(digits.length() - 8);
        try {
            String s = ctx.getSharedPreferences(PREFS_LINKS,
                    Context.MODE_PRIVATE)
                    .getString("links_json", null);
            if (s == null) return -1;
            JSONObject links = new JSONObject(s);
            long found = -1;
            Iterator<String> it = links.keys();
            while (it.hasNext()) {
                String uidS = it.next();
                JSONObject o = links.optJSONObject(uidS);
                if (o == null) continue;
                boolean hit = last8.equals(last8Of(
                        o.optString("main", "")));
                if (!hit) {
                    JSONArray payers = o.optJSONArray("payers");
                    if (payers != null) {
                        for (int i = 0; i < payers.length();
                                i++) {
                            if (last8.equals(last8Of(
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

    private static String last8Of(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("\\D", "");
        return d.length() <= 8 ? d
                : d.substring(d.length() - 8);
    }

    private static long ownerIdOf(JSONObject t) {
        Object u = t.opt("user");
        if (u instanceof Number) {
            return ((Number) u).longValue();
        }
        if (u instanceof JSONObject) {
            Object i = ((JSONObject) u).opt("id");
            if (i instanceof Number) {
                return ((Number) i).longValue();
            }
        }
        if (u instanceof String) {
            try {
                return Long.parseLong(((String) u).trim());
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private static JSONObject trialRecord(Context ctx,
            long tid) {
        try {
            String s = ctx.getSharedPreferences(PREFS_LOCAL,
                    Context.MODE_PRIVATE)
                    .getString("trials_json", null);
            if (s == null) return null;
            return new JSONObject(s)
                    .optJSONObject(String.valueOf(tid));
        } catch (Exception e) {
            return null;
        }
    }

    // Registros «hecho en casa» (v0.68.0): los mismos que
    // usa la app abierta, para que las detecciones de la
    // web no suenen por lo que marcó este fondo.
    private static void noteSelfMark(Context ctx, long tid,
            int m, int y) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(
                    "admin3force_settings", Context.MODE_PRIVATE);
            JSONObject o;
            String s = sp.getString("self_marks_json", null);
            o = s != null ? new JSONObject(s) : new JSONObject();
            long now = System.currentTimeMillis();
            java.util.Iterator<String> it = o.keys();
            java.util.List<String> old =
                    new java.util.ArrayList<>();
            while (it.hasNext()) {
                String k = it.next();
                if (now - o.optLong(k, 0)
                        > 7L * 24L * 3600000L) {
                    old.add(k);
                }
            }
            for (String k : old) o.remove(k);
            o.put(tid + ":" + m + ":" + y, now);
            sp.edit().putString("self_marks_json",
                    o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void noteSelfStatus(Context ctx, long tid,
            int st) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(
                    "admin3force_settings", Context.MODE_PRIVATE);
            JSONObject o;
            String s = sp.getString("self_status_json", null);
            o = s != null ? new JSONObject(s) : new JSONObject();
            long now = System.currentTimeMillis();
            java.util.Iterator<String> it = o.keys();
            java.util.List<String> old =
                    new java.util.ArrayList<>();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray v = o.optJSONArray(k);
                if (v == null || now - v.optLong(1, 0)
                        > 24L * 3600000L) {
                    old.add(k);
                }
            }
            for (String k : old) o.remove(k);
            JSONArray v = new JSONArray();
            v.put(st);
            v.put(now);
            o.put("t" + tid, v);
            sp.edit().putString("self_status_json",
                    o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void clearTrial(Context ctx, long tid) {
        try {
            SharedPreferences lp = ctx.getSharedPreferences(
                    PREFS_LOCAL, Context.MODE_PRIVATE);
            String s = lp.getString("trials_json", null);
            if (s == null) return;
            JSONObject all = new JSONObject(s);
            if (all.has(String.valueOf(tid))) {
                all.remove(String.valueOf(tid));
                lp.edit().putString("trials_json",
                        all.toString()).apply();
            }
        } catch (Exception ignored) {
        }
    }

    private static long lastRate(SharedPreferences stg) {
        long v = stg.getLong("rate_cup", 0);
        long at = stg.getLong("rate_at", 0);
        if (v <= 0 || at <= 0) return 0;
        if (System.currentTimeMillis() - at
                > 48L * 3600000L) return 0;
        return v;
    }

    private static void recordAmount(Context ctx, String key,
            double amt, long tid, List<int[]> months) {
        try {
            SharedPreferences sms = ctx.getSharedPreferences(
                    PREFS_SMS, Context.MODE_PRIVATE);
            JSONObject all;
            String s = sms.getString(
                    "assigned_amounts_json", null);
            all = s != null ? new JSONObject(s)
                    : new JSONObject();
            JSONObject e = new JSONObject();
            e.put("a", amt);
            e.put("t", System.currentTimeMillis());
            e.put("tid", tid);
            JSONArray ms = new JSONArray();
            for (int[] my : months) {
                JSONArray pair = new JSONArray();
                pair.put(my[0]);
                pair.put(my[1]);
                ms.put(pair);
            }
            e.put("months", ms);
            e.put("auto", true);
            all.put(key, e);
            sms.edit().putString("assigned_amounts_json",
                    all.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void bell(Context ctx, String kind,
            String title, String sub, String target) {
        try {
            SharedPreferences stg = ctx.getSharedPreferences(
                    PREFS_SETTINGS, Context.MODE_PRIVATE);
            if (!stg.getBoolean("n_" + kind, true)) return;
            SharedPreferences np = ctx.getSharedPreferences(
                    PREFS_NOTICES, Context.MODE_PRIVATE);
            JSONArray a;
            try {
                String s = np.getString("items_json", null);
                a = s != null ? new JSONArray(s)
                        : new JSONArray();
            } catch (Exception e) {
                a = new JSONArray();
            }
            JSONObject o = new JSONObject();
            o.put("kind", kind);
            o.put("title", title);
            o.put("sub", sub != null ? sub : "");
            o.put("target", target);
            o.put("ts", System.currentTimeMillis());
            a.put(o);
            while (a.length() > 100) a.remove(0);
            np.edit().putString("items_json", a.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private static boolean hasNetwork(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(
                            Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}
