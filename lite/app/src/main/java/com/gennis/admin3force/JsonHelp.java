package com.gennis.admin3force;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Ayudas de parsing TOLERANTE para las respuestas de la API.
 *
 * Las claves JSON crudas de la API del panel NO están documentadas (ver
 * API-INSPECTION.md), así que todo lo que pasa por aquí acepta varias formas
 * conocidas (sobre DRF {count, results}, {data: [...]}, arreglo pelado…) y,
 * si ninguna coincide, devuelve null en vez de inventar estructura.
 *
 * Nada de aquí imprime valores de negocio: describe() resume solo NOMBRES de
 * claves y conteos, para la pantalla de Diagnóstico.
 */
public final class JsonHelp {

    private JsonHelp() {}

    /** Parsea un cuerpo a JSONObject o JSONArray; null si no es JSON de esos tipos. */
    public static Object parse(String body) {
        if (body == null) return null;
        String t = body.trim();
        try {
            if (t.startsWith("{")) return new JSONObject(t);
            if (t.startsWith("[")) return new JSONArray(t);
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** Nombres de claves de un objeto, separados por comas (solo nombres). */
    public static String keyList(JSONObject obj) {
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(it.next());
        }
        return sb.toString();
    }

    private static final String[] ITEM_KEYS = {
            "results", "data", "items", "rows", "matrix", "records", "list",
            "users", "tunnels", "content", "entries"
    };

    /**
     * Localiza el arreglo de items dentro de una respuesta: raíz arreglo, o
     * una clave conocida con un arreglo (recursivo un nivel si la clave trae
     * un objeto), o el primer valor arreglo de objetos que aparezca.
     */
    public static JSONArray itemsArray(Object root) {
        if (root instanceof JSONArray) return (JSONArray) root;
        if (!(root instanceof JSONObject)) return null;
        JSONObject obj = (JSONObject) root;
        for (String k : ITEM_KEYS) {
            Object v = obj.opt(k);
            if (v instanceof JSONArray) return (JSONArray) v;
            if (v instanceof JSONObject) {
                JSONArray nested = itemsArray(v);
                if (nested != null) return nested;
            }
        }
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            Object v = obj.opt(it.next());
            if (v instanceof JSONArray) {
                JSONArray arr = (JSONArray) v;
                if (arr.length() == 0 || arr.opt(0) instanceof JSONObject) return arr;
            }
        }
        return null;
    }

    /** Total del sobre paginado ({count} o {total}), si viene. */
    public static Integer envelopeCount(Object root) {
        if (root instanceof JSONObject) {
            JSONObject obj = (JSONObject) root;
            String[] keys = {"count", "total"};
            for (String k : keys) {
                Object c = obj.opt(k);
                if (c instanceof Number) return ((Number) c).intValue();
            }
        }
        return null;
    }

    /** URL de la página siguiente (sobre DRF), si viene y no es nula. */
    public static String nextUrl(Object root) {
        if (root instanceof JSONObject) {
            Object n = ((JSONObject) root).opt("next");
            if (n instanceof String) {
                String s = ((String) n).trim();
                if (!s.isEmpty() && !"null".equals(s)) return s;
            }
        }
        return null;
    }

    /**
     * Resumen SEGURO de una respuesta para el diagnóstico: solo nombres de
     * claves, conteos y tipos gruesos. Nunca valores.
     */
    public static String describe(String body) {
        if (body == null || body.trim().isEmpty()) return "sin cuerpo";
        Object root = parse(body);
        if (root == null) return "respuesta no JSON (" + body.length() + " caracteres)";
        if (root instanceof JSONArray) {
            JSONArray arr = (JSONArray) root;
            String s = "arreglo con " + arr.length() + " items";
            if (arr.length() > 0 && arr.opt(0) instanceof JSONObject) {
                s += " · item {" + keyList(arr.optJSONObject(0)) + "}";
            }
            return s;
        }
        JSONObject obj = (JSONObject) root;
        StringBuilder sb = new StringBuilder();
        sb.append("objeto {").append(keyList(obj)).append("}");
        JSONArray items = itemsArray(obj);
        if (items != null) {
            sb.append(" · items: ").append(items.length());
            if (items.length() > 0 && items.opt(0) instanceof JSONObject) {
                sb.append(" · item {").append(keyList(items.optJSONObject(0))).append("}");
            }
        }
        return sb.toString();
    }
}
