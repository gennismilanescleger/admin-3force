package com.gennis.admin3force;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Diagnóstico de la sesión EN TRES BLOQUES copiables por separado (v0.7.1,
 * orden del propietario: «separa los diagnósticos para que no se haga uno
 * tan grande»):
 *
 *  - «Intentos y escrituras»: cabecera mínima + SOLO las entradas de
 *    intento (escrituras, descarga, servidores, sonda de detalle, refresh).
 *    Es el bloque que se pega tras cada ronda de pruebas.
 *  - «Matriz»: solo la matriz fila por fila, con su mes.
 *  - «Diagnóstico general»: app, sesión guardada (solo fuentes y claims
 *    iat/exp — NUNCA valores de tokens), las llamadas RUTINARIAS, el detalle
 *    de la sesión (usuario + stats) y los agregados de usuarios y túneles.
 *
 * Los tres textos salen de los mismos cuerpos privados (sin duplicar la
 * recolección). Los nombres de clientes aparecen SOLO aquí (y en las vistas
 * Cuentas / Túneles), porque es el diagnóstico que el propietario copia
 * voluntariamente.
 */
public final class DiagFull {

    private final String versionName;
    private final String versionCode;
    private final String androidDesc;
    private final JSONObject sessionJson;   // sesión guardada (puede ser null)
    private final String sessionBody;      // respuesta cruda del POST de sesión
    private final JSONArray users;
    private final Integer usersCount;
    private final JSONArray tunnels;       // alcance usado por el Home
    private final String tunnelsScope;     // etiqueta del alcance
    private final JSONArray matrix;
    private final int year;
    private final int month;
    private String smsDebug;   // depuración del parseo de SMS (v0.10.0)

    public DiagFull(String versionName, String versionCode, String androidDesc,
                    JSONObject sessionJson, String sessionBody,
                    JSONArray users, Integer usersCount,
                    JSONArray tunnels, String tunnelsScope,
                    JSONArray matrix, int year, int month) {
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.androidDesc = androidDesc;
        this.sessionJson = sessionJson;
        this.sessionBody = sessionBody;
        this.users = users;
        this.usersCount = usersCount;
        this.tunnels = tunnels;
        this.tunnelsScope = tunnelsScope;
        this.matrix = matrix;
        this.year = year;
        this.month = month;
    }

    /** Sección {título, cuerpo} para pintar en pantalla (sin las llamadas). */
    public String[] sectionApp() {
        return new String[]{"App", appBody()};
    }

    public String[] sectionSavedSession() {
        return new String[]{"Sesión guardada", savedSessionBody()};
    }

    public String[] sectionSessionDetail() {
        return new String[]{"Sesión (detalle)", sessionDetailBody()};
    }

    public String[] sectionUsers() {
        return new String[]{"Usuarios — agregados", usersBody()};
    }

    public String[] sectionTunnels() {
        return new String[]{"Túneles — agregados", tunnelsBody()};
    }

    public String[] sectionMatrix() {
        return new String[]{"Matriz fila por fila", matrixBody()};
    }

    /** Bloque «intentos y escrituras»: cabecera mínima y solo los intentos. */
    public String attemptsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("INTENTOS Y ESCRITURAS — Admin 3FORCE Lite ").append(versionName).append("\n");
        sb.append("==================================================\n");
        sb.append("Generado: ").append(now()).append("\n");
        sb.append("Panel: ").append(PanelConfig.HOST).append("\n");
        sb.append("Sesión: ").append(sessionUserLine()).append("\n\n");
        appendSection(sb, "INTENTOS Y ESCRITURAS", callsBody(true));
        return sb.toString();
    }

    /** Bloque «matriz»: solo la matriz fila por fila, con su mes indicado. */
    public String matrixText() {
        StringBuilder sb = new StringBuilder();
        sb.append("MATRIZ — Admin 3FORCE Lite ").append(versionName).append("\n");
        sb.append("==================================================\n\n");
        appendSection(sb, "MATRIZ FILA POR FILA", matrixBody());
        return sb.toString();
    }

    /**
     * Bloque «general»: app, sesión guardada, llamadas RUTINARIAS (sin los
     * intentos, que van en su bloque), detalle de sesión y agregados.
     * SIN la matriz fila por fila (va en su propio bloque).
     */
    public String generalText() {
        StringBuilder sb = new StringBuilder();
        sb.append("DIAGNÓSTICO GENERAL — Admin 3FORCE Lite ").append(versionName).append("\n");
        sb.append("==================================================\n\n");
        appendSection(sb, "1. APP", appBody());
        appendSection(sb, "2. SESIÓN GUARDADA", savedSessionBody());
        appendSection(sb, "3. LLAMADAS", callsBody(false));
        appendSection(sb, "4. SESIÓN (DETALLE)", sessionDetailBody());
        appendSection(sb, "5. USUARIOS — AGREGADOS", usersBody());
        appendSection(sb, "6. TÚNELES — AGREGADOS", tunnelsBody());
        appendSection(sb, "7. SMS DE PAGO — DEPURACIÓN DEL PARSEO",
                smsDebug != null ? smsDebug
                        : "Sin lectura de SMS todavía (abre Pagos y permite "
                                + "el acceso a los SMS).\n");
        return sb.toString();
    }

    /** Texto de depuración de la etapa SMS (lo fija MainActivity). */
    public void setSmsDebug(String text) {
        this.smsDebug = text;
    }

    private static String now() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    /** «usuario · rol» derivados de la sesión cargada (o «—» si aún no hay). */
    private String sessionUserLine() {
        if (sessionBody == null) return "—";
        String[] ur = HomeStats.sessionUser(sessionBody);
        String name = ur != null && ur.length > 0 ? ur[0] : null;
        String role = ur != null && ur.length > 1 ? ur[1] : null;
        if (name == null && role == null) return "—";
        return (name != null ? name : "—") + (role != null ? " · " + role : "");
    }

    private static void appendSection(StringBuilder sb, String title, String body) {
        sb.append("== ").append(title).append(" ==\n");
        sb.append(body);
        if (!body.endsWith("\n")) sb.append("\n");
        sb.append("\n");
    }

    // ---------------- 1. App ----------------

    private String appBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("Versión: ").append(versionName)
                .append(" (código ").append(versionCode).append(")\n");
        sb.append("Android: ").append(androidDesc).append("\n");
        sb.append("Generado: ").append(now()).append("\n");
        sb.append("Panel: ").append(PanelConfig.HOST).append("\n");
        return sb.toString();
    }

    // ---------------- 2. Sesión guardada ----------------

    private String savedSessionBody() {
        StringBuilder sb = new StringBuilder();
        if (sessionJson == null) {
            sb.append("Sin sesión guardada.\n");
            return sb.toString();
        }
        tokenLine(sb, "Access", "accessToken", "accessSource", "accessKey");
        tokenLine(sb, "Refresh", "refreshToken", "refreshSource", "refreshKey");
        long capturedAt = sessionJson.optLong("capturedAt", 0);
        if (capturedAt > 0) sb.append("Capturada: ").append(fmtMs(capturedAt)).append("\n");
        long refreshedAt = sessionJson.optLong("refreshedAt", 0);
        if (refreshedAt > 0) sb.append("Último refresh aplicado: ")
                .append(fmtMs(refreshedAt)).append("\n");
        return sb.toString();
    }

    /** Presencia + fuente del token y sus claims iat/exp. El valor NUNCA se imprime. */
    private void tokenLine(StringBuilder sb, String label, String tokenKey,
                           String sourceKey, String keyKey) {
        String token = sessionJson.optString(tokenKey, "");
        sb.append(label).append(": ");
        if (token.isEmpty()) {
            sb.append("ausente\n");
            return;
        }
        sb.append("presente");
        String src = sessionJson.optString(sourceKey, "");
        String key = sessionJson.optString(keyKey, "");
        if (!src.isEmpty()) {
            sb.append(" · fuente: ").append(src);
            if (!key.isEmpty()) sb.append(" · ").append(key);
        }
        JSONObject payload = JwtUtils.decodePayload(token);
        if (payload != null) {
            long iat = payload.optLong("iat", -1);
            long exp = payload.optLong("exp", -1);
            if (iat > 0) sb.append("\n  iat: ").append(fmtSec(iat));
            if (exp > 0) sb.append("\n  exp: ").append(fmtSec(exp));
        }
        sb.append("\n");
    }

    // ---------------- 3. Llamadas ----------------

    /**
     * ¿La entrada es un INTENTO (escritura o descubrimiento de una ronda de
     * pruebas) y no una llamada rutinaria de carga? Los intentos son: todas
     * las «Escritura — …», «Descarga de configuración», «Servidores del
     * panel», «Detalle de túnel (sonda)» y «Refresh». Rutinarias: Sesión,
     * Usuarios, Túneles (ambos alcances), Matriz y Tasa.
     */
    private static boolean isAttemptLabel(String label) {
        return label.startsWith("Escritura")
                || "Descarga de configuración".equals(label)
                || "Servidores del panel".equals(label)
                || "Detalle de túnel (sonda)".equals(label)
                || "Refresh".equals(label);
    }

    /** Entradas de ApiDiag filtradas: attempts=true → intentos; false → rutinarias. */
    private String callsBody(boolean attempts) {
        List<ApiDiag.Entry> entries = ApiDiag.entries();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (ApiDiag.Entry e : entries) {
            if (isAttemptLabel(e.label) != attempts) continue;
            shown++;
            sb.append("[").append(e.label).append(" · ").append(e.time).append("]\n");
            sb.append(e.method).append(" ").append(e.url).append("\n");
            sb.append(e.code > 0 ? "HTTP " + e.code + " · " + e.ms + " ms"
                    : "Sin respuesta · " + e.ms + " ms").append("\n");
            sb.append(e.detail).append("\n\n");
        }
        if (shown == 0) {
            return attempts
                    ? "Todavía no hay intentos ni escrituras registradas.\n"
                    : "Todavía no hay llamadas registradas.\n";
        }
        return sb.toString();
    }

    // ---------------- 4. Sesión (detalle) ----------------

    private String sessionDetailBody() {
        if (sessionBody == null) {
            return "Sin respuesta de sesión cargada todavía (el Home aún no cargó).\n";
        }
        StringBuilder sb = new StringBuilder();
        String user = HomeStats.sessionUserDiag(sessionBody);
        sb.append("Usuario: ").append(user != null ? user
                : "sin objeto de usuario identificable").append("\n");
        String stats = HomeStats.sessionStatsDiag(sessionBody);
        sb.append("Stats: ").append(stats != null ? stats
                : "sin objeto «stats» identificable").append("\n");
        String[] ur = HomeStats.sessionUser(sessionBody);
        if (ur[0] != null) sb.append("Nombre derivado: ").append(ur[0]).append("\n");
        if (ur[1] != null) sb.append("Rol derivado: ").append(ur[1]).append("\n");
        return sb.toString();
    }

    // ---------------- 5. Usuarios — agregados ----------------

    private String usersBody() {
        if (users == null) return "Sin datos: el Home aún no cargó la lista de usuarios.\n";
        StringBuilder sb = new StringBuilder();
        sb.append("Total leído: ").append(users.length());
        if (usersCount != null) sb.append(" (sobre: ").append(usersCount).append(")");
        sb.append("\n");
        int actT = 0, actF = 0, actX = 0;
        int conT = 0, sinT = 0, sinCount = 0;
        int conAlias = 0, conNotes = 0;
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.optJSONObject(i);
            if (u == null) continue;
            Object ia = u.opt("is_active");
            if (ia instanceof Boolean) {
                if ((Boolean) ia) actT++; else actF++;
            } else actX++;
            Object tc = u.opt("tunnels_count");
            if (tc instanceof Number) {
                if (((Number) tc).intValue() > 0) conT++; else sinT++;
            } else sinCount++;
            if (DataHelp.fieldStr(u, "alias") != null) conAlias++;
            if (DataHelp.fieldStr(u, "notes") != null) conNotes++;
        }
        sb.append("is_active: true ").append(actT)
                .append(" · false ").append(actF);
        if (actX > 0) sb.append(" · sin booleano ").append(actX);
        sb.append("\n");
        sb.append("Con túneles (tunnels_count>0): ").append(conT)
                .append(" · Sin túneles: ").append(sinT);
        if (sinCount > 0) sb.append(" · sin tunnels_count: ").append(sinCount);
        sb.append("\n");
        sb.append("Con alias: ").append(conAlias).append("\n");
        sb.append("Con notes no vacía: ").append(conNotes).append("\n");
        return sb.toString();
    }

    // ---------------- 6. Túneles — agregados ----------------

    private String tunnelsBody() {
        if (tunnels == null) return "Sin datos: el Home aún no cargó la lista de túneles.\n";
        StringBuilder sb = new StringBuilder();
        sb.append("Alcance: ").append(tunnelsScope != null ? tunnelsScope : "—").append("\n");
        sb.append("Total leído: ").append(tunnels.length()).append("\n");

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        int manT = 0, manF = 0, manX = 0;
        int conNotes = 0, conDisabled = 0;
        Map<String, Integer> hostTypes = new LinkedHashMap<>();
        Map<String, Integer> hostLabels = new LinkedHashMap<>();
        String firstHostShape = null;
        for (int i = 0; i < tunnels.length(); i++) {
            JSONObject t = tunnels.optJSONObject(i);
            if (t == null) continue;
            String st;
            if (!t.has("status") || t.isNull("status")) st = "(sin status)";
            else {
                Object sv = t.opt("status");
                st = sv instanceof String ? (String) sv : String.valueOf(sv);
            }
            inc(statusCounts, st);
            Object mp = t.opt("manual_payment");
            if (mp instanceof Boolean) {
                if ((Boolean) mp) manT++; else manF++;
            } else manX++;
            if (DataHelp.fieldStr(t, "notes") != null) conNotes++;
            if (DataHelp.fieldStr(t, "disabled_date") != null) conDisabled++;
            if (t.has("host") && !t.isNull("host")) {
                inc(hostTypes, DataHelp.typeName(t.opt("host")));
                if (firstHostShape == null) firstHostShape = DataHelp.hostShape(t);
                String label = DataHelp.hostLabel(t);
                if (label != null) inc(hostLabels, label);
            } else {
                inc(hostTypes, "ausente/nulo");
            }
        }
        sb.append("status: ").append(joinCounts(statusCounts)).append("\n");
        sb.append("manual_payment: true ").append(manT).append(" · false ").append(manF);
        if (manX > 0) sb.append(" · sin booleano ").append(manX);
        sb.append("\n");
        sb.append("Con notes no vacía: ").append(conNotes).append("\n");
        sb.append("Con disabled_date: ").append(conDisabled).append("\n");
        sb.append("host — tipos: ").append(joinCounts(hostTypes));
        if (firstHostShape != null) sb.append(" · forma: ").append(firstHostShape);
        sb.append("\n");
        if (!hostLabels.isEmpty()) {
            sb.append("host — etiquetas legibles: ").append(joinCounts(hostLabels)).append("\n");
        } else {
            sb.append("host — sin etiquetas legibles (no se muestran ids como etiqueta)\n");
        }
        return sb.toString();
    }

    // ---------------- 7. Matriz fila por fila ----------------

    private String matrixBody() {
        if (matrix == null) return "Sin datos: el Home aún no cargó la matriz.\n";
        StringBuilder sb = new StringBuilder();
        sb.append("Mes actual: ").append(month).append("/").append(year).append("\n");

        // Cabecera de formas: tipo (+claves si es objeto) de tunnel/user/months.
        JSONObject first = null;
        for (int i = 0; i < matrix.length(); i++) {
            first = matrix.optJSONObject(i);
            if (first != null) break;
        }
        if (first != null) {
            sb.append("«tunnel»: ").append(shapeOf(first.opt("tunnel")));
            sb.append(" · «user»: ").append(shapeOf(first.opt("user")));
            sb.append(" · «months»: ").append(monthsShape(first.opt("months")));
            sb.append("\n");
        }
        sb.append("\n");

        int pagadas = 0, noPagadas = 0, sinCelda = 0, ilegibles = 0;
        for (int i = 0; i < matrix.length(); i++) {
            JSONObject row = matrix.optJSONObject(i);
            if (row == null) {
                sb.append("- (fila no objeto)\n");
                sinCelda++;
                continue;
            }
            sb.append("- ").append(rowTunnelName(row))
                    .append(" · ").append(rowUserName(row))
                    .append(" · meses: ").append(rowMonthsList(row))
                    .append(" · ").append(month).append("/").append(year).append(": ");
            JSONObject cell = currentCell(row);
            if (cell == null && !hasCurrentCell(row)) {
                sb.append("SIN CELDA");
                sinCelda++;
            } else if (cell == null) {
                sb.append("celda presente pero no objeto (")
                        .append(DataHelp.typeName(rawCurrentCell(row))).append(")");
                ilegibles++;
            } else {
                Object ip = cell.opt("is_paid");
                if (ip instanceof Boolean) {
                    sb.append("is_paid=").append(ip);
                    if ((Boolean) ip) pagadas++; else noPagadas++;
                } else if (ip instanceof Number) {
                    boolean paid = ((Number) ip).intValue() != 0;
                    sb.append("is_paid=").append(paid).append(" (número ").append(ip).append(")");
                    if (paid) pagadas++; else noPagadas++;
                } else {
                    sb.append("celda sin «is_paid» legible {")
                            .append(JsonHelp.keyList(cell)).append("}");
                    ilegibles++;
                }
            }
            sb.append("\n");
        }
        sb.append("\nTotales: filas ").append(matrix.length())
                .append(" · pagadas ").append(pagadas)
                .append(" · con celda no pagada ").append(noPagadas)
                .append(" · sin celda ").append(sinCelda);
        if (ilegibles > 0) sb.append(" · celda ilegible ").append(ilegibles);
        sb.append("\n");
        return sb.toString();
    }

    private static String shapeOf(Object v) {
        if (v == null) return "ausente";
        if (v instanceof JSONObject) {
            return "objeto {" + JsonHelp.keyList((JSONObject) v) + "}";
        }
        if (v instanceof Number || v instanceof String) return DataHelp.typeName(v) + " (id)";
        return DataHelp.typeName(v);
    }

    private static String monthsShape(Object months) {
        if (months instanceof JSONArray) {
            JSONArray arr = (JSONArray) months;
            String s = "arreglo de " + arr.length();
            if (arr.length() > 0 && arr.opt(0) instanceof JSONObject) {
                s += " · celda {" + JsonHelp.keyList(arr.optJSONObject(0)) + "}";
            }
            return s;
        }
        return shapeOf(months);
    }

    /** Nombre del túnel de una fila: del objeto «tunnel» si lo trae; si es id, el id. */
    private static String rowTunnelName(JSONObject row) {
        Object t = row.opt("tunnel");
        if (t instanceof JSONObject) {
            JSONObject o = (JSONObject) t;
            String n = DataHelp.str(o, "name", "tunnel_name", "title", "name_for_user");
            if (n != null) return n;
            Object id = o.opt("id");
            return id != null && id != JSONObject.NULL ? "túnel #" + id : "túnel (objeto sin nombre)";
        }
        if (t == null || t == JSONObject.NULL) return "túnel —";
        return "túnel #" + t;
    }

    /** Usuario de una fila: username del objeto «user» si lo trae; si es id, el id. */
    private static String rowUserName(JSONObject row) {
        Object u = row.opt("user");
        if (u instanceof JSONObject) {
            JSONObject o = (JSONObject) u;
            String n = DataHelp.str(o, "username", "full_name", "name");
            if (n != null) return n;
            Object id = o.opt("id");
            return id != null && id != JSONObject.NULL ? "usuario #" + id : "usuario (objeto sin nombre)";
        }
        if (u == null || u == JSONObject.NULL) return "usuario —";
        return "usuario #" + u;
    }

    /** Lista de meses presentes en el arreglo «months» de la fila, formato M/AAAA. */
    private String rowMonthsList(JSONObject row) {
        Object months = row.opt("months");
        if (!(months instanceof JSONArray)) {
            return "(" + DataHelp.typeName(months) + ")";
        }
        JSONArray arr = (JSONArray) months;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject cell = arr.optJSONObject(i);
            if (cell == null) continue;
            Object m = cell.opt("month");
            Object y = cell.opt("year");
            if (m instanceof Number && y instanceof Number) {
                parts.add(((Number) m).intValue() + "/" + ((Number) y).intValue());
            }
        }
        return parts.isEmpty() ? "(sin celdas con month/year)" : join(parts, ", ");
    }

    /** Celda cruda del mes actual dentro del arreglo «months» (o null). */
    private Object rawCurrentCell(JSONObject row) {
        Object months = row.opt("months");
        if (!(months instanceof JSONArray)) return null;
        JSONArray arr = (JSONArray) months;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject cell = arr.optJSONObject(i);
            if (cell == null) continue;
            Object m = cell.opt("month");
            Object y = cell.opt("year");
            if (m instanceof Number && y instanceof Number
                    && ((Number) m).intValue() == month
                    && ((Number) y).intValue() == year) {
                return cell;
            }
        }
        return null;
    }

    private boolean hasCurrentCell(JSONObject row) {
        return rawCurrentCell(row) != null;
    }

    private JSONObject currentCell(JSONObject row) {
        Object c = rawCurrentCell(row);
        return c instanceof JSONObject ? (JSONObject) c : null;
    }

    // ---------------- Utilidades ----------------

    private static void inc(Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        map.put(key, v == null ? 1 : v + 1);
    }

    private static String joinCounts(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getKey()).append(" ×").append(e.getValue());
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private static String fmtSec(long epochSec) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(epochSec * 1000));
    }

    private static String fmtMs(long epochMs) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(epochMs));
    }
}
