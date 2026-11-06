package com.gennis.admin3force;

import android.content.Context;

import java.util.Map;

/**
 * Las 5 plantillas de WhatsApp (v0.27.0): conjunto FIJO de la
 * spec — no se añaden ni se borran, solo se edita el texto de
 * cada una. Viven en las preferencias privadas; si una no se ha
 * editado nunca, vale el texto por defecto (el del prototipo,
 * con los textos literales de Gennis donde existen).
 *
 * La de la TASA es la misma del Home: la tarjeta de la tasa del
 * Inicio copia ESTA plantilla rellenada — una sola fuente (su
 * orden expresa). Las llaves {mes}, {cliente}, {valor}… se
 * rellenan al usarla con fill().
 */
public final class Templates {

    private Templates() {}

    private static final String PREFS = "admin3force_templates";

    public static final String[] KEYS = {
            "confirmacion", "recordatorio", "tasa",
            "tunel_creado", "contrasena"};

    public static final String[] NAMES = {
            "Confirmación de pago",
            "Recordatorio de pago pendiente",
            "Tasa de cambio USDT",
            "Túnel creado · datos de acceso",
            "Contraseña restablecida · datos de acceso"};

    /** Variables de cada plantilla (las del prototipo). */
    public static final String[] VARS = {
            "{mes} {cliente}",
            "",
            "{valor} {fecha} {hora}",
            "{cliente} {tunel}",
            "{cliente} {usuario} {contraseña}"};

    private static final String[] DEFAULTS = {
            "🚀✅ Pago de {mes} recibido. MUCHAS GRACIAS.\n\n"
                    + "> Recuerda recomendarnos con amigos y familiares🫂👥️.",
            "👋 *Hola, ¿vas a continuar con el servicio este mes?*\n\n"
                    + "Han pasado varios días desde tu *fecha de pago "
                    + "correspondiente* y aún se encuentra pendiente el "
                    + "*PAGO DE TU CUENTA*.\n\n"
                    + "Si deseas *CONTINUAR CON EL SERVICIO*, antes de "
                    + "realizar el pago puedes preguntarnos por la *TASA "
                    + "DE CAMBIO ACTUAL* para conocer el importe exacto "
                    + "a pagar en CUP.\n\n"
                    + "⚠️ *Agradecemos que respondas este mensaje para "
                    + "evitar interrupciones en tu servicio.*",
            "💱 *TASA DE CAMBIO ACTUAL*\n"
                    + "💵 *1 USDT = {valor} CUP*\n"
                    + "> 📅 *Fecha:* {fecha}\n"
                    + "> 🕐 *Hora:* {hora}\n"
                    + "🔎 *Verificar tasa de cambio:*\n"
                    + "> https://www.cambiocup.com/",
            "🎉 *¡TU TÚNEL YA FUE CREADO!*\n\n"
                    + "Hola {cliente}, tu túnel *{tunel}* ya está creado "
                    + "y activo ✅\n"
                    + "Descarga tu configuración e impórtala en la app "
                    + "WireGuard para empezar a usarlo.",
            "🔐 *Contraseña restablecida*\n\n"
                    + "Hola {cliente}, tu contraseña fue restablecida.\n\n"
                    + "*Tus datos de acceso:*\n"
                    + "> Usuario: {usuario}\n"
                    + "> Contraseña: {contraseña}\n\n"
                    + "Entra con estos datos en https://3forcevpn.com"};

    public static String get(Context ctx, String key) {
        int i = indexOf(key);
        if (i < 0) return "";
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("tpl_" + key, null);
        return s != null ? s : DEFAULTS[i];
    }

    public static void set(Context ctx, String key, String text) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("tpl_" + key, text).apply();
    }

    public static int indexOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return i;
        }
        return -1;
    }

    /** Sustituye las {llaves} por los valores dados. */
    public static String fill(String text, Map<String, String> values) {
        if (text == null) return "";
        String out = text;
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                out = out.replace("{" + e.getKey() + "}",
                        e.getValue() != null ? e.getValue() : "");
            }
        }
        return out;
    }
}
