package com.gennis.admin3force;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Derivaciones del Home a partir de las respuestas REALES de la API.
 *
 * Parsing tolerante pero honesto: cada conteo se deriva solo si el campo que
 * lo sostiene se identifica en TODOS los items; si no, el valor queda null
 * (la UI muestra «—») y la nota explica por qué, para leerla en el
 * Diagnóstico. Cero números inventados.
 */
public final class HomeStats {

    private HomeStats() {}

    public static class UsersStats {
        public Integer total;
        public Integer conTuneles;
        public Integer sinTuneles;
        public String note = "";
    }

    public static class TunnelsStats {
        public Integer total;
        public Integer activos;
        public Integer inactivos;
        public Integer automaticos;
        public Integer manuales;
        public String note = "";
    }

    public static class MatrixStats {
        public Integer filas;
        public Integer pagados;
        public Integer pendientes;
        public Integer sinRegistro; // filas sin celda/objeto del mes actual
        public String note = "";
    }

    // ---------------- Sesión ----------------

    /** Objeto de usuario localizado dentro de la respuesta de sesión. */
    private static class UserObj {
        JSONObject obj;
        String path;
    }

    /**
     * Localiza el objeto que representa al usuario dentro de la respuesta de
     * sesión: la raíz, o anidado en «user» / «requestData» / «data» (y el
     * «user» dentro de estos dos últimos). Gana el candidato con más señales
     * de identidad (username > full_name > name/sub/email). Devuelve también
     * la ruta («raíz», «user», «requestData.user»…) para el Diagnóstico.
     */
    private static UserObj findUserObject(JSONObject root) {
        if (root == null) return null;
        JSONObject requestData = root.optJSONObject("requestData");
        JSONObject data = root.optJSONObject("data");
        JSONObject[] candidates = {
                root,
                root.optJSONObject("user"),
                requestData,
                requestData != null ? requestData.optJSONObject("user") : null,
                data,
                data != null ? data.optJSONObject("user") : null
        };
        String[] paths = {"raíz", "user", "requestData", "requestData.user",
                "data", "data.user"};
        UserObj best = null;
        int bestScore = 0;
        for (int i = 0; i < candidates.length; i++) {
            JSONObject c = candidates[i];
            if (c == null) continue;
            int score = 0;
            if (c.has("username")) score = 3;
            else if (c.has("full_name")) score = 2;
            else if (c.has("name") || c.has("sub") || c.has("email")) score = 1;
            if (score > bestScore) {
                bestScore = score;
                best = new UserObj();
                best.obj = c;
                best.path = paths[i];
            }
        }
        return best;
    }

    /**
     * {nombre, rol} desde la respuesta de sesión (POST, verificado en la
     * prueba real). Nombre: username o full_name del objeto usuario. Rol:
     * campo literal «role»/«rol» si existe; si no, los booleanos del item de
     * usuario (is_superuser → «Administración», is_reseller → «Reseller»,
     * is_staff → «Staff»). Null en lo que no se derive de verdad.
     */
    public static String[] sessionUser(String body) {
        String[] out = {null, null};
        Object root = JsonHelp.parse(body);
        if (!(root instanceof JSONObject)) return out;
        UserObj uo = findUserObject((JSONObject) root);
        if (uo == null) return out;
        out[0] = firstString(uo.obj, "username", "full_name", "name",
                "preferred_username", "email", "sub");
        String role = firstString(uo.obj, "role", "rol");
        if (role != null && ("bearer".equalsIgnoreCase(role)
                || "jwt".equalsIgnoreCase(role))) {
            role = null; // valor técnico, no un rol
        }
        if (role == null) {
            if (Boolean.TRUE.equals(uo.obj.opt("is_superuser"))) role = "Administración";
            else if (Boolean.TRUE.equals(uo.obj.opt("is_reseller"))) role = "Reseller";
            else if (Boolean.TRUE.equals(uo.obj.opt("is_staff"))) role = "Staff";
        }
        out[1] = role;
        return out;
    }

    /**
     * Línea para el Diagnóstico: claves del objeto «stats» de la respuesta de
     * sesión y los VALORES de sus campos numéricos/booleanos (son contadores
     * propios del negocio, p.ej. los del dashboard). Null si no hubo respuesta
     * JSON objeto.
     */
    public static String sessionStatsDiag(String body) {
        Object root = JsonHelp.parse(body);
        if (!(root instanceof JSONObject)) return null;
        JSONObject stats = ((JSONObject) root).optJSONObject("stats");
        if (stats == null) {
            JSONObject data = ((JSONObject) root).optJSONObject("data");
            if (data != null) stats = data.optJSONObject("stats");
        }
        if (stats == null) return "sin objeto «stats» en la respuesta de sesión";
        StringBuilder sb = new StringBuilder();
        sb.append("stats {").append(JsonHelp.keyList(stats)).append("}");
        StringBuilder vals = new StringBuilder();
        Iterator<String> it = stats.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = stats.opt(k);
            if (v instanceof Number || v instanceof Boolean) {
                if (vals.length() > 0) vals.append(", ");
                vals.append(k).append("=").append(v);
            }
        }
        if (vals.length() > 0) sb.append(" · valores: ").append(vals);
        return sb.toString();
    }

    /**
     * Línea para el Diagnóstico: ruta y claves del objeto de usuario que la
     * respuesta de sesión devolvió (sin valores). Null si no hubo respuesta
     * JSON objeto.
     */
    public static String sessionUserDiag(String body) {
        Object root = JsonHelp.parse(body);
        if (!(root instanceof JSONObject)) return null;
        UserObj uo = findUserObject((JSONObject) root);
        if (uo == null) return "sin objeto de usuario identificable en la respuesta";
        return "objeto usuario en «" + uo.path + "» {"
                + JsonHelp.keyList(uo.obj) + "}";
    }

    private static String firstString(JSONObject obj, String... keys) {
        for (String k : keys) {
            Object v = obj.opt(k);
            if (v instanceof String && !((String) v).trim().isEmpty()) return (String) v;
        }
        return null;
    }

    // ---------------- Usuarios / Cuentas ----------------

    private static final String[] TUNNEL_COUNT_KEYS = {
            "tunnels_count", "tunnel_count", "tunnelsCount", "num_tunnels",
            "total_tunnels", "tunnels_total", "count_tunnels", "n_tunnels",
            "tunnels_number"
    };

    public static UsersStats users(JSONArray items, Integer envelopeTotal) {
        UsersStats s = new UsersStats();
        s.total = envelopeTotal != null ? envelopeTotal : items.length();
        if (items.length() == 0) {
            s.note = "lista de usuarios vacía";
            return s;
        }
        int con = 0, sin = 0;
        String usedKey = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) { s.note = "hay items que no son objeto"; return s; }
            Integer count = tunnelCount(item);
            if (count == null) {
                s.note = "sin campo de conteo de túneles identificable en los items";
                return s;
            }
            if (usedKey == null) usedKey = lastTunnelCountKey;
            if (count > 0) con++; else sin++;
        }
        s.conTuneles = con;
        s.sinTuneles = sin;
        s.note = "conteo de túneles por la clave «" + usedKey + "»";
        return s;
    }

    private static String lastTunnelCountKey;

    private static Integer tunnelCount(JSONObject item) {
        lastTunnelCountKey = null;
        for (String k : TUNNEL_COUNT_KEYS) {
            Object v = item.opt(k);
            if (v instanceof Number) {
                lastTunnelCountKey = k;
                return ((Number) v).intValue();
            }
        }
        Object t = item.opt("tunnels");
        if (t instanceof JSONArray) {
            lastTunnelCountKey = "tunnels (arreglo)";
            return ((JSONArray) t).length();
        }
        if (t instanceof Number) {
            lastTunnelCountKey = "tunnels";
            return ((Number) t).intValue();
        }
        if (t instanceof JSONObject) {
            Integer c = JsonHelp.envelopeCount(t);
            if (c != null) {
                lastTunnelCountKey = "tunnels.count";
                return c;
            }
        }
        return null;
    }

    // ---------------- Túneles ----------------

    private static final String[] STATUS_KEYS = {
            "status", "state", "estado", "is_active", "isActive", "active",
            "enabled", "online"
    };

    private static final Set<String> ACTIVE_WORDS = new HashSet<>();
    private static final Set<String> INACTIVE_WORDS = new HashSet<>();
    static {
        String[] a = {"ACTIVO", "ACTIVA", "ACTIVE", "ONLINE", "TRUE", "ON",
                "ENABLED", "HABILITADO", "RUNNING", "UP", "1"};
        String[] i = {"INACTIVO", "INACTIVA", "INACTIVE", "OFFLINE", "FALSE",
                "OFF", "DISABLED", "DESHABILITADO", "DESACTIVADO", "EXPIRED",
                "EXPIRADO", "VENCIDO", "SUSPENDED", "SUSPENDIDO", "0"};
        for (String w : a) ACTIVE_WORDS.add(w);
        for (String w : i) INACTIVE_WORDS.add(w);
    }

    /**
     * Estado de UN túnel con el mismo mapeo que usa el Home: 1 = activo,
     * 0 = inactivo, -1 = sin campo de estado reconocible. Lo reutiliza la
     * vista Túneles para sus filtros/grupos (mismo criterio, mismo resultado).
     */
    public static int statusOf(JSONObject item) {
        if (item == null) return -1;
        // v0.8.5: una fecha de deshabilitado/borrado NO vacía manda sobre
        // el «status» de salida: si el panel marca el túnel con
        // disabled_date (o deleted_at), está inactivo aunque el campo
        // status siga diciendo «active» (es un valor derivado que ya se
        // vio desactualizado frente al estado real en rondas anteriores).
        if (hasNonEmpty(item, "disabled_date") || hasNonEmpty(item, "deleted_at")) {
            return 0;
        }
        for (String k : STATUS_KEYS) {
            if (item.has(k) && !item.isNull(k)) return mapStatusValue(item.opt(k));
        }
        return -1;
    }

    /** ¿La clave trae una cadena no vacía? (marcas de fecha reales). */
    private static boolean hasNonEmpty(JSONObject item, String key) {
        Object v = item.opt(key);
        return v instanceof String && !((String) v).trim().isEmpty();
    }

    /** 1 = activo, 0 = inactivo, -1 = no reconocido. */
    private static int mapStatusValue(Object v) {
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue() != 0 ? 1 : 0;
        if (v instanceof String) {
            String w = ((String) v).trim().toUpperCase(Locale.ROOT);
            if (ACTIVE_WORDS.contains(w)) return 1;
            if (INACTIVE_WORDS.contains(w)) return 0;
        }
        return -1;
    }

    public static TunnelsStats tunnels(JSONArray items, Integer envelopeTotal) {
        TunnelsStats s = new TunnelsStats();
        s.total = envelopeTotal != null ? envelopeTotal : items.length();
        if (items.length() == 0) {
            s.note = "lista de túneles vacía";
            return s;
        }
        int act = 0, inact = 0, otros = 0;
        String usedKey = null;
        boolean statusOk = true;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) { statusOk = false; break; }
            // v0.8.5: el mismo criterio que statusOf (disabled_date /
            // deleted_at mandan sobre el status de salida), para que el
            // conteo del Home y los grupos de la vista Túneles coincidan.
            boolean markedOff = hasNonEmpty(item, "disabled_date")
                    || hasNonEmpty(item, "deleted_at");
            Object v = null;
            String foundKey = null;
            for (String k : STATUS_KEYS) {
                if (item.has(k) && !item.isNull(k)) { v = item.opt(k); foundKey = k; break; }
            }
            if (foundKey == null && !markedOff) { statusOk = false; break; }
            if (usedKey == null) usedKey = markedOff ? "disabled_date" : foundKey;
            int m = statusOf(item);
            if (m == 1) act++;
            else if (m == 0) inact++;
            else otros++;
        }
        if (statusOk) {
            s.activos = act;
            s.inactivos = inact;
            s.note = "estado por la clave «" + usedKey + "»"
                    + (otros > 0 ? " · " + otros + " con valor no reconocido" : "");
        } else {
            s.note = "sin campo de estado identificable en todos los túneles";
        }
        // Split manual/automático por el booleano REAL «manual_payment»
        // (confirmado en el item de túnel, prueba del 2026-10-30): solo se
        // deriva si TODOS los items lo traen como booleano; si falta en
        // alguno, ambos conteos quedan null y la UI muestra «—».
        int[] split = manualSplit(items);
        if (split != null) {
            s.manuales = split[0];
            s.automaticos = split[1];
            s.note += (s.note.isEmpty() ? "" : " · ")
                    + "split manual/automático por «manual_payment»";
        } else {
            s.note += (s.note.isEmpty() ? "" : " · ")
                    + "automáticos no derivables (falta «manual_payment» booleano en algún túnel)";
        }
        return s;
    }

    /** {manuales, automáticos} por «manual_payment»; null si falta en alguno. */
    private static int[] manualSplit(JSONArray items) {
        int man = 0, auto = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) return null;
            Object v = item.opt("manual_payment");
            if (!(v instanceof Boolean)) return null;
            if ((Boolean) v) man++; else auto++;
        }
        return new int[]{man, auto};
    }

    // ---------------- Matriz de facturación manual ----------------

    private static final String[] MONTH_ES = {
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final String[] MONTH_EN = {
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final String[] MONTH_ES_FULL = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private static final Set<String> PAID_WORDS = new HashSet<>();
    private static final Set<String> PENDING_WORDS = new HashSet<>();
    static {
        String[] p = {"PAGADO", "PAGADA", "PAID", "PAGO", "TRUE", "SI", "SÍ",
                "YES", "OK", "COMPLETO", "COMPLETADO", "COBRADO", "DONE",
                "SETTLED", "1"};
        String[] q = {"PENDIENTE", "PENDING", "UNPAID", "NO PAGADO",
                "NOPAGADO", "DEBE", "DEUDA", "FALSE", "POR PAGAR", "IMPAGO",
                "VENCIDO", "0"};
        for (String w : p) PAID_WORDS.add(w);
        for (String w : q) PENDING_WORDS.add(w);
    }

    /** 1 = pagado, 0 = pendiente, -1 = no reconocido. */
    private static int mapCellValue(Object v) {
        if (v == null || v == JSONObject.NULL) return -1;
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue() != 0 ? 1 : 0;
        if (v instanceof String) {
            String w = ((String) v).trim().toUpperCase(Locale.ROOT);
            if (w.isEmpty()) return -1;
            if (PAID_WORDS.contains(w)) return 1;
            if (PENDING_WORDS.contains(w)) return 0;
            return -1;
        }
        if (v instanceof JSONObject) {
            JSONObject cell = (JSONObject) v;
            String[] boolKeys = {"paid", "pagado", "is_paid", "isPaid", "has_paid"};
            for (String k : boolKeys) {
                Object b = cell.opt(k);
                if (b instanceof Boolean) return (Boolean) b ? 1 : 0;
                if (b instanceof Number) return ((Number) b).intValue() != 0 ? 1 : 0;
            }
            String[] strKeys = {"status", "state", "estado", "payment_status", "paid_status"};
            for (String k : strKeys) {
                Object sv = cell.opt(k);
                if (sv instanceof String) {
                    int m = mapCellValue(sv);
                    if (m != -1) return m;
                }
            }
            String[] dateKeys = {"paid_at", "paidAt", "marked_at", "markedAt",
                    "fecha_pago", "payment_date", "date_paid"};
            for (String k : dateKeys) {
                Object d = cell.opt(k);
                if (d instanceof String && !((String) d).trim().isEmpty()) return 1;
            }
        }
        return -1;
    }

    private static boolean monthMatches(Object v, int year, int month) {
        if (v instanceof Number) return ((Number) v).intValue() == month;
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.equals(String.valueOf(month))) return true;
            // Formas «2026-10» / «10/2026»: basta que el componente mensual coincida.
            String mm = (month < 10 ? "0" : "") + month;
            return s.equals(year + "-" + mm) || s.equals(mm + "/" + year)
                    || s.endsWith("-" + mm) || s.endsWith("/" + mm);
        }
        return false;
    }

    /** Claves-celda candidatas para un mes dentro de una fila. */
    private static Set<String> monthKeySet(int year, int month) {
        Set<String> set = new HashSet<>();
        String mm = (month < 10 ? "0" : "") + month;
        set.add(String.valueOf(month));
        set.add(mm);
        set.add(year + "-" + mm);
        set.add(year + "-" + month);
        set.add(mm + "/" + year);
        set.add(mm + "-" + year);
        set.add(month + "-" + year);
        set.add(MONTH_ES[month - 1]);
        set.add(MONTH_EN[month - 1]);
        set.add(MONTH_ES_FULL[month - 1]);
        return set;
    }

    /** Busca la celda del mes en una fila; devuelve su valor crudo o null. */
    private static Object findMonthCell(JSONObject row, int year, int month) {
        Set<String> keys = monthKeySet(year, month);
        // 1) Claves directas en la fila.
        Iterator<String> it = row.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (keys.contains(k.toLowerCase(Locale.ROOT))) return row.opt(k);
        }
        // 2) Objeto anidado indexado por mes.
        String[] nestKeys = {"months", "meses", "payments", "pagos", "billing",
                "facturacion", "cells", "celdas"};
        for (String nk : nestKeys) {
            Object nested = row.opt(nk);
            if (nested instanceof JSONObject) {
                JSONObject no = (JSONObject) nested;
                Iterator<String> nit = no.keys();
                while (nit.hasNext()) {
                    String k = nit.next();
                    if (keys.contains(k.toLowerCase(Locale.ROOT))) return no.opt(k);
                }
            }
        }
        // 3) Arreglo de celdas/meses.
        for (String nk : nestKeys) {
            Object nested = row.opt(nk);
            if (!(nested instanceof JSONArray)) continue;
            JSONArray arr = (JSONArray) nested;
            boolean anyMonthKey = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject cell = arr.optJSONObject(i);
                if (cell == null) continue;
                String[] mk = {"month", "mes", "month_number", "monthNumber",
                        "period", "periodo"};
                for (String k : mk) {
                    if (cell.has(k)) {
                        anyMonthKey = true;
                        if (monthMatches(cell.opt(k), year, month)) return cell;
                    }
                }
            }
            // Sin claves de mes en ninguna celda: si hay 12, es posicional.
            if (!anyMonthKey && arr.length() == 12) {
                return arr.opt(month - 1);
            }
        }
        return null;
    }

    /**
     * ¿La celda trae algún marcador de estado explícito (aunque no sea
     * «pagado»)? Booleano/número/cadena no vacía, u objeto con alguna clave
     * de estado conocida con valor no nulo.
     */
    private static boolean hasExplicitState(Object cell) {
        if (cell instanceof Boolean || cell instanceof Number) return true;
        if (cell instanceof String) return !((String) cell).trim().isEmpty();
        if (cell instanceof JSONObject) {
            JSONObject o = (JSONObject) cell;
            String[] keys = {"paid", "pagado", "is_paid", "isPaid", "has_paid",
                    "status", "state", "estado", "payment_status", "paid_status"};
            for (String k : keys) {
                if (o.has(k) && !o.isNull(k)) return true;
            }
        }
        return false;
    }

    /**
     * Regla de conteo CONFIRMADA con nombres el 2026-10-31 (diagnóstico
     * completo de la v0.4.0 interpretado fila por fila): la celda de un mes
     * existe solo si ese mes quedó registrado en el panel; por tanto, para el
     * mes actual, fila SIN celda = PENDIENTE. Pendientes del mes = celdas del
     * mes en estado explícito no pagado + filas sin celda del mes. Octubre
     * 2026 real: Pagados 27 · Pendientes 5. Una celda presente pero opaca
     * (sin marcador de estado) sigue sin contar y queda anotada.
     */
    public static MatrixStats matrix(JSONArray rows, int year, int month) {
        MatrixStats s = new MatrixStats();
        s.filas = rows.length();
        if (rows.length() == 0) {
            s.note = "matriz vacía";
            return s;
        }
        int pag = 0, pen = 0, sinCelda = 0, noRecon = 0, conCelda = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) { sinCelda++; continue; }
            Object cell = findMonthCell(row, year, month);
            if (cell == null || cell == JSONObject.NULL) { sinCelda++; continue; }
            conCelda++;
            int m = mapCellValue(cell);
            if (m == 1) pag++;
            else if (m == 0) pen++;
            else if (hasExplicitState(cell)) pen++;
            else noRecon++;
        }
        if (conCelda > 0 || noRecon > 0) {
            s.pagados = pag;
            s.pendientes = pen + sinCelda; // regla confirmada: sin celda = pendiente
        }
        s.sinRegistro = sinCelda;
        s.note = "celdas del mes: " + pag + " pagadas · " + pen + " pendientes con celda"
                + " · sin celda del mes: " + sinCelda
                + " (pendientes por la regla confirmada el 31/10)"
                + (noRecon > 0 ? " · celdas del mes no reconocidas: " + noRecon : "")
                + (conCelda == 0 ? " · ninguna fila trae celda del mes" : "");
        return s;
    }

    /**
     * Forma segura de una celda para el Diagnóstico: tipo y, si es objeto,
     * sus claves + el valor SOLO de campos booleanos o de estado corto
     * (paid/status/state…). Nunca importes ni datos de clientes.
     */
    private static String describeCellShape(Object cell) {
        if (cell == null || cell == JSONObject.NULL) return "nula";
        if (cell instanceof Boolean) return "booleano " + cell;
        if (cell instanceof Number) return "número " + cell;
        if (cell instanceof String) {
            String s = ((String) cell).trim();
            return s.length() <= 24 ? "cadena «" + s + "»"
                    : "cadena larga (" + s.length() + " caracteres)";
        }
        if (cell instanceof JSONArray) return "arreglo de " + ((JSONArray) cell).length();
        if (cell instanceof JSONObject) {
            JSONObject o = (JSONObject) cell;
            StringBuilder sb = new StringBuilder();
            sb.append("objeto {").append(JsonHelp.keyList(o)).append("}");
            String[] stateKeys = {"paid", "pagado", "is_paid", "isPaid",
                    "has_paid", "status", "state", "estado", "payment_status",
                    "paid_status"};
            for (String k : stateKeys) {
                if (!o.has(k) || o.isNull(k)) continue;
                Object v = o.opt(k);
                if (v instanceof Boolean || v instanceof Number) {
                    sb.append(" · ").append(k).append("=").append(v);
                } else if (v instanceof String && ((String) v).trim().length() <= 24) {
                    sb.append(" · ").append(k).append("=«")
                            .append(((String) v).trim()).append("»");
                }
            }
            return sb.toString();
        }
        return cell.getClass().getSimpleName();
    }

    /**
     * Línea de Diagnóstico de la Matriz: (a) las claves del objeto «months»
     * de la primera fila que lo traiga, tal como vengan; (b) la forma de una
     * celda del mes actual reconocida como pagada y de una NO reconocida como
     * pagada, para fijar la forma real de la celda no pagada.
     */
    public static String matrixDiag(JSONArray rows, int year, int month) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            Object months = row.opt("months");
            if (months instanceof JSONObject) {
                sb.append("«months» de la 1ª fila: {")
                        .append(JsonHelp.keyList((JSONObject) months)).append("}");
                break;
            }
            if (months instanceof JSONArray) {
                sb.append("«months» de la 1ª fila: arreglo de ")
                        .append(((JSONArray) months).length());
                break;
            }
        }
        Object paidCell = null, otherCell = null;
        for (int i = 0; i < rows.length() && (paidCell == null || otherCell == null); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            Object cell = findMonthCell(row, year, month);
            if (cell == null || cell == JSONObject.NULL) continue;
            if (mapCellValue(cell) == 1) {
                if (paidCell == null) paidCell = cell;
            } else if (otherCell == null) {
                otherCell = cell;
            }
        }
        if (paidCell != null) {
            sb.append(" · celda pagada del mes: ").append(describeCellShape(paidCell));
        }
        if (otherCell != null) {
            sb.append(" · celda del mes NO reconocida como pagada: ")
                    .append(describeCellShape(otherCell));
        }
        return sb.toString();
    }

    // ---------------- Pagos (vista Pagos, desde la matriz) ----------------

    /** «Octubre 2026» para un mes/año dados. */
    public static String monthLabel(int year, int month) {
        if (month < 1 || month > 12) return month + "/" + year;
        String n = MONTH_ES_FULL[month - 1];
        return Character.toUpperCase(n.charAt(0)) + n.substring(1) + " " + year;
    }

    /**
     * Una entrada de la vista Pagos: un túnel y un mes, con su estado según
     * la matriz real. `noCell` = la fila no trae celda de ese mes (solo se
     * genera para el mes actual: por la regla confirmada, es un pendiente).
     * notes/markedAt/markedBy vienen de la celda cuando existe.
     */
    public static class PayEntry {
        public long tunnelId = -1;
        public String tunnelName;
        public String userName;
        public int month;
        public int year;
        public boolean paid;
        public boolean noCell;
        public String notes;
        public String markedAt;
        public String markedBy;
    }

    private static JSONArray monthsArray(JSONObject row) {
        Object m = row.opt("months");
        return m instanceof JSONArray ? (JSONArray) m : null;
    }

    private static Integer cellInt(JSONObject cell, String key) {
        Object v = cell.opt(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    /** Valor de una celda como texto mostrable (cadena o número); null si no. */
    private static String cellText(JSONObject cell, String key) {
        Object v = cell.opt(key);
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.isEmpty() ? null : s;
        }
        if (v instanceof Number) return String.valueOf(v);
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            return firstString(o, "username", "full_name", "name");
        }
        return null;
    }

    private static PayEntry baseEntry(JSONObject row, int month, int year) {
        PayEntry e = new PayEntry();
        e.month = month;
        e.year = year;
        Object t = row.opt("tunnel");
        if (t instanceof JSONObject) {
            JSONObject o = (JSONObject) t;
            e.tunnelName = firstString(o, "name", "tunnel_name", "title", "name_for_user");
            Object id = o.opt("id");
            if (id instanceof Number) e.tunnelId = ((Number) id).longValue();
            else if (id instanceof String) {
                try { e.tunnelId = Long.parseLong(((String) id).trim()); }
                catch (Exception ignored) { /* sin id usable */ }
            }
        } else if (t instanceof Number) {
            e.tunnelId = ((Number) t).longValue();
        }
        Object u = row.opt("user");
        if (u instanceof JSONObject) {
            e.userName = firstString((JSONObject) u, "username", "full_name", "name");
        } else if (u instanceof Number || u instanceof String) {
            e.userName = "Usuario #" + u;
        }
        return e;
    }

    private static void fillCellData(PayEntry e, JSONObject cell) {
        e.notes = cellText(cell, "notes");
        e.markedAt = cellText(cell, "marked_at");
        e.markedBy = cellText(cell, "marked_by");
    }

    private static void sortByMonthDesc(List<PayEntry> list) {
        Collections.sort(list, (a, b) -> Integer.compare(
                b.year * 100 + b.month, a.year * 100 + a.month));
    }

    /**
     * Todas las celdas pagadas (is_paid) de todos los meses presentes en la
     * matriz, ordenadas por mes descendente. Datos reales, sin interpretar.
     */
    public static List<PayEntry> paidEntries(JSONArray rows) {
        List<PayEntry> out = new ArrayList<>();
        if (rows == null) return out;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            JSONArray months = monthsArray(row);
            if (months == null) continue;
            for (int j = 0; j < months.length(); j++) {
                JSONObject cell = months.optJSONObject(j);
                if (cell == null) continue;
                Integer m = cellInt(cell, "month");
                Integer y = cellInt(cell, "year");
                if (m == null || y == null) continue;
                if (mapCellValue(cell) != 1) continue;
                PayEntry e = baseEntry(row, m, y);
                e.paid = true;
                fillCellData(e, cell);
                out.add(e);
            }
        }
        sortByMonthDesc(out);
        return out;
    }

    /**
     * Pendientes según la regla confirmada (2026-10-31):
     *  - Mes actual: filas SIN celda del mes (noCell) + celdas del mes en
     *    estado explícito no pagado — el mismo criterio que matrix().
     *  - Meses anteriores: solo celdas explícitas no pagadas.
     * Ordenados por mes descendente (el mes actual queda primero).
     */
    public static List<PayEntry> pendingEntries(JSONArray rows, int curYear, int curMonth) {
        List<PayEntry> out = new ArrayList<>();
        if (rows == null) return out;
        int curKey = curYear * 100 + curMonth;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            // Mes actual, con el mismo buscador de celdas que matrix().
            Object cell = findMonthCell(row, curYear, curMonth);
            if (cell == null || cell == JSONObject.NULL) {
                PayEntry e = baseEntry(row, curMonth, curYear);
                e.paid = false;
                e.noCell = true;
                out.add(e);
            } else {
                int st = mapCellValue(cell);
                if (st == 0 || (st == -1 && hasExplicitState(cell))) {
                    PayEntry e = baseEntry(row, curMonth, curYear);
                    e.paid = false;
                    if (cell instanceof JSONObject) fillCellData(e, (JSONObject) cell);
                    out.add(e);
                }
            }
            // Meses anteriores: celdas explícitas no pagadas del arreglo.
            JSONArray months = monthsArray(row);
            if (months == null) continue;
            for (int j = 0; j < months.length(); j++) {
                JSONObject c = months.optJSONObject(j);
                if (c == null) continue;
                Integer m = cellInt(c, "month");
                Integer y = cellInt(c, "year");
                if (m == null || y == null) continue;
                if (y * 100 + m >= curKey) continue; // solo anteriores
                int st = mapCellValue(c);
                if (st == 0 || (st == -1 && hasExplicitState(c))) {
                    PayEntry e = baseEntry(row, m, y);
                    e.paid = false;
                    fillCellData(e, c);
                    out.add(e);
                }
            }
        }
        sortByMonthDesc(out);
        return out;
    }

    // ---------------- Tasa USDT ----------------

    /** Último valor del arreglo data de cambiocup, redondeado. Null si no se puede. */
    public static Long rateValue(String body) {
        Object root = JsonHelp.parse(body);
        JSONArray data = null;
        if (root instanceof JSONArray) {
            data = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            Object d = ((JSONObject) root).opt("data");
            if (d instanceof JSONArray) data = (JSONArray) d;
            else if (d instanceof JSONObject) {
                JSONArray nested = JsonHelp.itemsArray(d);
                if (nested != null) data = nested;
            }
        }
        if (data == null || data.length() == 0) return null;
        Object last = data.opt(data.length() - 1);
        Double val = numericValue(last);
        if (val == null && data.length() > 1) {
            // Por si el último elemento es un marcador vacío: el anterior con número.
            for (int i = data.length() - 1; i >= 0 && val == null; i--) {
                val = numericValue(data.opt(i));
            }
        }
        return val != null ? Math.round(val) : null;
    }

    private static Double numericValue(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim().replace(",", "."));
            } catch (Exception e) {
                return null;
            }
        }
        if (o instanceof JSONObject) {
            String[] keys = {"value", "rate", "price", "close", "promedio",
                    "avg", "valor", "tasa"};
            for (String k : keys) {
                Double v = numericValue(((JSONObject) o).opt(k));
                if (v != null) return v;
            }
        }
        return null;
    }
}
