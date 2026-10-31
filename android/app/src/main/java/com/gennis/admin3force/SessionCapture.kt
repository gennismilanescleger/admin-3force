package com.gennis.admin3force

import android.webkit.CookieManager
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Datos fijos del panel (de la spec; la URL de login es pública y conocida). */
object PanelConfig {
    const val HOST = "reseller.3forcevpn.com"
    const val BASE_URL = "https://reseller.3forcevpn.com"
    const val LOGIN_URL = "https://reseller.3forcevpn.com/auth/login"
    const val LOGIN_PATH = "/auth/login"
}

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
class SessionCapture(private val webView: WebView) {

    /**
     * Hace un intento de captura. Llama a [onFound] con el JSON de la sesión
     * si encuentra al menos un JWT válido; si no, no llama a nada.
     */
    fun attempt(onFound: (JSONObject) -> Unit) {
        webView.evaluateJavascript(STORAGE_DUMP_JS) { rawResult ->
            val candidates = LinkedHashMap<String, JwtUtils.Candidate>()

            // 1) localStorage + sessionStorage
            parseStorageDump(rawResult)?.let { dump ->
                for (section in listOf("ls" to "localStorage", "ss" to "sessionStorage")) {
                    val mapObj = dump.optJSONObject(section.first) ?: continue
                    val map = HashMap<String, String>()
                    val keys = mapObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = mapObj.optString(k, "")
                    }
                    for (c in JwtUtils.findInMap(section.second, map)) {
                        candidates.putIfAbsent(c.token, c)
                    }
                }
            }

            // 2) Cookies del panel
            val cookieManager = CookieManager.getInstance()
            val cookieUrls = listOf(
                PanelConfig.BASE_URL,
                PanelConfig.LOGIN_URL,
                PanelConfig.BASE_URL + "/dashboard"
            )
            var cookieHeader = ""
            for (u in cookieUrls) {
                val h = cookieManager.getCookie(u)
                if (!h.isNullOrEmpty()) {
                    if (cookieHeader.isEmpty()) cookieHeader = h
                    for (c in JwtUtils.findInCookies(h)) {
                        candidates.putIfAbsent(c.token, c)
                    }
                }
            }

            if (candidates.isEmpty()) return@evaluateJavascript

            val list = candidates.values.toList()
            val access = JwtUtils.pickAccess(list) ?: return@evaluateJavascript
            val refresh = JwtUtils.pickRefresh(list, access)

            val tokensArr = JSONArray()
            for (c in list) {
                tokensArr.put(
                    JSONObject()
                        .put("source", c.source)
                        .put("key", c.key)
                        .put("token", c.token)
                )
            }

            val session = JSONObject()
                .put("panelHost", PanelConfig.HOST)
                .put("loginUrl", PanelConfig.LOGIN_URL)
                .put("capturedAt", System.currentTimeMillis())
                .put("accessToken", access.token)
                .put("accessSource", access.source)
                .put("accessKey", access.key)
                .put("tokens", tokensArr)
                .put("cookies", cookieHeader)
            if (refresh != null) {
                session.put("refreshToken", refresh.token)
                session.put("refreshSource", refresh.source)
                session.put("refreshKey", refresh.key)
            }
            onFound(session)
        }
    }

    /** El resultado de evaluateJavascript llega como string JSON entrecomillado; se desenvuelve. */
    private fun parseStorageDump(rawResult: String?): JSONObject? {
        if (rawResult.isNullOrEmpty() || rawResult == "null") return null
        return try {
            val decoded = JSONTokener(rawResult).nextValue() as? String ?: return null
            JSONObject(decoded)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // Vuelca localStorage y sessionStorage completos como un solo JSON.
        private const val STORAGE_DUMP_JS =
            "(function(){var o={ls:{},ss:{}};" +
                "try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o.ls[k]=localStorage.getItem(k);}}catch(e){}" +
                "try{for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);o.ss[k]=sessionStorage.getItem(k);}}catch(e){}" +
                "return JSON.stringify(o);})()"
    }
}
