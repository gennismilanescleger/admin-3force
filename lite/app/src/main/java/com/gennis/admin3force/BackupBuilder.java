package com.gennis.admin3force;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * El constructor del respaldo COMPLETO (v1.0.0): la
 * misma construcción que «Exportar respaldo» hacía
 * dentro de MainActivity, sacada a clase propia para
 * que el respaldo AUTOMÁTICO de las 2 AM guarde
 * exactamente lo mismo que el manual — una sola
 * fuente, sin dos listas que se desincronicen.
 * Todo sale de las preferencias locales; nada de la
 * web (el alias y las notas viven en el panel).
 */
final class BackupBuilder {

    private BackupBuilder() {
    }

    static JSONObject build(Context ctx) throws Exception {
        JSONObject root = new JSONObject();
        JSONObject st = new JSONObject();
        SharedPreferences sp = ctx.getSharedPreferences(
                "admin3force_settings", Context.MODE_PRIVATE);
        st.put("day", sp.getInt("day", 10));
        st.put("grace", sp.getInt("grace", 3));
        st.put("new_hours", sp.getInt("new_hours", 24));
        st.put("trial_expired_hours",
                sp.getInt("trial_expired_hours", 48));
        st.put("trial_warn_hours",
                sp.getInt("trial_warn_hours", 3));
        st.put("trial_custom_secs",
                sp.getInt("trial_custom_secs", 60));
        st.put("lock_after_secs",
                sp.getInt("lock_after_secs", 60));
        st.put("trial_disable_expiry",
                sp.getBoolean("trial_disable_expiry", true));
        st.put("auto_pay", sp.getBoolean("auto_pay", false));
        st.put("queues_paused",
                sp.getBoolean("queues_paused", false));
        st.put("auto_bg", sp.getBoolean("auto_bg", false));
        st.put("auto_multi",
                sp.getBoolean("auto_multi", false));
        st.put("auto_advance",
                sp.getBoolean("auto_advance", false));
        st.put("price_usdt",
                (double) sp.getFloat("price_usdt", 0f));
        st.put("min_auto_cup", sp.getInt("min_auto_cup", 0));
        st.put("auto_tol_pct", sp.getInt("auto_tol_pct", 3));
        st.put("auto_doubt_json",
                sp.getString("auto_doubt_json", "[]"));
        st.put("profiles_json",
                sp.getString("profiles_json", "[]"));
        st.put("profile_default_id",
                sp.getLong("profile_default_id", 0));
        st.put("links_json", ctx.getSharedPreferences(
                "admin3force_links", Context.MODE_PRIVATE)
                .getString("links_json", "{}"));
        st.put("cards_json", ctx.getSharedPreferences(
                "admin3force_cards", Context.MODE_PRIVATE)
                .getString("cards_json", "[]"));
        st.put("servers_json", ctx.getSharedPreferences(
                "admin3force_servers", Context.MODE_PRIVATE)
                .getString("list_json", "[]"));
        SharedPreferences spSms = ctx.getSharedPreferences(
                "admin3force_sms", Context.MODE_PRIVATE);
        st.put("billdays_json", ctx.getSharedPreferences(
                "admin3force_billdays", Context.MODE_PRIVATE)
                .getString("days_json", "{}"));
        st.put("trials_json", ctx.getSharedPreferences(
                "admin3force_local", Context.MODE_PRIVATE)
                .getString("trials_json", "{}"));
        st.put("assigned_amounts_json", spSms
                .getString("assigned_amounts_json", "{}"));
        st.put("sms_assigned_json", spSms
                .getString("assigned_json", "[]"));
        st.put("sms_discarded_json", spSms
                .getString("discarded_json", "[]"));
        st.put("mark_queue_json", spSms
                .getString("mark_queue_json", "[]"));
        st.put("write_queue_json", ctx.getSharedPreferences(
                "admin3force_queue", Context.MODE_PRIVATE)
                .getString("write_queue_json", "[]"));
        st.put("seen_users_json",
                sp.getString("seen_users_json", "[]"));
        st.put("seen_users_at",
                sp.getLong("seen_users_at", 0));
        st.put("seen_tunnels_json",
                sp.getString("seen_tunnels_json", "[]"));
        st.put("seen_tunnels_at",
                sp.getLong("seen_tunnels_at", 0));
        st.put("free_tunnels_json",
                sp.getString("free_tunnels_json", "[]"));
        st.put("personal_users_json",
                sp.getString("personal_users_json", "[]"));
        st.put("sintunel_json",
                sp.getString("sintunel_json", "[]"));
        SharedPreferences spCr = ctx.getSharedPreferences(
                "admin3force_created", Context.MODE_PRIVATE);
        st.put("created_accounts_json", spCr
                .getString("accounts_json", "{}"));
        st.put("created_tunnels_json", spCr
                .getString("tunnels_json", "{}"));
        st.put("sms_tm", sp.getBoolean("sms_tm", true));
        st.put("sms_ez", sp.getBoolean("sms_ez", true));
        st.put("lock_on", sp.getBoolean("lock_on", true));
        // El propio modo del respaldo automático viaja
        // (v1.0.0, regla de respaldo completo).
        st.put("auto_backup_mode",
                sp.getInt("auto_backup_mode", 0));
        String[] kinds = {"pago", "sinid", "invalido", "cola",
                "prueba", "cuenta", "tunel", "servidor", "cobro",
                "eliminado", "estado", "sintunel", "webmark"};
        for (String k : kinds) {
            st.put("n_" + k, sp.getBoolean("n_" + k, true));
        }
        root.put("settings", st);
        JSONObject tp = new JSONObject();
        for (String k : Templates.KEYS) {
            tp.put(k, Templates.get(ctx, k));
        }
        root.put("templates", tp);
        return root;
    }
}
