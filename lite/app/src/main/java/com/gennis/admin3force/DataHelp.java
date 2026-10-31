package com.gennis.admin3force;

import org.json.JSONObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractores de valores LEGIBLES de los items reales de la API, compartidos
 * por las vistas Cuentas/Túneles y por el diagnóstico completo.
 *
 * Regla de honestidad: si un campo no trae una etiqueta que un humano pueda
 * leer (p.ej. «host» viene como id numérico), se devuelve null y la UI
 * muestra «—»; nunca se inventa una etiqueta.
 */
public final class DataHelp {

    private DataHelp() {}

    /** Primera cadena no vacía entre las claves dadas; null si ninguna. */
    public static String str(JSONObject o, String... keys) {
        if (o == null) return null;
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    /** Nombre grueso del tipo JSON de un valor (para el diagnóstico). */
    public static String typeName(Object v) {
        if (v == null) return "ausente";
        if (v == JSONObject.NULL) return "nulo";
        if (v instanceof JSONObject) return "objeto";
        if (v instanceof org.json.JSONArray) return "arreglo";
        if (v instanceof String) return "cadena";
        if (v instanceof Boolean) return "booleano";
        if (v instanceof Number) return "número";
        return v.getClass().getSimpleName();
    }

    /**
     * Etiqueta legible del host de un túnel: la cadena misma si «host» es
     * texto, o el campo nombre/etiqueta si es un objeto. Null si es un id u
     * otra forma no legible.
     */
    public static String hostLabel(JSONObject item) {
        if (item == null) return null;
        Object h = item.opt("host");
        if (h instanceof String) {
            String s = ((String) h).trim();
            return s.isEmpty() ? null : s;
        }
        if (h instanceof JSONObject) {
            return str((JSONObject) h, "name", "label", "hostname", "title",
                    "display_name", "code");
        }
        return null;
    }

    /** Forma de la clave «host» de un item: tipo y, si es objeto, sus claves. */
    public static String hostShape(JSONObject item) {
        if (item == null || !item.has("host")) return "sin clave «host»";
        Object h = item.opt("host");
        if (h instanceof JSONObject) {
            return "objeto {" + JsonHelp.keyList((JSONObject) h) + "}";
        }
        return typeName(h);
    }

    /** Zona legible de un item («zone» como texto u objeto con nombre). */
    public static String zoneLabel(JSONObject item) {
        if (item == null) return null;
        Object z = item.opt("zone");
        if (z instanceof String) {
            String s = ((String) z).trim();
            return s.isEmpty() ? null : s;
        }
        if (z instanceof JSONObject) {
            return str((JSONObject) z, "name", "label", "title", "display_name");
        }
        return null;
    }

    /**
     * Nombre del propietario de un túnel. «user» puede venir como objeto
     * (usa su username/nombre) o como id: entonces se resuelve contra el mapa
     * id→username construido desde la lista de usuarios. Si no se resuelve,
     * «Usuario #id».
     */
    public static String ownerName(JSONObject tunnel, Map<String, String> idToUsername) {
        if (tunnel == null) return null;
        Object u = tunnel.opt("user");
        if (u instanceof JSONObject) {
            JSONObject o = (JSONObject) u;
            String n = str(o, "username", "full_name", "name");
            if (n != null) return n;
            Object id = o.opt("id");
            if (id != null && id != JSONObject.NULL) {
                String r = lookup(idToUsername, String.valueOf(id));
                return r != null ? r : "Usuario #" + id;
            }
            return null;
        }
        if (u instanceof Number || u instanceof String) {
            String idStr = String.valueOf(u).trim();
            if (idStr.isEmpty() || "null".equals(idStr)) return null;
            String r = lookup(idToUsername, idStr);
            return r != null ? r : "Usuario #" + idStr;
        }
        return null;
    }

    private static String lookup(Map<String, String> map, String id) {
        if (map == null || id == null) return null;
        String v = map.get(id);
        if (v != null) return v;
        // El id puede venir como «12» en un sitio y «12.0» en otro (número JSON).
        try {
            long l = Long.parseLong(id);
            return map.get(String.valueOf(l));
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern ISO = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?)?");

    /** «2026-10-30T14:23:45…» → «30/10/2026». Null si no se reconoce la forma. */
    public static String fmtIsoDate(String iso) {
        if (iso == null) return null;
        Matcher m = ISO.matcher(iso.trim());
        if (!m.find()) return null;
        return m.group(3) + "/" + m.group(2) + "/" + m.group(1);
    }

    /** «2026-10-30T14:23:45…» → «30/10/2026 14:23». Null si no se reconoce. */
    public static String fmtIsoDateTime(String iso) {
        String d = fmtIsoDate(iso);
        if (d == null) return null;
        Matcher m = ISO.matcher(iso.trim());
        if (m.find() && m.group(4) != null) {
            return d + " " + m.group(4) + ":" + m.group(5);
        }
        return d;
    }

    /** Cadena de un campo si es texto no vacío; null en otro caso. */
    public static String fieldStr(JSONObject o, String key) {
        if (o == null) return null;
        Object v = o.opt(key);
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }
}
