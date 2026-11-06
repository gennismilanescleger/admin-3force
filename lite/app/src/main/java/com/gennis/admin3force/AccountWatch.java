package com.gennis.admin3force;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Vigía de registros (v0.52.0, plan aprobado por Gennis):
 * con la app cerrada, un despertador de 15 minutos más o
 * menos baja la lista de cuentas con la sesión guardada y
 * avisa si apareció una cuenta que no se creó desde la
 * app — con la HORA REAL del registro (created_at del
 * panel), no la hora de la revisión.
 *
 * Reglas (las del detector de la app abierta, heredadas):
 *  - La siembra del registro de cuentas vistas la hace
 *    SOLO la app abierta con datos frescos; sin siembra, el
 *    vigía no avisa de nada (espera).
 *  - Una cuenta solo suena si nació DESPUÉS de que la app
 *    empezó a vigilar (margen de 5 min); las viejas que
 *    aparezcan tarde entran en silencio.
 *  - Las cuentas creadas desde la app (registro local de
 *    creaciones) no suenan: son suyas.
 *  - Sin internet en el teléfono: no se toca nada.
 *  - Si la sesión guardada caducó (el refresh ya no
 *    renueva), tras 3 despertares seguidos sin poder
 *    entrar deja UN único aviso en la campana y sigue
 *    intentándolo en silencio.
 *  - Con la app abierta recientemente no gasta: la carga
 *    de la app ya hizo esta misma revisión.
 *
 * El aviso respeta el interruptor «Nueva cuenta» de
 * Ajustes › Notificaciones y avisos (es el mismo tipo de
 * aviso), y el vigía tiene su propio interruptor allí
 * mismo («Vigía de registros», llave watch_on).
 */
public class AccountWatch {

    public static final String ACTION =
            "com.gennis.admin3force.WATCH_ACCOUNTS";
    // Cada 15 minutos (v1.5.35, su orden —
    // antes era 1 hora): lo más rápido que
    // Android deja trabajar con la app
    // cerrada sin que el sistema lo vuelva
    // inestable.
    private static final long WATCH_MS = 900000L;
    private static final int REQ = 42;
    private static final String CHANNEL_ID = "cuentas";
    private static final String PREFS_SETTINGS =
            "admin3force_settings";
    private static final String PREFS_NOTICES =
            "admin3force_notices";
    private static final String PREFS_CREATED =
            "admin3force_created";

    private AccountWatch() {
    }

    private static SharedPreferences settings(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SETTINGS,
                Context.MODE_PRIVATE);
    }

    // ---------------- despertador ----------------

    private static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, WatchReceiver.class);
        i.setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, REQ, i, flags);
    }

    /** (Re)arma el siguiente despertar, ~15 min desde ahora. */
    public static void schedule(Context ctx) {
        try {
            if (!settings(ctx).getBoolean("watch_on", true)) {
                return;
            }
            AlarmManager am = (AlarmManager) ctx
                    .getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCH_MS,
                    pending(ctx));
        } catch (Exception ignored) {
            // sin despertador, el detector de la app abierta
            // sigue cubriendo al abrir
        }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx
                    .getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(ctx));
        } catch (Exception ignored) {
        }
    }

    // ---------------- la revisión ----------------

    private static void setStatus(Context ctx, String result) {
        settings(ctx).edit()
                .putLong("watch_last_ms",
                        System.currentTimeMillis())
                .putString("watch_last_result", result)
                .apply();
    }

    /** Una revisión completa. Se llama desde un hilo de
     *  fondo (el receptor). Nunca lanza. */
    public static void runCheck(Context ctx) {
        try {
            SharedPreferences sp = settings(ctx);
            if (!sp.getBoolean("watch_on", true)) return;
            if (!new SessionStore(ctx).hasSession()) {
                setStatus(ctx, "Sin sesión guardada");
                return;
            }
            long fg = sp.getLong("watch_fg_ms", 0);
            if (fg > 0 && System.currentTimeMillis() - fg
                    < 55L * 60000L) {
                setStatus(ctx, "La app se abrió hace poco");
                return;
            }
            if (!hasNetwork(ctx)) {
                setStatus(ctx, "Sin conexión en el teléfono");
                return;
            }
            // La desconexión simulada de
            // Desarrollo (v1.5.38) también
            // manda aquí: el Vigía no toca la
            // web durante la prueba.
            ApiClient.SIMULATE_OFFLINE = sp.getBoolean(
                    "dev_web_off", false);
            ApiClient.ItemsResult ru = new ApiClient(
                    new SessionStore(ctx)).useLoadTimeouts()
                    .getAllItems("Vigía — usuarios",
                            PanelConfig.USERS_URL);
            if (ru.authFailed) {
                int fails = sp.getInt("watch_auth_fails", 0) + 1;
                sp.edit().putInt("watch_auth_fails", fails)
                        .apply();
                if (fails == 3) {
                    bell(ctx, "cuenta",
                            "El vigía perdió la sesión",
                            "Lleva 3 revisiones sin poder "
                                    + "entrar: abre la app e "
                                    + "inicia sesión para que "
                                    + "siga vigilando",
                            "cuentas");
                    // También en Android (v1.5.95):
                    // la campana sola no basta —
                    // con la sesión muerta el
                    // Vigía entero está parado y
                    // él puede no abrir la app
                    // en días.
                    postSessionLost(ctx);
                }
                setStatus(ctx, "Sesión caducada: entra a la app");
                return;
            }
            if (ru.error != null) {
                // Panel caído (v1.5.97, líos):
                // tras 4 revisiones seguidas
                // sin respuesta, avisa — con
                // el panel caído no hay
                // avisos ni automático que
                // valgan. La falta de red
                // del propio teléfono NO
                // cuenta (eso se chequea
                // antes y no es el panel).
                int pf = sp.getInt(
                        "watch_panel_fails", 0) + 1;
                sp.edit().putInt("watch_panel_fails",
                        pf).apply();
                if (pf >= 4 && !sp.getBoolean(
                        "watch_panel_down", false)) {
                    sp.edit().putBoolean(
                            "watch_panel_down", true)
                            .apply();
                    String pTitle =
                            "El panel no responde";
                    String pSub = "Lleva " + pf
                            + " revisiones seguidas "
                            + "sin responder · los "
                            + "avisos y el pago "
                            + "automático no pueden "
                            + "trabajar";
                    bell(ctx, "sistema", pTitle, pSub,
                            "home");
                    if (sp.getBoolean("n_sistema",
                            true)) {
                        SysNotices.post(ctx, "sistema",
                                pTitle, pSub, "home",
                                -1, null);
                    }
                }
                setStatus(ctx, "Sin respuesta del panel");
                return;
            }
            sp.edit().putInt("watch_auth_fails", 0).apply();

            String raw = sp.getString("seen_users_json", null);
            if (raw == null) {
                // Sin siembra no hay vigilia: la primera
                // lista la siembra la app abierta.
                setStatus(ctx,
                        "Esperando la primera lista desde la app");
                return;
            }
            Set<Long> seen = new HashSet<>();
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    seen.add(arr.optLong(i));
                }
            } catch (Exception e) {
                setStatus(ctx,
                        "Esperando la primera lista desde la app");
                return;
            }
            long seededAt = sp.getLong("seen_users_at", 0);
            Set<Long> createdHere = createdAccountIds(ctx);
            int nuevas = 0;
            for (int i = 0; i < ru.items.length(); i++) {
                JSONObject u = ru.items.optJSONObject(i);
                if (u == null) continue;
                Object ido = u.opt("id");
                if (!(ido instanceof Number)) continue;
                long uid = ((Number) ido).longValue();
                boolean isNew = !seen.contains(uid);
                seen.add(uid);
                if (!isNew) continue;
                if (createdHere.contains(uid)) continue;
                long createdMs = isoToMs(
                        u.optString("created_at", ""));
                if (createdMs > 0 && seededAt > 0
                        && createdMs < seededAt - 300000L) {
                    continue; // vieja que aparece tarde: silencio
                }
                String username = u.optString("username", "");
                String when = whenText(createdMs);
                bell(ctx, "cuenta",
                        username + " se registró",
                        "Cuenta nueva · se registró " + when
                                + " · no se creó desde la app",
                        "cuentas");
                markCreated(ctx, uid);
                notifySystem(ctx, username, createdMs, uid);
                nuevas++;
            }
            JSONArray out = new JSONArray();
            for (Long id : seen) out.put(id.longValue());
            sp.edit().putString("seen_users_json", out.toString())
                    .apply();
            // Túneles nuevos de la web (v0.66.0, su orden
            // de mejorar el Vigía): la misma vigilia, con
            // el mismo registro que usa la app abierta
            // (seen_tunnels_json). Sin siembra, esta parte
            // espera en silencio, como las cuentas. Un
            // fallo aquí nunca tumba la revisión de cuentas.
            int nuevosTun = 0;
            JSONArray watchTunnels = null;
            try {
                String rawT = sp.getString(
                        "seen_tunnels_json", null);
                if (rawT != null) {
                    ApiClient.ItemsResult rt = new ApiClient(
                            new SessionStore(ctx))
                            .useLoadTimeouts()
                            .getAllItems("Vigía — túneles",
                                    PanelConfig.TUNNELS_URL_ALL);
                    if (rt.error == null && !rt.authFailed) {
                        watchTunnels = rt.items;
                        Set<Long> seenT = new HashSet<>();
                        JSONArray arrT = new JSONArray(rawT);
                        for (int i = 0; i < arrT.length(); i++) {
                            seenT.add(arrT.optLong(i));
                        }
                        long seededT = sp.getLong(
                                "seen_tunnels_at", 0);
                        Set<Long> createdTun =
                                createdTunnelIds(ctx);
                        java.util.Map<Long, String> userNames =
                                new java.util.HashMap<>();
                        for (int i = 0; i < ru.items.length();
                                i++) {
                            JSONObject uu = ru.items
                                    .optJSONObject(i);
                            if (uu == null) continue;
                            Object ido2 = uu.opt("id");
                            if (ido2 instanceof Number) {
                                userNames.put(
                                        ((Number) ido2)
                                                .longValue(),
                                        uu.optString(
                                                "username", ""));
                            }
                        }
                        for (int i = 0; i < rt.items.length();
                                i++) {
                            JSONObject t = rt.items
                                    .optJSONObject(i);
                            if (t == null) continue;
                            Object ido = t.opt("id");
                            if (!(ido instanceof Number)) {
                                continue;
                            }
                            long tid = ((Number) ido)
                                    .longValue();
                            boolean isNew = !seenT
                                    .contains(tid);
                            seenT.add(tid);
                            if (!isNew) continue;
                            if (createdTun.contains(tid)) {
                                continue;
                            }
                            long createdMs = isoToMs(
                                    t.optString("created_at",
                                            ""));
                            if (createdMs > 0 && seededT > 0
                                    && createdMs
                                            < seededT - 300000L) {
                                continue;
                            }
                            String tname = t.optString(
                                    "name", "");
                            String owner = "";
                            Object uo = t.opt("user");
                            long ouid = -1;
                            if (uo instanceof Number) {
                                ouid = ((Number) uo)
                                        .longValue();
                            } else if (uo
                                    instanceof JSONObject) {
                                ouid = ((JSONObject) uo)
                                        .optLong("id", -1);
                            }
                            if (ouid > 0 && userNames
                                    .containsKey(ouid)) {
                                owner = userNames.get(ouid);
                            }
                            bell(ctx, "tunel",
                                    tname + " · túnel nuevo",
                                    "Cuenta " + (owner
                                            .isEmpty()
                                            ? "—" : owner)
                                            + " · se creó "
                                            + whenText(createdMs)
                                            + " · no se creó "
                                            + "desde la app",
                                    "tuneles");
                            markCreatedTunnel(ctx, tid);
                            notifySystemTunnel(ctx, tname,
                                    owner, createdMs);
                            nuevosTun++;
                        }
                        JSONArray outT = new JSONArray();
                        for (Long id : seenT) {
                            outT.put(id.longValue());
                        }
                        sp.edit().putString(
                                "seen_tunnels_json",
                                outT.toString()).apply();
                    }
                }
            } catch (Exception ignored) {
                // los túneles nunca tumban las cuentas
            }
            // Detecciones de cambios en la web (v0.68.0):
            // la misma comparación contra la foto que
            // hace la app abierta — eliminados, estados,
            // cuentas sin túnel y marcas de la web. Quien
            // compare primero actualiza la foto.
            try {
                runDiffWatch(ctx, ru.items, watchTunnels);
            } catch (Exception ignored) {
                // la comparación nunca tumba la revisión
            }
            String res;
            if (nuevas > 0 || nuevosTun > 0) {
                StringBuilder rb = new StringBuilder();
                if (nuevas > 0) {
                    rb.append(nuevas).append(nuevas == 1
                            ? " cuenta nueva"
                            : " cuentas nuevas");
                }
                if (nuevosTun > 0) {
                    if (rb.length() > 0) rb.append(" · ");
                    rb.append(nuevosTun).append(nuevosTun == 1
                            ? " túnel nuevo"
                            : " túneles nuevos");
                }
                res = rb.toString();
            } else {
                res = "Sin novedad";
            }
            setStatus(ctx, res);
            // Prueba de vida (v0.54.0, idea suya): la
            // primera revisión que el Vigía completa queda
            // escrita en el Historial de la app, para que
            // sepa que está vigilando sin esperar a que
            // aparezca un registro de verdad.
            if (!sp.getBoolean("watch_first_done", false)) {
                sp.edit().putBoolean("watch_first_done", true)
                        .apply();
                HistoryLog.add(ctx, "cuenta",
                        "Vigía de registros en marcha",
                        "Primera revisión con la app "
                                + "cerrada: " + res,
                        null);
            }
        } catch (Exception ignored) {
            // una revisión fallida nunca rompe nada: a la
            // próxima hora se intenta de nuevo
        }
    }

    // ---------- Detecciones de cambios (v0.68.0) ----------
    // Espejo de la comparación de la app abierta
    // (runWatchDiff de MainActivity): misma foto
    // (watch_snap_json), mismos registros «self» y mismo
    // registro de cuentas sin túnel. Los avisos salen por
    // la campana de siempre (bell), que respeta el
    // interruptor de cada tipo.

    private static JSONObject selfRegS(Context ctx,
            String key) {
        try {
            String s = settings(ctx).getString(key, null);
            return s != null ? new JSONObject(s)
                    : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static boolean selfStatusCoversS(Context ctx,
            String pfx, long id, int st) {
        try {
            JSONArray v = selfRegS(ctx, "self_status_json")
                    .optJSONArray(pfx + id);
            return v != null && v.optInt(0, -9) == st
                    && System.currentTimeMillis()
                            - v.optLong(1, 0) < 24L * 3600000L;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean selfDeletedCoversS(Context ctx,
            String pfx, long id) {
        try {
            long ts = selfRegS(ctx, "self_deleted_json")
                    .optLong(pfx + id, 0);
            return ts > 0 && System.currentTimeMillis() - ts
                    < 24L * 3600000L;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean selfMarkCoversS(Context ctx,
            String cell) {
        try {
            long ts = selfRegS(ctx, "self_marks_json")
                    .optLong(cell, 0);
            return ts > 0 && System.currentTimeMillis() - ts
                    < 7L * 24L * 3600000L;
        } catch (Exception e) {
            return false;
        }
    }

    /** La nota «self» se CONSUME al usarse
     *  (v1.5.51, espejo de la app): la
     *  comparación que la reconoce como
     *  propia la borra. */
    private static void consumeSelfStatusS(Context ctx,
            String pfx, long id) {
        try {
            JSONObject o = selfRegS(ctx,
                    "self_status_json");
            if (o.has(pfx + id)) {
                o.remove(pfx + id);
                settings(ctx).edit()
                        .putString("self_status_json",
                                o.toString())
                        .apply();
            }
        } catch (Exception ignored) {
        }
    }

    private static void consumeSelfDeletedS(Context ctx,
            String pfx, long id) {
        try {
            JSONObject o = selfRegS(ctx,
                    "self_deleted_json");
            if (o.has(pfx + id)) {
                o.remove(pfx + id);
                settings(ctx).edit()
                        .putString("self_deleted_json",
                                o.toString())
                        .apply();
            }
        } catch (Exception ignored) {
        }
    }

    private static long ownerIdS(JSONObject t) {
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

    private static Set<String> paidCellSetS(JSONArray matrix) {
        Set<String> out = new HashSet<>();
        if (matrix == null) return out;
        java.util.Calendar c = java.util.Calendar.getInstance();
        int minIdx = c.get(java.util.Calendar.YEAR) * 12
                + c.get(java.util.Calendar.MONTH) + 1 - 13;
        for (int i = 0; i < matrix.length(); i++) {
            JSONObject row = matrix.optJSONObject(i);
            if (row == null) continue;
            long tid = -1;
            Object tv = row.opt("tunnel");
            if (tv instanceof Number) {
                tid = ((Number) tv).longValue();
            } else if (tv instanceof JSONObject) {
                Object ido = ((JSONObject) tv).opt("id");
                if (ido instanceof Number) {
                    tid = ((Number) ido).longValue();
                }
            }
            if (tid < 0) continue;
            JSONArray months = row.optJSONArray("months");
            if (months == null) continue;
            for (int j = 0; j < months.length(); j++) {
                JSONObject cell = months.optJSONObject(j);
                if (cell == null
                        || !cell.optBoolean("is_paid", false)) {
                    continue;
                }
                int m = cell.optInt("month", 0);
                int y = cell.optInt("year", 0);
                if (y * 12 + m < minIdx) continue;
                out.add(tid + ":" + m + ":" + y);
            }
        }
        return out;
    }

    private static void runDiffWatch(Context ctx,
            JSONArray users, JSONArray tunnels) {
        if (users == null || tunnels == null) return;
        // El panel volvió (v1.5.97): esta
        // función solo corre con datos
        // frescos del panel en la mano.
        try {
            SharedPreferences spR = settings(ctx);
            if (spR.getBoolean("watch_panel_down",
                    false)) {
                spR.edit().putBoolean(
                        "watch_panel_down", false)
                        .putInt("watch_panel_fails", 0)
                        .apply();
                bell(ctx, "sistema",
                        "El panel volvió",
                        "Ya responde otra vez · el "
                                + "Vigía sigue "
                                + "trabajando",
                        "home");
                if (spR.getBoolean("n_sistema",
                        true)) {
                    SysNotices.post(ctx, "sistema",
                            "El panel volvió",
                            "Ya responde otra vez · "
                                    + "el Vigía sigue "
                                    + "trabajando",
                            "home", -1, null);
                }
            } else if (spR.getInt("watch_panel_fails",
                    0) != 0) {
                spR.edit().putInt("watch_panel_fails",
                        0).apply();
            }
        } catch (Exception ignored) {
        }
        SharedPreferences sp = settings(ctx);
        JSONObject curU = new JSONObject();
        JSONObject curT = new JSONObject();
        java.util.Map<Long, Integer> tCount =
                new java.util.HashMap<>();
        java.util.Map<Long, String> tNameById =
                new java.util.HashMap<>();
        try {
            for (int i = 0; i < tunnels.length(); i++) {
                JSONObject t = tunnels.optJSONObject(i);
                if (t == null) continue;
                Object ido = t.opt("id");
                if (!(ido instanceof Number)) continue;
                long tid = ((Number) ido).longValue();
                long oid = ownerIdS(t);
                JSONArray v = new JSONArray();
                v.put(HomeStats.statusOf(t));
                v.put(oid);
                v.put(t.optString("name", ""));
                v.put(hostKeyS(t));
                curT.put(String.valueOf(tid), v);
                tNameById.put(tid, t.optString("name", ""));
                if (oid > 0) {
                    Integer n = tCount.get(oid);
                    tCount.put(oid, n == null ? 1 : n + 1);
                }
            }
            for (int i = 0; i < users.length(); i++) {
                JSONObject u = users.optJSONObject(i);
                if (u == null) continue;
                Object ido = u.opt("id");
                if (!(ido instanceof Number)) continue;
                long uid = ((Number) ido).longValue();
                Object ia = u.opt("is_active");
                int act = ia instanceof Boolean
                        && !((Boolean) ia).booleanValue() ? 0 : 1;
                JSONArray v = new JSONArray();
                v.put(act);
                v.put(isoToMs(u.optString("created_at", "")));
                v.put(u.optString("username", ""));
                curU.put(String.valueOf(uid), v);
            }
        } catch (Exception e) {
            return;
        }
        JSONObject snapNew = new JSONObject();
        try {
            snapNew.put("u", curU);
            snapNew.put("t", curT);
        } catch (Exception e) {
            return;
        }
        // La matriz solo hace falta para las marcas: si
        // no baja, esa parte espera a la próxima revisión.
        JSONArray matrix = null;
        try {
            ApiClient.ItemsResult rm = new ApiClient(
                    new SessionStore(ctx)).useLoadTimeouts()
                    .getAllItems("Vigía — matriz",
                            PanelConfig.MATRIX_URL);
            if (rm.error == null && !rm.authFailed) {
                matrix = rm.items;
            }
        } catch (Exception ignored) {
        }
        String raw = sp.getString("watch_snap_json", null);
        if (raw == null) {
            sp.edit().putString("watch_snap_json",
                    snapNew.toString()).apply();
            if (matrix != null) {
                try {
                    JSONArray a = new JSONArray();
                    for (String cell : paidCellSetS(matrix)) {
                        a.put(cell);
                    }
                    sp.edit().putString("watch_paid_json",
                            a.toString()).apply();
                } catch (Exception ignored) {
                }
            }
            return;
        }
        JSONObject snap;
        JSONObject oldU, oldT;
        try {
            snap = new JSONObject(raw);
            oldU = snap.optJSONObject("u");
            oldT = snap.optJSONObject("t");
        } catch (Exception e) {
            return;
        }
        if (oldU == null || oldT == null) return;
        if ((curU.length() == 0 && oldU.length() > 0)
                || (curT.length() == 0 && oldT.length() > 0)) {
            return;
        }
        java.util.Map<Long, String> uNameNow =
                new java.util.HashMap<>();
        java.util.Iterator<String> ku = curU.keys();
        while (ku.hasNext()) {
            String k = ku.next();
            JSONArray v = curU.optJSONArray(k);
            if (v != null) {
                try {
                    uNameNow.put(Long.parseLong(k),
                            v.optString(2, ""));
                } catch (Exception ignored) {
                }
            }
        }
        int fired = 0;
        try {
            java.util.Iterator<String> kt = oldT.keys();
            while (kt.hasNext()) {
                String k = kt.next();
                if (curT.has(k)) continue;
                long tid = Long.parseLong(k);
                if (selfDeletedCoversS(ctx, "t", tid)) {
                    consumeSelfDeletedS(ctx, "t", tid);
                    continue;
                }
                if (fired >= 6) break;
                JSONArray ov = oldT.optJSONArray(k);
                String oname = ov != null
                        ? (uNameNow.containsKey(ov.optLong(1, -1)) ? uNameNow.get(ov.optLong(1, -1)) : "—") : "—";
                bell(ctx, "eliminado",
                        "Túnel eliminado en la web: "
                                + (ov != null
                                        ? ov.optString(2, "")
                                        : k),
                        "Cuenta " + oname + " · ya no está "
                                + "en el panel y no se borró "
                                + "desde la app",
                        "tuneles");
                fired++;
            }
            java.util.Iterator<String> ku2 = oldU.keys();
            while (ku2.hasNext()) {
                String k = ku2.next();
                if (curU.has(k)) continue;
                long uid = Long.parseLong(k);
                if (selfDeletedCoversS(ctx, "u", uid)) {
                    consumeSelfDeletedS(ctx, "u", uid);
                    continue;
                }
                if (fired >= 6) break;
                JSONArray ov = oldU.optJSONArray(k);
                bell(ctx, "eliminado",
                        "Cuenta eliminada en la web: "
                                + (ov != null
                                        ? ov.optString(2, "")
                                        : k),
                        "Ya no está en el panel y no se "
                                + "borró desde la app",
                        "cuentas");
                fired++;
            }
            java.util.Iterator<String> kt2 = curT.keys();
            while (kt2.hasNext()) {
                String k = kt2.next();
                JSONArray ov = oldT.optJSONArray(k);
                JSONArray nv = curT.optJSONArray(k);
                if (ov == null || nv == null) continue;
                int os = ov.optInt(0, -9);
                int ns = nv.optInt(0, -9);
                if (os < 0 || ns < 0 || os == ns) continue;
                long tid = Long.parseLong(k);
                if (selfStatusCoversS(ctx, "t", tid, ns)) {
                    consumeSelfStatusS(ctx, "t", tid);
                    HistoryLog.add(ctx, "sistema",
                            "Cambio propio no avisado: "
                                    + "túnel "
                                    + nv.optString(2, "")
                                    + (ns == 1
                                            ? " habilitado"
                                            : " deshabilitado"),
                            "Se hizo desde la app: no es "
                                    + "un cambio de la web",
                            null);
                    continue;
                }
                if (fired >= 6) break;
                String owner = (uNameNow.containsKey(nv.optLong(1, -1)) ? uNameNow.get(nv.optLong(1, -1)) : "—");
                bell(ctx, "estado",
                        (ns == 1
                                ? "Túnel habilitado en la web: "
                                : "Túnel deshabilitado en la "
                                        + "web: ")
                                + nv.optString(2, ""),
                        "Cuenta " + owner + " · no se hizo "
                                + "desde la app",
                        "tuneles");
                fired++;
            }
            java.util.Iterator<String> ku3 = curU.keys();
            while (ku3.hasNext()) {
                String k = ku3.next();
                JSONArray ov = oldU.optJSONArray(k);
                JSONArray nv = curU.optJSONArray(k);
                if (ov == null || nv == null) continue;
                int os = ov.optInt(0, -9);
                int ns = nv.optInt(0, -9);
                if (os < 0 || ns < 0 || os == ns) continue;
                long uid = Long.parseLong(k);
                if (selfStatusCoversS(ctx, "u", uid, ns)) {
                    consumeSelfStatusS(ctx, "u", uid);
                    HistoryLog.add(ctx, "sistema",
                            "Cambio propio no avisado: "
                                    + "cuenta "
                                    + nv.optString(2, "")
                                    + (ns == 1
                                            ? " habilitada"
                                            : " deshabilitada"),
                            "Se hizo desde la app: no es "
                                    + "un cambio de la web",
                            null);
                    continue;
                }
                if (fired >= 6) break;
                bell(ctx, "estado",
                        (ns == 1
                                ? "Cuenta habilitada en la web: "
                                : "Cuenta deshabilitada en la "
                                        + "web: ")
                                + nv.optString(2, ""),
                        "No se hizo desde la app",
                        "cuentas");
                fired++;
            }
            // Cuenta sin túnel al día siguiente.
            JSONArray notifiedArr = new JSONArray(
                    sp.getString("sintunel_json", "[]"));
            Set<Long> notified = new HashSet<>();
            for (int i = 0; i < notifiedArr.length(); i++) {
                notified.add(notifiedArr.optLong(i));
            }
            boolean nChanged = false;
            long now = System.currentTimeMillis();
            java.util.Iterator<String> ku4 = curU.keys();
            while (ku4.hasNext()) {
                String k = ku4.next();
                long uid = Long.parseLong(k);
                JSONArray nv = curU.optJSONArray(k);
                if (nv == null) continue;
                int cnt = tCount.containsKey(uid)
                        ? tCount.get(uid) : 0;
                if (cnt > 0) {
                    if (notified.remove(uid)) nChanged = true;
                    continue;
                }
                long createdMs = nv.optLong(1, 0);
                if (createdMs <= 0
                        || now - createdMs < 24L * 3600000L) {
                    continue;
                }
                if (notified.contains(uid)) continue;
                notified.add(uid);
                nChanged = true;
                bell(ctx, "sintunel",
                        "Cuenta sin túnel: "
                                + nv.optString(2, ""),
                        "Se registró hace más de un día y "
                                + "sigue sin túnel: créaselo "
                                + "desde la app o la web",
                        "cuentas");
            }
            if (nChanged) {
                JSONArray out = new JSONArray();
                for (Long id : notified) {
                    out.put(id.longValue());
                }
                sp.edit().putString("sintunel_json",
                        out.toString()).apply();
            }
            // Marcas hechas en la web.
            if (matrix != null) {
                Set<String> cur = paidCellSetS(matrix);
                Set<String> old = new HashSet<>();
                String rawP = sp.getString(
                        "watch_paid_json", null);
                if (rawP == null) {
                    JSONArray a = new JSONArray();
                    for (String cell : cur) a.put(cell);
                    sp.edit().putString("watch_paid_json",
                            a.toString()).apply();
                } else {
                    JSONArray a = new JSONArray(rawP);
                    java.util.Calendar cc =
                            java.util.Calendar.getInstance();
                    int minIdx = cc.get(
                            java.util.Calendar.YEAR) * 12
                            + cc.get(java.util.Calendar.MONTH)
                            + 1 - 13;
                    for (int i = 0; i < a.length(); i++) {
                        String cell = a.optString(i, "");
                        String[] parts = cell.split(":");
                        if (parts.length == 3) {
                            try {
                                int mm = Integer.parseInt(
                                        parts[1]);
                                int yy = Integer.parseInt(
                                        parts[2]);
                                if (yy * 12 + mm < minIdx) {
                                    continue;
                                }
                            } catch (Exception ignored) {
                                continue;
                            }
                        }
                        old.add(cell);
                    }
                    for (String cell : cur) {
                        if (old.contains(cell)
                                || selfMarkCoversS(ctx, cell)) {
                            continue;
                        }
                        if (fired >= 6) break;
                        String[] parts = cell.split(":");
                        long tid = Long.parseLong(parts[0]);
                        int mm = Integer.parseInt(parts[1]);
                        int yy = Integer.parseInt(parts[2]);
                        bell(ctx, "webmark",
                                "Pago marcado en la web: "
                                        + (tNameById.containsKey(tid) ? tNameById.get(tid) : "túnel " + tid),
                                HomeStats.monthLabel(yy, mm)
                                        + " se marcó en el "
                                        + "panel, sin pasar "
                                        + "por la app",
                                "pagos");
                        fired++;
                    }
                    for (String cell : old) {
                        if (cur.contains(cell)
                                || selfMarkCoversS(ctx, cell)) {
                            continue;
                        }
                        if (fired >= 6) break;
                        String[] parts = cell.split(":");
                        long tid = Long.parseLong(parts[0]);
                        int mm = Integer.parseInt(parts[1]);
                        int yy = Integer.parseInt(parts[2]);
                        bell(ctx, "webmark",
                                "Pago desmarcado en la web: "
                                        + (tNameById.containsKey(tid) ? tNameById.get(tid) : "túnel " + tid),
                                HomeStats.monthLabel(yy, mm)
                                        + " volvió a pendiente "
                                        + "en el panel, sin "
                                        + "pasar por la app",
                                "pagos");
                        fired++;
                    }
                    JSONArray out = new JSONArray();
                    for (String cell : cur) out.put(cell);
                    sp.edit().putString("watch_paid_json",
                            out.toString()).apply();
                }
            }
        } catch (Exception ignored) {
            // comparar nunca rompe la revisión
        }
        sp.edit().putString("watch_snap_json",
                snapNew.toString()).apply();
    }

    private static String whenText(long createdMs) {
        if (createdMs <= 0) return "mientras la app estaba "
                + "cerrada";
        java.text.SimpleDateFormat sameDay =
                new java.text.SimpleDateFormat("h:mm a",
                        Locale.getDefault());
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.setTimeInMillis(createdMs);
        boolean today = a.get(java.util.Calendar.YEAR)
                == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR)
                == b.get(java.util.Calendar.DAY_OF_YEAR);
        if (today) return "hoy a las " + sameDay.format(
                new java.util.Date(createdMs));
        return "el " + new java.text.SimpleDateFormat(
                "dd/MM", Locale.getDefault()).format(
                new java.util.Date(createdMs))
                + " a las " + sameDay.format(
                new java.util.Date(createdMs));
    }

    // ---------------- avisos ----------------

    /** El mismo almacén de la campana que usa la app
     *  (addNotice): respeta el interruptor del tipo y el
     *  tope de 100. */
    private static void bell(Context ctx, String kind,
            String title, String sub, String target) {
        try {
            SharedPreferences sp = settings(ctx);
            if (!sp.getBoolean("n_" + kind, true)) return;
            SharedPreferences np = ctx.getSharedPreferences(
                    PREFS_NOTICES, Context.MODE_PRIVATE);
            JSONArray a;
            try {
                String s = np.getString("items_json", null);
                a = s != null ? new JSONArray(s) : new JSONArray();
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
            // Espejo en Android (v1.5.30): los cambios
            // en la web que el Vigía ve en segundo
            // plano también suenan fuera de la app.
            // Cuenta y túnel NO: ya tienen su
            // notificación propia (notifySystem*).
            if (kind.equals("eliminado")
                    || kind.equals("estado")
                    || kind.equals("sintunel")
                    || kind.equals("webmark")) {
                SysNotices.post(ctx, kind, title, sub,
                        target, -1, null);
            }
        } catch (Exception ignored) {
        }
    }

    private static void ensureChannel(Context ctx) {
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
                                    "Cuentas nuevas",
                                    NotificationManager
                                            .IMPORTANCE_HIGH);
                    ch.setDescription("Aviso cuando alguien "
                            + "se registra en la web");
                    nm.createNotificationChannel(ch);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Muestras para el botón de
     *  Desarrollo «Probar notificaciones
     *  de Android» (v1.5.91, su orden):
     *  las dos notificaciones de ESTE
     *  canal (cuenta nueva y túnel
     *  nuevo) con datos de ejemplo.
     *  Respetan sus interruptores de
     *  Ajustes (n_cuenta / n_tunel),
     *  como las de verdad. */
    /** Notificación de Android de la
     *  sesión perdida (v1.5.95): compañera
     *  de la campana del mismo hecho,
     *  porque la cuenta NO se espeja por
     *  SysNotices y este aviso es de los
     *  que no pueden esperar a que abra
     *  la app. Respeta el mismo
     *  interruptor que la campana. */
    private static void postSessionLost(Context ctx) {
        try {
            if (!settings(ctx).getBoolean(
                    "n_cuenta", true)) {
                return;
            }
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx,
                    MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 4001, open, flags);
            String text = "Lleva 3 revisiones sin poder "
                    + "entrar · abre la app e inicia "
                    + "sesión para que siga vigilando";
            Notification.Builder b =
                    Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(
                                    ctx, CHANNEL_ID)
                            : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle(
                            "El vigía perdió la sesión")
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
                    "El vigía perdió la sesión", text);
            nm.notify(4000, b.build());
        } catch (Exception ignored) {
        }
    }

    /** Clave comparable del servidor de
     *  un túnel (v1.5.95): «id:N» cuando
     *  el host trae id; «s:» + texto si
     *  no. La MISMA clave se guarda en
     *  la foto del Vigía desde la app y
     *  desde el fondo. */
    private static String hostKeyS(JSONObject t) {
        Object h = t.opt("host");
        if (h instanceof Number) {
            return "id:" + ((Number) h).longValue();
        }
        if (h instanceof JSONObject) {
            Object i = ((JSONObject) h).opt("id");
            if (i instanceof Number) {
                return "id:" + ((Number) i).longValue();
            }
            String n = ((JSONObject) h)
                    .optString("name", "");
            if (!n.isEmpty()) return "s:" + n;
        }
        if (h instanceof String
                && !((String) h).isEmpty()) {
            return "s:" + h;
        }
        return "";
    }

    /** La clave de servidor, legible. */
    private static String hostLabelS(String key) {
        if (key == null || key.isEmpty()) return "—";
        if (key.startsWith("id:")) {
            return "servidor #" + key.substring(3);
        }
        if (key.startsWith("s:")) return key.substring(2);
        return key;
    }

    /** ¿El cambio de servidor de este
     *  túnel fue PROPIO? (v1.5.95): la
     *  app anota sus cambios de host en
     *  self_hosts_json; si la nota cubre
     *  el servidor nuevo, se consume y
     *  no avisa como cambio de la web.
     *  Mismo registro que lee la app. */
    private static boolean selfHostCoversS(Context ctx,
            long tid, String newKey) {
        try {
            SharedPreferences sp = settings(ctx);
            JSONObject o = new JSONObject(
                    sp.getString("self_hosts_json", "{}"));
            JSONArray v = o.optJSONArray(
                    String.valueOf(tid));
            if (v == null) return false;
            long ts = v.optLong(1, 0);
            if (System.currentTimeMillis() - ts
                    > 24L * 3600000L) {
                return false;
            }
            if (!newKey.equals(v.optString(0, ""))) {
                return false;
            }
            o.remove(String.valueOf(tid));
            sp.edit().putString("self_hosts_json",
                    o.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void postTestSamples(Context ctx) {
        long now = System.currentTimeMillis();
        notifySystem(ctx, "pedro87", now, 1);
        notifySystemTunnel(ctx, "Pepe-T1", "Pepe", now);
    }

    private static void notifySystem(Context ctx,
            String username, long createdMs, long uid) {
        try {
            if (!settings(ctx).getBoolean("n_cuenta", true)) {
                return;
            }
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("open_cuentas", true);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 8100, open, flags);
            PendingIntent piVer = PendingIntent.getActivity(
                    ctx, 8101, open, flags);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle(username + " se registró")
                    .setContentText("Cuenta nueva · se "
                            + "registró "
                            + whenText(createdMs))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_stat_3force,
                            "Ver cuenta ›", piVer);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
            }
            // Botón «Crear túnel» (v1.5.94, su
            // orden: que la notificación de
            // registro traiga en los botones
            // cómo asignarle su túnel).
            if (uid >= 0) {
                Intent crear = new Intent(ctx,
                        MainActivity.class);
                crear.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                crear.putExtra("crear_tunel_uid", uid);
                crear.putExtra("crear_tunel_uname", username);
                PendingIntent piCrear =
                        PendingIntent.getActivity(ctx, 8102,
                                crear, flags);
                b.addAction(R.drawable.ic_stat_3force,
                        "Crear túnel ›", piCrear);
            }
            NotifIcons.attach(b, ctx, "cuenta",
                    username + " se registró",
                    "Cuenta nueva · se registró "
                            + whenText(createdMs));
            int id = 2000 + (username.hashCode() & 0x3fff);
            nm.notify(id, b.build());
        } catch (Exception ignored) {
        }
    }

    // ---------------- registro de creaciones propias ----------------

    private static Set<Long> createdAccountIds(Context ctx) {
        Set<Long> out = new HashSet<>();
        try {
            String s = ctx.getSharedPreferences(PREFS_CREATED,
                    Context.MODE_PRIVATE)
                    .getString("accounts_json", null);
            if (s == null) return out;
            JSONObject o = new JSONObject(s);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                try {
                    out.add(Long.parseLong(it.next()));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** La misma marca que pone la app al crear (para que la
     *  cuenta salga como «Nueva» y arriba en Cuentas). */
    private static void markCreated(Context ctx, long uid) {
        try {
            SharedPreferences cp = ctx.getSharedPreferences(
                    PREFS_CREATED, Context.MODE_PRIVATE);
            JSONObject o;
            try {
                String s = cp.getString("accounts_json", null);
                o = s != null ? new JSONObject(s)
                        : new JSONObject();
            } catch (Exception e) {
                o = new JSONObject();
            }
            long now = System.currentTimeMillis();
            o.put(String.valueOf(uid), now);
            java.util.Iterator<String> it = o.keys();
            java.util.List<String> old =
                    new java.util.ArrayList<>();
            while (it.hasNext()) {
                String k = it.next();
                if (now - o.optLong(k, 0) > 24L * 3600000L) {
                    old.add(k);
                }
            }
            for (String k : old) o.remove(k);
            cp.edit().putString("accounts_json", o.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private static void notifySystemTunnel(Context ctx,
            String tname, String owner, long createdMs) {
        try {
            if (!settings(ctx).getBoolean("n_tunel", true)) {
                return;
            }
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("open_tuneles", true);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 9, open, flags);
            PendingIntent piVer = PendingIntent.getActivity(
                    ctx, 9101, open, flags);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_3force)
                    .setContentTitle(tname + " · túnel nuevo")
                    .setContentText("Cuenta "
                            + (owner == null || owner.isEmpty()
                                    ? "—" : owner)
                            + " · se creó en la web "
                            + whenText(createdMs))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_stat_3force,
                            "Ver túneles ›", piVer);
            try {
                b.setLargeIcon(BitmapFactory.decodeResource(
                        ctx.getResources(),
                        R.mipmap.ic_launcher));
            } catch (Exception ignored) {
            }
            NotifIcons.attach(b, ctx, "tunel",
                    tname + " · túnel nuevo",
                    "Cuenta "
                            + (owner == null
                                    || owner.isEmpty()
                                    ? "—" : owner)
                            + " · se creó en la web "
                            + whenText(createdMs));
            int id = 3000 + (tname.hashCode() & 0x3fff);
            nm.notify(id, b.build());
        } catch (Exception ignored) {
        }
    }

    private static Set<Long> createdTunnelIds(Context ctx) {
        Set<Long> out = new HashSet<>();
        try {
            String s = ctx.getSharedPreferences(PREFS_CREATED,
                    Context.MODE_PRIVATE)
                    .getString("tunnels_json", null);
            if (s == null) return out;
            JSONObject o = new JSONObject(s);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                try {
                    out.add(Long.parseLong(it.next()));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** La misma marca que pone la app al crear un túnel. */
    private static void markCreatedTunnel(Context ctx,
            long tid) {
        try {
            SharedPreferences cp = ctx.getSharedPreferences(
                    PREFS_CREATED, Context.MODE_PRIVATE);
            JSONObject o;
            try {
                String s = cp.getString("tunnels_json", null);
                o = s != null ? new JSONObject(s)
                        : new JSONObject();
            } catch (Exception e) {
                o = new JSONObject();
            }
            long now = System.currentTimeMillis();
            o.put(String.valueOf(tid), now);
            java.util.Iterator<String> it = o.keys();
            java.util.List<String> old =
                    new java.util.ArrayList<>();
            while (it.hasNext()) {
                String k = it.next();
                if (now - o.optLong(k, 0) > 24L * 3600000L) {
                    old.add(k);
                }
            }
            for (String k : old) o.remove(k);
            cp.edit().putString("tunnels_json", o.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    // ---------------- utilidades ----------------

    private static boolean hasNetwork(Context ctx) {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) ctx
                            .getSystemService(
                                    Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            android.net.NetworkInfo ni =
                    cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    /** La misma lectura de fechas del panel que usa la app
     *  (isoToMs de MainActivity). */
    static long isoToMs(String iso) {
        if (iso == null) return -1;
        String s = iso.trim();
        s = s.replaceAll("\\.\\d+", "");
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1) + "+00:00";
        }
        s = s.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ssXXX",
                            Locale.US);
            java.util.Date d = f.parse(s);
            return d != null ? d.getTime() : -1;
        } catch (Exception e) {
            try {
                java.text.SimpleDateFormat f2 =
                        new java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss",
                                Locale.US);
                f2.setTimeZone(java.util.TimeZone
                        .getTimeZone("America/Havana"));
                java.util.Date d = f2.parse(s.length() > 19
                        ? s.substring(0, 19) : s);
                return d != null ? d.getTime() : -1;
            } catch (Exception e2) {
                return -1;
            }
        }
    }
}
