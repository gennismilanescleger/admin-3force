package com.gennis.admin3force;

import android.util.Base64;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades para encontrar y decodificar JWT reales.
 *
 * No se asume ningún nombre de clave ni esquema del panel: se escanea el texto
 * (localStorage, sessionStorage, cookies) buscando valores con forma de JWT
 * (tres segmentos base64url) y se valida que el payload decodifique a un JSON
 * con pinta de JWT (exp / iat / sub / iss).
 */
public final class JwtUtils {

    private static final Pattern JWT_REGEX =
            Pattern.compile("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{5,}");

    private JwtUtils() {}

    public static final class Candidate {
        public final String source; // "localStorage" | "sessionStorage" | "cookie"
        public final String key;    // nombre de la clave donde apareció (o "" si no aplica)
        public final String token;
        public final JSONObject payload;

        Candidate(String source, String key, String token, JSONObject payload) {
            this.source = source;
            this.key = key;
            this.token = token;
            this.payload = payload;
        }

        public long exp() { return payload.optLong("exp", -1L); }
        public long iat() { return payload.optLong("iat", -1L); }

        /** Vida total del token en segundos, si se puede calcular. */
        public long lifetime() {
            long e = exp(), i = iat();
            return (e > 0 && i > 0 && e > i) ? e - i : Long.MAX_VALUE;
        }
    }

    /** Decodifica el payload (segundo segmento) de un JWT. Null si no es válido. */
    public static JSONObject decodePayload(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        try {
            String b64 = parts[1].replace('-', '+').replace('_', '/');
            int rem = b64.length() % 4;
            if (rem > 0) {
                StringBuilder sb = new StringBuilder(b64);
                for (int i = 0; i < 4 - rem; i++) sb.append('=');
                b64 = sb.toString();
            }
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            JSONObject json = new JSONObject(new String(bytes, "UTF-8"));
            // Debe tener al menos un claim típico de JWT para aceptarlo.
            if (json.has("exp") || json.has("iat") || json.has("sub") || json.has("iss")) {
                return json;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Busca todos los JWT válidos dentro de un texto cualquiera. */
    public static List<Candidate> findInText(String source, String key, String text) {
        List<Candidate> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        Matcher m = JWT_REGEX.matcher(text);
        while (m.find()) {
            String tok = m.group();
            if (!seen.add(tok)) continue;
            JSONObject payload = decodePayload(tok);
            if (payload != null) out.add(new Candidate(source, key, tok, payload));
        }
        return out;
    }

    /** Busca JWT en un mapa clave→valor (localStorage / sessionStorage). */
    public static List<Candidate> findInMap(String source, Map<String, String> map) {
        LinkedHashMap<String, Candidate> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            // La clave misma podría contener el token en casos raros; se escanea el valor
            // y también "clave=valor" por si el valor es un JSON que lo envuelve.
            for (Candidate c : findInText(source, e.getKey(), e.getValue())) {
                if (!out.containsKey(c.token)) out.put(c.token, c);
            }
            for (Candidate c : findInText(source, e.getKey(), e.getKey())) {
                if (!out.containsKey(c.token)) out.put(c.token, c);
            }
        }
        return new ArrayList<>(out.values());
    }

    /** Busca JWT en una cabecera de cookies ("k=v; k2=v2"). */
    public static List<Candidate> findInCookies(String cookieHeader) {
        LinkedHashMap<String, Candidate> out = new LinkedHashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) return new ArrayList<>(out.values());
        for (String pair : cookieHeader.split(";")) {
            int idx = pair.indexOf('=');
            String name = idx >= 0 ? pair.substring(0, idx).trim() : "";
            String value = idx >= 0 ? pair.substring(idx + 1).trim() : pair.trim();
            for (Candidate c : findInText("cookie", name, value)) {
                if (!out.containsKey(c.token)) out.put(c.token, c);
            }
        }
        return new ArrayList<>(out.values());
    }

    /**
     * Elige el access token: primero por pista en el nombre de la clave
     * ("access"), y si no, el de vida más corta (el access vive 10 min según
     * la doc del panel; el refresh vive mucho más).
     */
    public static Candidate pickAccess(List<Candidate> candidates) {
        if (candidates.isEmpty()) return null;
        for (Candidate c : candidates) {
            String k = c.key.toLowerCase(Locale.ROOT);
            if (k.contains("access") && !k.contains("refresh")) return c;
        }
        for (Candidate c : candidates) {
            String k = c.key.toLowerCase(Locale.ROOT);
            if ((k.equals("token") || k.endsWith("_token") || k.endsWith("-token"))
                    && !k.contains("refresh")) return c;
        }
        Candidate best = null;
        for (Candidate c : candidates) {
            if (c.exp() > 0 && (best == null || c.lifetime() < best.lifetime())) best = c;
        }
        return best != null ? best : candidates.get(0);
    }

    /** Elige el refresh token: por nombre ("refresh") o el de vida más larga distinto del access. */
    public static Candidate pickRefresh(List<Candidate> candidates, Candidate access) {
        for (Candidate c : candidates) {
            if (c.key.toLowerCase(Locale.ROOT).contains("refresh")) return c;
        }
        Candidate best = null;
        for (Candidate c : candidates) {
            if (c != access && c.exp() > 0 && (best == null || c.lifetime() > best.lifetime())) {
                best = c;
            }
        }
        return best;
    }
}
