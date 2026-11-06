package com.gennis.admin3force;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Historial local de la app (v0.26.0): el registro de eventos —
 * pagos marcados, SMS detectados/asignados/descartados, acciones
 * sobre cuentas y túneles, eventos de la cola y del sistema.
 *
 * LIGERO a propósito (su orden: nada de acumulación de basura):
 * una sola lista JSON en las preferencias privadas, con TOPE de
 * 300 eventos; al pasarlo, los más viejos se caen solos. Nada de
 * bases de datos ni archivos sueltos.
 *
 * Estático y con Context: lo escriben igual la actividad, el
 * receptor de SMS y el barrido periódico.
 */
public final class HistoryLog {

    private HistoryLog() {}

    private static final String PREFS = "admin3force_history";
    private static final String KEY = "events_json";
    private static final int MAX_EVENTS = 300;

    /** Añade un evento (ts = ahora). kind: pago/sms/cuenta/tunel/sistema. */
    public static synchronized void add(Context ctx, String kind, String text) {
        add(ctx, kind, text, null, null);
    }

    /**
     * Evento con título y detalle separados (la fila del
     * Historial del prototipo: negrita + línea secundaria), y la
     * clave del SMS cuando aplica (las descartadas la guardan
     * para poder restaurarse). kind extra: «descartada».
     */
    public static synchronized void add(Context ctx, String kind,
                                        String title, String sub,
                                        String smsKey) {
        if (ctx == null || title == null) return;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = read(sp);
            JSONObject e = new JSONObject();
            e.put("ts", System.currentTimeMillis());
            e.put("kind", kind != null ? kind : "sistema");
            e.put("title", title);
            if (sub != null) e.put("sub", sub);
            if (smsKey != null) e.put("sms_key", smsKey);
            a.put(e);
            while (a.length() > MAX_EVENTS) {
                // JSONArray no tiene remove en APIs viejas: se
                // reconstruye sin el más viejo.
                JSONArray b = new JSONArray();
                for (int i = 1; i < a.length(); i++) b.put(a.opt(i));
                a = b;
            }
            sp.edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {
            // El historial nunca debe tumbar la app.
        }
    }

    /**
     * Quita los eventos de descarte de una transferencia (al
     * restaurarla desde el Historial). Devuelve cuántos quitó.
     */
    public static synchronized int removeDiscards(Context ctx,
                                                  String smsKey) {
        if (ctx == null || smsKey == null) return 0;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = read(sp);
            JSONArray b = new JSONArray();
            int removed = 0;
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e != null && "descartada".equals(e.optString("kind", ""))
                        && smsKey.equals(e.optString("sms_key", ""))) {
                    removed++;
                    continue;
                }
                b.put(e);
            }
            if (removed > 0) {
                sp.edit().putString(KEY, b.toString()).apply();
            }
            return removed;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Todos los eventos, del más viejo al más nuevo. */
    public static JSONArray readAll(Context ctx) {
        if (ctx == null) return new JSONArray();
        return read(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    private static JSONArray read(SharedPreferences sp) {
        String s = sp.getString(KEY, null);
        if (s == null) return new JSONArray();
        try {
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
