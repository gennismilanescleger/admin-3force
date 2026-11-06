package com.gennis.admin3force;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

/**
 * Cliente HTTP mínimo para la API del panel (HttpURLConnection, sin librerías).
 *
 * Convención estándar JWT (la documentación no trae el formato de cabecera ni
 * los campos del refresh — ver API-INSPECTION.md): access en
 * «Authorization: Bearer <token>» y refresh con {"refresh": "<token>"}.
 * Si la API difiere, las respuestas reales quedan en el Diagnóstico y la app
 * lo MUESTRA; nunca se inventa una estructura.
 *
 * Reglas:
 *  - Todo se llama desde hilos de fondo (esto bloquea en red).
 *  - Ante un 401 se intenta el refresh UNA sola vez; si trae tokens nuevos se
 *    guardan (con las claves que la respuesta use de verdad) y se reintenta.
 *  - Si el refresh falla, el resultado marca authFailed y la UI vuelve al
 *    WebView de login (ruta híbrida de la spec).
 */
public class ApiClient {

    public static class Result {
        public int code = 0;          // 0 = sin respuesta (red)
        public String body;           // cuerpo crudo (puede ser null)
        public String error;          // error de red literal (si lo hubo)
        public long ms = 0;
        public String contentType;    // Content-Type de la respuesta, si vino
        public boolean authFailed;    // 401 incluso tras el refresh
        public boolean ambiguous;     // escritura enviada y respuesta perdida:
                                      // el servidor PUDO aplicarla; no reintentar
        public boolean methodOverridden; // PATCH salió como POST +
                                      // X-HTTP-Method-Override (ver rawRequest)
    }

    public static class ItemsResult {
        public final JSONArray items = new JSONArray();
        public Integer count;         // total del sobre, si vino
        public int code = 0;
        public int pages = 0;
        public boolean authFailed;
        public String error;          // si la lista no se pudo leer
    }

    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_PAGES = 12;

    private final SessionStore store;
    // Timeouts cortos para CARGAS (v0.35.0): una carga de fondo
    // sin red no debe colgar 12 s por bloque; las escrituras
    // conservan los valores completos.
    /** Desconexión simulada (v1.5.38): el
     *  interruptor «Desconectar de la web» de
     *  Desarrollo. Con esto activo, NINGUNA
     *  petición sale: rawRequest devuelve el
     *  «sin respuesta» de siempre sin tocar
     *  la red. Lo sincronizan la app (desde
     *  devWebOff) y los motores de fondo. */
    public static volatile boolean SIMULATE_OFFLINE = false;

    private int connectTimeoutMs = CONNECT_TIMEOUT_MS;

    public ApiClient(SessionStore store) {
        this.store = store;
    }

    /** Este cliente es para cargar datos: conectar falla antes. */
    public ApiClient useLoadTimeouts() {
        connectTimeoutMs = 7000;
        return this;
    }

    // ---------------- API pública ----------------

    /** GET autenticado, con registro en el Diagnóstico. */
    public Result get(String label, String url) {
        return request(label, "GET", url, null, true, true);
    }

    /**
     * GET autenticado cuyo registro añade los pares compactos
     * «id=nombre» de TODOS los ítems de la respuesta (v0.8.4). Lo usa
     * «Servidores del panel»: en la ronda 5 el diagnóstico no guardaba
     * los nombres asociados a cada id de la lista y no se pudo cruzar
     * lo tocado en el selector con lo enviado y lo aplicado; con los
     * pares en la misma entrada, la comparación es número a número.
     */
    public Result getWithPairs(String label, String url) {
        return request(label, "GET", url, null, true, true, true);
    }

    /** GET sin autenticación (la tasa), con registro en el Diagnóstico. */
    public Result getPublic(String label, String url) {
        return request(label, "GET", url, null, false, true);
    }

    /** POST autenticado con cuerpo JSON, con registro en el Diagnóstico. */
    public Result post(String label, String url, String jsonBody) {
        return request(label, "POST", url, jsonBody, true, true);
    }

    /**
     * Lee una lista completa siguiendo la paginación del sobre DRF («next»)
     * hasta MAX_PAGES. Registra UNA entrada de diagnóstico por la lista.
     */
    public ItemsResult getAllItems(String label, String url) {
        ItemsResult out = new ItemsResult();
        long start = System.currentTimeMillis();
        String next = url;
        String firstBody = null;
        String lastError = null;
        while (next != null && out.pages < MAX_PAGES) {
            Result r = request(label, "GET", next, null, true, false);
            out.pages++;
            out.code = r.code;
            if (out.pages == 1) firstBody = r.body;
            if (r.authFailed) {
                out.authFailed = true;
                lastError = "HTTP 401 (ni tras el refresh)";
                break;
            }
            if (r.error != null) { lastError = r.error; break; }
            if (r.code >= 400 || r.body == null) {
                lastError = "HTTP " + r.code;
                break;
            }
            Object root = JsonHelp.parse(r.body);
            JSONArray items = JsonHelp.itemsArray(root);
            if (items == null) {
                lastError = "estructura no reconocida";
                break;
            }
            if (out.count == null) out.count = JsonHelp.envelopeCount(root);
            for (int i = 0; i < items.length(); i++) {
                Object item = items.opt(i);
                if (item != null) out.items.put(item);
            }
            next = JsonHelp.nextUrl(root);
        }
        out.error = lastError;
        String detail;
        if (out.authFailed) {
            detail = "sin acceso: " + lastError;
        } else if (lastError != null && out.items.length() == 0) {
            detail = "error: " + lastError + " · " + JsonHelp.describe(firstBody);
        } else {
            detail = JsonHelp.describe(firstBody)
                    + " · páginas leídas: " + out.pages
                    + " · items leídos: " + out.items.length()
                    + (lastError != null ? " · aviso: " + lastError : "");
        }
        ApiDiag.add(label, "GET", url, out.code,
                System.currentTimeMillis() - start, detail);
        return out;
    }

    /** Intenta renovar la sesión con el refresh guardado. true si hay access nuevo. */
    public boolean refreshNow() {
        return tryRefresh() != null;
    }

    /**
     * POST de ESCRITURA (marcar pagado). Ver write(): mismas reglas.
     */
    public Result postWrite(String label, String url, String jsonBody) {
        return write(label, "POST", url, jsonBody);
    }

    /**
     * ESCRITURA genérica (POST/PATCH/DELETE), con reglas distintas a las
     * lecturas — es el patrón de escritura segura de la app:
     *
     *  - SIN reintentos automáticos: si la petición salió y la respuesta se
     *    perdió (error de red, code 0), el servidor PUDO haberla aplicado;
     *    el resultado sale marcado «ambiguous» y quien llama debe recargar
     *    los datos y mostrar el estado real, NUNCA reenviar a ciegas.
     *  - La única re-llamada segura es tras un 401: la autenticación JWT se
     *    rechaza ANTES de ejecutar la vista, así que esa petición no se
     *    aplicó; tras un refresh correcto se reenvía UNA vez.
     *  - El intento queda registrado ENTERO en el Diagnóstico bajo la
     *    etiqueta dada: URL, cuerpo enviado completo y cuerpo de respuesta
     *    literal (recortado a ~1500 caracteres, sin formas de token). Con UN
     *    intento, falle o acierte, se fija el esquema real del endpoint.
     *  - UNA sola ruta por llamada: descubrir rutas con escrituras en cadena
     *    podría aplicar la misma acción dos veces. Si la candidata falla,
     *    el error literal del Diagnóstico manda el ajuste a la siguiente
     *    build.
     */
    public Result write(String label, String method, String url, String jsonBody) {
        return write(label, method, url, jsonBody, false);
    }

    /**
     * Como write(), pero con «enrichState» la entrada del Diagnóstico
     * añade un resumen del objeto respuesta: sus claves de nivel
     * superior y los valores de status / is_active / disabled_date si
     * vienen. Lo usan SOLO deshabilitar/habilitar (v0.8.3) para
     * confirmar en un toque si el campo enviado se aplicó.
     */
    public Result write(String label, String method, String url, String jsonBody,
                        boolean enrichState) {
        return write(label, method, url, jsonBody, enrichState, null, false);
    }

    /**
     * Como write() con enrichState, con DOS anotaciones extra para
     * discriminar el resultado (v0.8.4; lo usa SOLO cambiar de
     * servidor): «extraNote» es un texto que quien llama conoce de
     * antemano (lo seleccionado en la UI) y va primero en la nota; con
     * «enrichHost» se añade al final el host resultante si la
     * respuesta trae el objeto del túnel. En la ronda 5 el POST
     * reassign-host/ respondió 200 pero el túnel aterrizó en otro
     * host, y el diagnóstico no registraba ni el nombre tocado en el
     * selector ni el host de la respuesta: sin esos dos datos no se
     * podía concluir si la API auto-asigna o la app enviaba otro id.
     */
    public Result write(String label, String method, String url, String jsonBody,
                        boolean enrichState, String extraNote, boolean enrichHost) {
        Result r = rawRequest(method, url, jsonBody, true);
        StringBuilder note = new StringBuilder();
        if (extraNote != null) note.append(extraNote).append(" · ");
        note.append("cuerpo enviado: ").append(ApiDiag.scrub(jsonBody));
        if (r.methodOverridden) {
            note.append(" · PATCH no soportado por HttpURLConnection: enviado "
                    + "como POST con X-HTTP-Method-Override: PATCH");
        }
        if (r.error == null && r.code == 401) {
            note.append(" · 1er intento: HTTP 401 (rechazado en autenticación, "
                    + "no aplicado)");
            String newAccess = tryRefresh();
            if (newAccess != null) {
                Result retry = rawRequest(method, url, jsonBody, true);
                retry.ms += r.ms;
                note.append(" · refresh OK → reintento único");
                if (retry.methodOverridden && !r.methodOverridden) {
                    note.append(" · el reintento también usó override de método");
                }
                r = retry;
                if (r.code == 401) r.authFailed = true;
            } else {
                r.authFailed = true;
                note.append(" · el refresh no renovó");
            }
        }
        if (r.error != null) {
            // La petición pudo llegar y aplicarse: prohibido reintentar.
            r.ambiguous = true;
            note.append(" · sin respuesta: ").append(r.error)
                    .append(" · RESPUESTA AMBIGUA: no se reintenta; "
                            + "hay que recargar los datos y mirar el estado real");
        } else {
            note.append(" · respuesta: HTTP ").append(r.code)
                    .append(" · cuerpo: ")
                    .append(ApiDiag.scrub(ApiDiag.truncateRaw(r.body, 1500)));
            if (r.code == 401) r.authFailed = true;
        }
        if (enrichState) appendStateSummary(note, r.body);
        if (enrichHost) appendHostSummary(note, r.body);
        ApiDiag.add(label, method, url, r.code, r.ms, note.toString());
        return r;
    }

    /**
     * Host resultante (v0.8.4): si el cuerpo es el objeto del túnel y
     * trae «host» como objeto con id y nombre, anota «host resultante:
     * <id> <name>». Es el dato que, cruzado con lo seleccionado
     * (extraNote) y con los pares de la lista de servidores, dice si
     * la API aplicó el host pedido en el cambio de servidor.
     */
    private void appendHostSummary(StringBuilder note, String body) {
        if (body == null) return;
        Object root = JsonHelp.parse(body);
        if (!(root instanceof JSONObject)) return;
        Object hv = ((JSONObject) root).opt("host");
        if (!(hv instanceof JSONObject)) return;
        JSONObject host = (JSONObject) hv;
        Object id = host.opt("id");
        Object name = host.opt("name");
        if (id == null || id == JSONObject.NULL
                || name == null || name == JSONObject.NULL) return;
        note.append(" · host resultante: ")
                .append(ApiDiag.scrub(String.valueOf(id))).append(" ")
                .append(ApiDiag.scrub(String.valueOf(name)));
    }

    /**
     * Resumen de estado (v0.8.3): si el cuerpo es un objeto JSON, anota
     * sus claves de nivel superior y los valores de status, is_active y
     * disabled_date que traiga. Nada si no hay cuerpo o no es un objeto.
     */
    private void appendStateSummary(StringBuilder note, String body) {
        if (body == null) return;
        Object root = JsonHelp.parse(body);
        if (!(root instanceof JSONObject)) return;
        JSONObject obj = (JSONObject) root;
        StringBuilder keys = new StringBuilder();
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            if (keys.length() > 0) keys.append(", ");
            keys.append(it.next());
        }
        note.append(" · claves de la respuesta: ").append(keys);
        for (String k : new String[]{"status", "is_active", "disabled_date"}) {
            if (obj.has(k)) {
                Object v = obj.opt(k);
                note.append(" · ").append(k).append("=")
                        .append(v == null || v == JSONObject.NULL ? "null"
                                : ApiDiag.scrub(String.valueOf(v)));
            }
        }
    }

    /**
     * GET crudo autenticado, con el mismo refresh-en-401 de las lecturas,
     * pero SIN registrar en el Diagnóstico: quien llama compone su propia
     * entrada. Lo usa la descarga de configuración, que anota solo tipo de
     * contenido y tamaño — el contenido (claves del cliente) jamás se anota.
     */
    public Result rawGet(String url) {
        Result r = rawRequest("GET", url, null, true);
        if (r.error == null && r.code == 401) {
            String newAccess = tryRefresh();
            if (newAccess != null) {
                Result retry = rawRequest("GET", url, null, true);
                retry.ms += r.ms;
                if (retry.code == 401) retry.authFailed = true;
                return retry;
            }
            r.authFailed = true;
        }
        return r;
    }

    /**
     * OPTIONS crudo autenticado (v0.8.5), con el mismo refresh-en-401 de
     * las lecturas y SIN registrar en el Diagnóstico: quien llama compone
     * su propia entrada. Es una LECTURA de metadatos: el cuerpo trae el
     * esquema que la propia API declara para el recurso (en DRF, el
     * objeto «actions» con los campos escribibles de PUT/PATCH, su tipo,
     * si son de solo lectura y sus valores posibles). Lo usa
     * Deshabilitar/Habilitar para preguntarle a la API qué campo cambia
     * el estado en vez de seguir adivinando nombres.
     */
    public Result rawOptions(String url) {
        Result r = rawRequest("OPTIONS", url, null, true);
        if (r.error == null && r.code == 401) {
            String newAccess = tryRefresh();
            if (newAccess != null) {
                Result retry = rawRequest("OPTIONS", url, null, true);
                retry.ms += r.ms;
                if (retry.code == 401) retry.authFailed = true;
                return retry;
            }
            r.authFailed = true;
        }
        return r;
    }

    // ---------------- Núcleo ----------------

    private Result request(String label, String method, String url, String jsonBody,
                           boolean auth, boolean record) {
        return request(label, method, url, jsonBody, auth, record, false);
    }

    private Result request(String label, String method, String url, String jsonBody,
                           boolean auth, boolean record, boolean withPairs) {
        Result r = rawRequest(method, url, jsonBody, auth);
        String note = null;
        if (auth && r.code == 401) {
            String newAccess = tryRefresh();
            if (newAccess != null) {
                Result retry = rawRequest(method, url, jsonBody, auth);
                retry.ms += r.ms;
                if (retry.code == 401) retry.authFailed = true;
                if (record) recordEntry(label, method, url, retry,
                        "401 inicial → refresh OK → reintento", withPairs);
                return retry;
            }
            r.authFailed = true;
            note = "401 y el refresh no renovó";
        }
        if (record) recordEntry(label, method, url, r, note, withPairs);
        return r;
    }

    private Result rawRequest(String method, String urlStr, String jsonBody, boolean auth) {
        Result r = new Result();
        long start = System.currentTimeMillis();
        if (SIMULATE_OFFLINE) {
            // La petición NO sale (v1.5.38): el
            // resultado es el de «sin respuesta»,
            // para que cada rincón de la app se
            // comporte como sin conexión con el
            // panel durante la prueba.
            r.code = 0;
            r.error = "Sin conexión (simulado)";
            r.ms = System.currentTimeMillis() - start;
            return r;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod(method);
            } catch (java.net.ProtocolException pe) {
                // HttpURLConnection rechaza PATCH («Invalid HTTP method»)
                // ANTES de abrir la conexión: la petición no salió, así que
                // reintentarla como POST con override es seguro y no duplica
                // ninguna escritura. Queda marcado en el resultado.
                if (!"PATCH".equals(method)) throw pe;
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                r.methodOverridden = true;
            }
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (auth) {
                String access = accessToken();
                if (access != null && !access.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + access);
                }
            }
            if (jsonBody != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = jsonBody.getBytes("UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
            }
            r.code = conn.getResponseCode();
            r.contentType = conn.getContentType();
            InputStream in = r.code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            r.body = readAll(in);
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
        } finally {
            if (conn != null) conn.disconnect();
            r.ms = System.currentTimeMillis() - start;
        }
        return r;
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        br.close();
        return sb.toString();
    }

    private void recordEntry(String label, String method, String url, Result r, String note) {
        recordEntry(label, method, url, r, note, false);
    }

    private void recordEntry(String label, String method, String url, Result r,
                             String note, boolean withPairs) {
        String detail;
        if (r.error != null) {
            detail = "sin respuesta: " + r.error;
        } else if (r.code >= 400) {
            detail = "error: " + ApiDiag.scrub(ApiDiag.truncate(r.body, 220));
        } else {
            // Solo claves: describe() nunca imprime valores.
            detail = JsonHelp.describe(r.body);
            if (withPairs) {
                String pairs = idNamePairs(r.body);
                if (pairs != null) detail += " · pares: " + pairs;
            }
        }
        if (note != null) detail += " · " + note;
        ApiDiag.add(label, method, url, r.code, r.ms, detail);
    }

    /**
     * Pares compactos «id=nombre» de todos los ítems de una respuesta de
     * lista (v0.8.4): p. ej. «43=S2-CUBA · 47=S3-CUBA». Es la EXCEPCIÓN
     * deliberada a la regla «describe() nunca imprime valores»: son los
     * nombres públicos de los servidores del panel, y sin ellos el
     * diagnóstico del cambio de servidor no discrimina (ronda 5). null
     * si la respuesta no trae ítems con id y nombre.
     */
    private String idNamePairs(String body) {
        Object root = JsonHelp.parse(body);
        JSONArray items = JsonHelp.itemsArray(root);
        if (items == null || items.length() == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            Object id = o.opt("id");
            if (id == null || id == JSONObject.NULL) continue;
            String name = null;
            for (String k : new String[]{"name", "label", "hostname", "title",
                    "display_name", "code"}) {
                Object v = o.opt(k);
                if (v instanceof String && !((String) v).trim().isEmpty()) {
                    name = ((String) v).trim();
                    break;
                }
            }
            if (name == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(ApiDiag.scrub(String.valueOf(id))).append("=")
                    .append(ApiDiag.scrub(name));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ---------------- Refresh ----------------

    /**
     * POST al endpoint de refresh con el refresh guardado. Si la respuesta
     * trae tokens nuevos, actualiza la sesión cifrada respetando las claves
     * que la respuesta use de verdad y devuelve el access nuevo; si no, null.
     * Queda registrado en el Diagnóstico SOLO con nombres de claves.
     */
    private String tryRefresh() {
        String refresh = refreshToken();
        if (refresh == null || refresh.isEmpty()) {
            ApiDiag.add("Refresh", "POST", PanelConfig.REFRESH_URL, 0, 0,
                    "sin refresh guardado en la sesión");
            return null;
        }
        String body;
        try {
            body = new JSONObject().put("refresh", refresh).toString();
        } catch (Exception e) {
            return null;
        }
        Result r = rawRequest("POST", PanelConfig.REFRESH_URL, body, false);
        recordEntry("Refresh", "POST", PanelConfig.REFRESH_URL, r,
                "petición con campo «refresh»");
        if (r.error != null || r.code < 200 || r.code >= 300 || r.body == null) return null;
        Object root = JsonHelp.parse(r.body);
        if (!(root instanceof JSONObject)) return null;
        String[] access = findJwtField((JSONObject) root, true);
        if (access == null) return null;
        String[] newRefresh = findJwtField((JSONObject) root, false);

        JSONObject session = loadSession();
        if (session == null) return null;
        try {
            session.put("accessToken", access[0]);
            session.put("accessSource", "refresh");
            session.put("accessKey", access[1]);
            if (newRefresh != null) {
                session.put("refreshToken", newRefresh[0]);
                session.put("refreshSource", "refresh");
                session.put("refreshKey", newRefresh[1]);
            }
            session.put("refreshedAt", System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
        store.save(session.toString());
        return access[0];
    }

    /**
     * Busca en el objeto (y un nivel dentro de «data») un valor string que sea
     * un JWT válido. wantAccess=true prefiere claves con «access» y la vida
     * más corta; false prefiere «refresh» y la vida más larga.
     * Devuelve {token, clave} o null.
     */
    private String[] findJwtField(JSONObject obj, boolean wantAccess) {
        String[] fromObj = scanJwtFields(obj, wantAccess);
        if (fromObj != null) return fromObj;
        JSONObject data = obj.optJSONObject("data");
        if (data != null) return scanJwtFields(data, wantAccess);
        return null;
    }

    private String[] scanJwtFields(JSONObject obj, boolean wantAccess) {
        String hinted = null, hintedKey = null;
        String best = null, bestKey = null;
        long bestLife = wantAccess ? Long.MAX_VALUE : -1;
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object v = obj.opt(key);
            if (!(v instanceof String)) continue;
            org.json.JSONObject payload = JwtUtils.decodePayload((String) v);
            if (payload == null) continue;
            String lk = key.toLowerCase(java.util.Locale.ROOT);
            boolean isRefreshKey = lk.contains("refresh");
            boolean isAccessKey = lk.contains("access");
            long life = Long.MAX_VALUE;
            long exp = payload.optLong("exp", -1);
            long iat = payload.optLong("iat", -1);
            if (exp > 0 && iat > 0 && exp > iat) life = exp - iat;
            if (wantAccess) {
                if (isAccessKey && hinted == null) { hinted = (String) v; hintedKey = key; }
                if (!isRefreshKey && life < bestLife) { bestLife = life; best = (String) v; bestKey = key; }
            } else {
                if (isRefreshKey && hinted == null) { hinted = (String) v; hintedKey = key; }
                if (!isAccessKey && life != Long.MAX_VALUE && life > bestLife) {
                    bestLife = life; best = (String) v; bestKey = key;
                }
            }
        }
        if (hinted != null) return new String[]{hinted, hintedKey};
        if (best != null) return new String[]{best, bestKey};
        return null;
    }

    // ---------------- Sesión guardada ----------------

    private JSONObject loadSession() {
        String raw = store.load();
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String accessToken() {
        JSONObject s = loadSession();
        return s != null ? s.optString("accessToken", null) : null;
    }

    private String refreshToken() {
        JSONObject s = loadSession();
        return s != null ? s.optString("refreshToken", null) : null;
    }
}
