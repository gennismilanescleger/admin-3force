package com.gennis.admin3force;

import android.webkit.CookieManager;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captura genérica de la sesión tras un login real en el WebView.
 *
 * No se conoce dónde guarda el panel su JWT (la documentación no trae
 * esquemas), así que se escanea TODO lo accesible:
 *  - localStorage y sessionStorage (vía evaluateJavascript),
 *  - las cookies del dominio (CookieManager),
 * buscando valores con forma de JWT. Lo capturado se devuelve como un JSON
 * listo para guardar cifrado. NUNCA se captura la contraseña: los campos del
 * formulario pertenecen a la página del panel y no se leen.
 */
public class SessionCapture {

    public interface OnFound {
        void onFound(JSONObject session);
    }

    // Vuelca localStorage y sessionStorage completos como un solo JSON.
    private static final String STORAGE_DUMP_JS =
            "(function(){var o={ls:{},ss:{}};"
                    + "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o.ls[k]=localStorage.getItem(k);}}catch(e){}"
                    + "try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);o.ss[k]=sessionStorage.getItem(k);}}catch(e){}"
                    + "return JSON.stringify(o);})()";

    private final WebView webView;

    public SessionCapture(WebView webView) {
        this.webView = webView;
    }

    /**
     * Hace un intento de captura. Llama a onFound con el JSON de la sesión
     * si encuentra al menos un JWT válido; si no, no llama a nada.
     */
    public void attempt(final OnFound onFound) {
        webView.evaluateJavascript(STORAGE_DUMP_JS, rawResult -> {
            LinkedHashMap<String, JwtUtils.Candidate> candidates = new LinkedHashMap<>();

            // 1) localStorage + sessionStorage
            JSONObject dump = parseStorageDump(rawResult);
            if (dump != null) {
                String[][] sections = {{"ls", "localStorage"}, {"ss", "sessionStorage"}};
                for (String[] section : sections) {
                    JSONObject mapObj = dump.optJSONObject(section[0]);
                    if (mapObj == null) continue;
                    Map<String, String> map = new HashMap<>();
                    Iterator<String> keys = mapObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        map.put(k, mapObj.optString(k, ""));
                    }
                    for (JwtUtils.Candidate c : JwtUtils.findInMap(section[1], map)) {
                        if (!candidates.containsKey(c.token)) candidates.put(c.token, c);
                    }
                }
            }

            // 2) Cookies del panel
            CookieManager cookieManager = CookieManager.getInstance();
            String[] cookieUrls = {
                    PanelConfig.BASE_URL,
                    PanelConfig.LOGIN_URL,
                    PanelConfig.BASE_URL + "/dashboard"
            };
            String cookieHeader = "";
            for (String u : cookieUrls) {
                String h = cookieManager.getCookie(u);
                if (h != null && !h.isEmpty()) {
                    if (cookieHeader.isEmpty()) cookieHeader = h;
                    for (JwtUtils.Candidate c : JwtUtils.findInCookies(h)) {
                        if (!candidates.containsKey(c.token)) candidates.put(c.token, c);
                    }
                }
            }

            if (candidates.isEmpty()) return;

            List<JwtUtils.Candidate> list =
                    new java.util.ArrayList<>(candidates.values());
            JwtUtils.Candidate access = JwtUtils.pickAccess(list);
            if (access == null) return;
            JwtUtils.Candidate refresh = JwtUtils.pickRefresh(list, access);

            try {
                JSONArray tokensArr = new JSONArray();
                for (JwtUtils.Candidate c : list) {
                    tokensArr.put(new JSONObject()
                            .put("source", c.source)
                            .put("key", c.key)
                            .put("token", c.token));
                }

                JSONObject session = new JSONObject()
                        .put("panelHost", PanelConfig.HOST)
                        .put("loginUrl", PanelConfig.LOGIN_URL)
                        .put("capturedAt", System.currentTimeMillis())
                        .put("accessToken", access.token)
                        .put("accessSource", access.source)
                        .put("accessKey", access.key)
                        .put("tokens", tokensArr)
                        .put("cookies", cookieHeader);
                if (refresh != null) {
                    session.put("refreshToken", refresh.token);
                    session.put("refreshSource", refresh.source);
                    session.put("refreshKey", refresh.key);
                }
                onFound.onFound(session);
            } catch (Exception e) {
                // JSON de sesión imposible de montar: no se reporta nada.
            }
        });
    }

    /** El resultado de evaluateJavascript llega como string JSON entrecomillado; se desenvuelve. */
    private JSONObject parseStorageDump(String rawResult) {
        if (rawResult == null || rawResult.isEmpty() || rawResult.equals("null")) return null;
        try {
            Object decoded = new JSONTokener(rawResult).nextValue();
            if (!(decoded instanceof String)) return null;
            return new JSONObject((String) decoded);
        } catch (Exception e) {
            return null;
        }
    }
}
