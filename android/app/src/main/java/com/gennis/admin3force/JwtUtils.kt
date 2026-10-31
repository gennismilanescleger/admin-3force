package com.gennis.admin3force

import android.util.Base64
import org.json.JSONObject

/**
 * Utilidades para encontrar y decodificar JWT reales.
 *
 * No se asume ningún nombre de clave ni esquema del panel: se escanea el texto
 * (localStorage, sessionStorage, cookies) buscando valores con forma de JWT
 * (tres segmentos base64url) y se valida que el payload decodifique a un JSON
 * con pinta de JWT (exp / iat / sub / iss).
 */
object JwtUtils {

    private val JWT_REGEX = Regex("""[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{5,}""")

    data class Candidate(
        val source: String,   // "localStorage" | "sessionStorage" | "cookie"
        val key: String,      // nombre de la clave donde apareció (o "" si no aplica)
        val token: String,
        val payload: JSONObject
    ) {
        val exp: Long get() = payload.optLong("exp", -1L)
        val iat: Long get() = payload.optLong("iat", -1L)

        /** Vida total del token en segundos, si se puede calcular. */
        val lifetime: Long
            get() = if (exp > 0 && iat > 0 && exp > iat) exp - iat else Long.MAX_VALUE
    }

    /** Decodifica el payload (segundo segmento) de un JWT. Null si no es válido. */
    fun decodePayload(token: String): JSONObject? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return try {
            var b64 = parts[1].replace('-', '+').replace('_', '/')
            val rem = b64.length % 4
            if (rem > 0) b64 += "=".repeat(4 - rem)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            // Debe tener al menos un claim típico de JWT para aceptarlo.
            if (json.has("exp") || json.has("iat") || json.has("sub") || json.has("iss")) json else null
        } catch (e: Exception) {
            null
        }
    }

    /** Busca todos los JWT válidos dentro de un texto cualquiera. */
    fun findInText(source: String, key: String, text: String?): List<Candidate> {
        if (text.isNullOrEmpty()) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        for (m in JWT_REGEX.findAll(text)) {
            val tok = m.value
            if (out.containsKey(tok)) continue
            val payload = decodePayload(tok) ?: continue
            out[tok] = Candidate(source, key, tok, payload)
        }
        return out.values.toList()
    }

    /** Busca JWT en un mapa clave→valor (localStorage / sessionStorage). */
    fun findInMap(source: String, map: Map<String, String>): List<Candidate> {
        val out = LinkedHashMap<String, Candidate>()
        for ((k, v) in map) {
            // La clave misma podría contener el token en casos raros; se escanea el valor
            // y también "clave=valor" por si el valor es un JSON que lo envuelve.
            for (c in findInText(source, k, v)) out.putIfAbsent(c.token, c)
            for (c in findInText(source, k, k)) out.putIfAbsent(c.token, c)
        }
        return out.values.toList()
    }

    /** Busca JWT en una cabecera de cookies ("k=v; k2=v2"). */
    fun findInCookies(cookieHeader: String?): List<Candidate> {
        if (cookieHeader.isNullOrEmpty()) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        for (pair in cookieHeader.split(";")) {
            val idx = pair.indexOf('=')
            val name = if (idx >= 0) pair.substring(0, idx).trim() else ""
            val value = if (idx >= 0) pair.substring(idx + 1).trim() else pair.trim()
            for (c in findInText("cookie", name, value)) out.putIfAbsent(c.token, c)
        }
        return out.values.toList()
    }

    /**
     * Elige el access token: primero por pista en el nombre de la clave
     * ("access"), y si no, el de vida más corta (el access vive 10 min según
     * la doc del panel; el refresh vive mucho más).
     */
    fun pickAccess(candidates: List<Candidate>): Candidate? {
        if (candidates.isEmpty()) return null
        candidates.firstOrNull {
            val k = it.key.lowercase()
            k.contains("access") && !k.contains("refresh")
        }?.let { return it }
        candidates.firstOrNull {
            val k = it.key.lowercase()
            (k == "token" || k.endsWith("_token") || k.endsWith("-token")) && !k.contains("refresh")
        }?.let { return it }
        return candidates
            .filter { it.exp > 0 }
            .minByOrNull { it.lifetime }
            ?: candidates.first()
    }

    /** Elige el refresh token: por nombre ("refresh") o el de vida más larga distinto del access. */
    fun pickRefresh(candidates: List<Candidate>, access: Candidate?): Candidate? {
        candidates.firstOrNull { it.key.lowercase().contains("refresh") }?.let { return it }
        return candidates
            .filter { it !== access && it.exp > 0 }
            .maxByOrNull { it.lifetime }
    }
}
