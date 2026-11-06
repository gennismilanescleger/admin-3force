package com.gennis.admin3force;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseo LOCAL de los SMS de pago (etapa SMS, v0.10.0).
 *
 * Hechos conocidos (del sistema de vigilancia de pagos y de la
 * facturación de octubre, verificados con pagos reales):
 *  - Los SMS de Transfermóvil llegan del remitente «PAGOxMOVIL» y
 *    traen: teléfono de origen (cuando lo hay), importe en CUP y
 *    «Nro. Transaccion» con formatos KW601…, AY601…, T26288…
 *  - Los de EnZona NO traen número de remitente ni tarjeta; su
 *    identificador es el «No. operación» (alfanumérico mixto,
 *    p. ej. N7pGaYb0WnnO).
 *
 * El parseo es TOLERANTE a propósito (es la primera etapa): cada
 * campo que no se pueda extraer queda null y la vista lo muestra
 * como «sin identificar» — NUNCA se inventa un valor. La card
 * muestra además el texto original del SMS para que el
 * propietario verifique el parseo contra la realidad, y el
 * Diagnóstico general incluye la depuración con las tarjetas
 * enmascaradas (solo últimos 4 dígitos).
 */
public final class SmsPayments {

    private SmsPayments() {}

    public static final String CH_TM = "Transfermóvil";
    public static final String CH_EZ = "EnZona";
    public static final String CH_SMS = "SMS";

    public static final class SmsPay {
        public long dateMs;
        public String address;      // remitente del SMS (PAGOxMOVIL, …)
        public String channel;      // CH_TM / CH_EZ / CH_SMS
        public boolean sim;         // pago SIMULADO de Desarrollo (v1.5.2): jamás es un pago de verdad
        public String amount;       // "1000.00" (sin separador de miles)
        public String senderPhone;  // 8 dígitos (5XXXXXXX) o null
        public String txnId;        // id de transacción/operación o null
        public String destLast4;    // últimos 4 de la tarjeta destino o null
        // Número COMPLETO destino: SOLO para comparar contra las
        // tarjetas permitidas dentro del teléfono. Nunca se muestra
        // (la vista y el diagnóstico usan destLast4) ni sale de la app.
        public String destFull;
        // Clasificación contra las tarjetas permitidas (la fija la app):
        // 0 = sin clasificar · 1 = válida · 2 = inválida (otra tarjeta).
        public int cls;
        public String cardName;     // nombre de SU tarjeta que coincidió
        public String cardType;     // tipo de SU tarjeta que coincidió
        public String body;         // texto original

        /** Clave de deduplicación: el id de transacción manda. */
        public String dedupeKey() {
            if (txnId != null) return "txn:" + txnId;
            return "raw:" + dateMs + ":" + (body != null ? body.hashCode() : 0);
        }
    }

    // El importe admite TAMBIÉN el formato con
    // punto de miles y coma decimal («1.016,24»,
    // v1.5.7): el grupo viejo no podía capturarlo
    // entero y lo leía como «016,24» (16 en vez
    // de 1016). El de siempre («2220.00») sigue
    // entrando por la segunda alternativa.
    private static final Pattern AMOUNT_AFTER =
            Pattern.compile("([0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)\\s*CUP");
    private static final Pattern AMOUNT_BEFORE =
            Pattern.compile("CUP\\s*([0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)");
    private static final Pattern TXN_KEYWORD = Pattern.compile(
            "(?i)(?:transacci[oó]n|operaci[oó]n|nro\\.?|no\\.|id)[:\\s#]*([A-Za-z0-9]{8,20})");
    private static final Pattern TXN_KW =
            Pattern.compile("\\b([A-Z]{2}[0-9]{3}[A-Z0-9]{5,})\\b");
    private static final Pattern TXN_T =
            Pattern.compile("\\b(T[0-9]{9,}[A-Z]{0,3})\\b");
    // Las tres condiciones (minúscula, mayúscula,
    // dígito) se exigen DENTRO del token (v1.5.3):
    // con «.*» una palabra larga cualquiera del
    // texto («transferencia») pasaba por código.
    private static final Pattern TXN_MIXED = Pattern.compile(
            "\\b(?=[A-Za-z0-9]{10,14}\\b)(?=[A-Za-z0-9]*[a-z])(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*[0-9])([A-Za-z0-9]{10,14})\\b");
    private static final Pattern CARD_SPACED =
            Pattern.compile("(?:[0-9]{4}[ -]){3}[0-9]{4}");
    private static final Pattern DEST_TAIL = Pattern.compile(
            "(?i)(?:tarjeta|cuenta|destino)[^0-9]{0,24}([0-9]{4})\\b");
    private static final Pattern DIGIT_RUN = Pattern.compile("[0-9]+");
    private static final Pattern LONG_DIGITS = Pattern.compile("[0-9]{13,}");

    /** Canal según el remitente; null si no es un SMS de pago conocido. */
    public static String channelOf(String address) {
        if (address == null) return null;
        String a = address.toUpperCase(Locale.ROOT);
        if (a.contains("PAGOxMOVIL".toUpperCase(Locale.ROOT))
                || a.contains("PAGO POR MOVIL")
                || a.contains("TRANSFERMOVIL")) return CH_TM;
        if (a.contains("ENZONA")) return CH_EZ;
        return null;
    }

    /** ¿El remitente es uno de los de pago (PAGOxMOVIL/EnZona)? */
    public static boolean isPaymentSender(String address) {
        return channelOf(address) != null;
    }

    /**
     * ¿El texto parece una TRANSFERENCIA RECIBIDA? Regla armada
     * con los ejemplos reales de Gennis (2026-11-01): los tres
     * dicen «transferencia» y la marcan como recibida o con
     * destino a una cuenta/tarjeta:
     *  - EZ: «transferencia recibida Importe: … No.: …»
     *  - TM: «Se ha realizado una transferencia a la cuenta …»
     *  - TM: «El titular del telefono … le ha realizado una
     *    transferencia a la cuenta …»
     * PAGOxMOVIL manda muchos otros avisos (recargas, consultas,
     * transferencias hechas por él…): sin esta puerta, todos
     * entraban en Pagos — lo que él vio y reportó.
     */
    private static boolean looksIncoming(String body) {
        String b = body.toLowerCase(Locale.ROOT);
        if (!b.contains("transferencia")) return false;
        return b.contains("recibid")
                || b.contains("a la cuenta")
                || b.contains("a su cuenta")
                || b.contains("a la tarjeta")
                || b.contains("a su tarjeta");
    }

    /**
     * Parsea un SMS. Devuelve null si no es un pago:
     *  - SOLO cuentan los remitentes de pago conocidos
     *    (PAGOxMOVIL / Transfermóvil y EnZona) — salvo el MODO DE
     *    PRUEBA (anySender), que acepta cualquier remitente si el
     *    cuerpo trae importe + transacción (para probar en casa
     *    con SMS de otro teléfono).
     *  - De los remitentes reales se exige además que el texto
     *    parezca una transferencia RECIBIDA (looksIncoming).
     */
    /** La definición ÚNICA del modo de prueba de SMS
     *  (v1.5.2): encendido Y dentro de sus 2 horas.
     *  Pasado ese tiempo se considera APAGADO solo —
     *  un olvido no puede dejar la puerta de
     *  «cualquier remitente» abierta para siempre.
     *  Un encendido sin sello de hora (de versiones
     *  anteriores) cuenta como caducado. TODOS los
     *  lectores (actividad, receptor, avisos, fondo)
     *  pasan por aquí: una sola frontera. */
    public static boolean testModeActive(
            android.content.SharedPreferences smsPrefs) {
        try {
            if (smsPrefs == null
                    || !smsPrefs.getBoolean("test_mode", false)) {
                return false;
            }
            long at = smsPrefs.getLong("test_mode_at", 0);
            if (at <= 0) return false;
            return System.currentTimeMillis() - at
                    < 2L * 3600L * 1000L;
        } catch (Exception e) {
            return false;
        }
    }

    /** ¿Un SMS de un canal de pago que
     *  PARECE una transferencia recibida
     *  pero no se pudo leer? (v1.5.95):
     *  canal válido + menciona
     *  transferencia y CUP, pero sin
     *  importe extraíble. PAGOxMOVIL
     *  manda muchos otros avisos
     *  (recargas, consultas) que NO son
     *  esto: sin «transferencia» no hay
     *  sospecha de pago perdido. */
    public static boolean looksUnreadable(String address,
            String body) {
        try {
            if (body == null) return false;
            if (channelOf(address) == null) return false;
            String b = body.toLowerCase(Locale.ROOT);
            if (!b.contains("transferencia")) return false;
            if (!b.contains("cup")) return false;
            return extractAmount(body) == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static SmsPay parse(String address, String body, long dateMs) {
        return parse(address, body, dateMs, false);
    }

    public static SmsPay parse(String address, String body, long dateMs,
                               boolean anySender) {
        if (body == null) return null;
        String channel = channelOf(address);
        boolean testChannel = false;
        if (channel == null) {
            if (!anySender) return null;
            testChannel = true;
        }
        SmsPay p = new SmsPay();
        p.dateMs = dateMs;
        p.address = address;
        p.body = body;
        p.amount = extractAmount(body);
        p.txnId = extractTxn(body);
        if (testChannel) {
            if (p.amount == null || p.txnId == null) return null;
            p.channel = CH_SMS;
        } else {
            if (!looksIncoming(body)) return null;
            p.channel = channel;
        }
        // La tarjeta destino se busca primero: sus dígitos no deben
        // contaminar la búsqueda del teléfono de origen.
        String work = body;
        Matcher cm = CARD_SPACED.matcher(work);
        if (cm.find()) {
            String digits = cm.group().replaceAll("[^0-9]", "");
            if (digits.length() >= 4) {
                p.destLast4 = digits.substring(digits.length() - 4);
                if (digits.length() >= 15 && digits.length() <= 19) {
                    p.destFull = digits;
                }
            }
            work = work.replace(cm.group(), " ");
        }
        Matcher rm = DIGIT_RUN.matcher(work);
        while (rm.find()) {
            String run = rm.group();
            if (p.destLast4 == null && run.length() >= 15 && run.length() <= 19) {
                p.destLast4 = run.substring(run.length() - 4);
                p.destFull = run;
            }
            if (p.senderPhone == null) {
                if (run.length() == 8 && run.startsWith("5")) {
                    p.senderPhone = run;
                } else if ((run.length() == 10 || run.length() == 11)
                        && run.startsWith("53") && run.charAt(2) == '5') {
                    // Formato real de Transfermóvil (ejemplo de
                    // Gennis, 2026-11-01): el teléfono viene como
                    // 53 + 8 dígitos pegados — «5354413876» — y la
                    // app buscaba solo 8 u 11: NO lo capturaba.
                    p.senderPhone = run.substring(2);
                }
            }
        }
        // Destino abreviado: «tarjeta …1234» / «cuenta: **1234» — solo
        // cola de 4, anclada a la palabra tarjeta/cuenta/destino.
        if (p.destLast4 == null) {
            Matcher tm = DEST_TAIL.matcher(body);
            if (tm.find()) p.destLast4 = tm.group(1);
        }
        return p;
    }

    /**
     * Clasifica un pago contra las tarjetas permitidas (la regla
     * de su Tasker: el DESTINO manda). EnZona no trae tarjeta en
     * el SMS: es válida por su canal. Sin tarjetas registradas,
     * nada se clasifica (cls 0). Destino que coincide (número
     * completo, o últimos 4 cuando solo hay cola) → válida (1)
     * con el nombre/tipo de SU tarjeta; destino presente que no
     * coincide → inválida (2). Vive aquí, estática, para que la
     * usen igual la actividad y los receptores de SMS.
     */
    public static void classify(SmsPay p, org.json.JSONArray cards) {
        p.cls = 0;
        p.cardName = null;
        p.cardType = null;
        if (CH_EZ.equals(p.channel)) {
            p.cls = 1;
            return;
        }
        if (cards == null || cards.length() == 0) return;
        if (p.destFull == null && p.destLast4 == null) return;
        for (int i = 0; i < cards.length(); i++) {
            org.json.JSONObject c = cards.optJSONObject(i);
            if (c == null) continue;
            String num = c.optString("number", "");
            // Comparación SOLO por dígitos (v0.86.0): la
            // versión anterior exigía igualdad exacta del
            // número completo — si la tarjeta se guardó
            // con espacios (9206 1299 …) o en otro
            // formato, NUNCA coincidía con el número del
            // SMS y el pago quedaba «otra tarjeta» para
            // siempre (caso real: el pago de Michael a la
            // ••••0883, guardada pero no reconocida).
            // Ahora: dígitos completos iguales, o los
            // últimos 4 iguales por ambas partes.
            String numD = num.replaceAll("[^0-9]", "");
            if (numD.isEmpty()) continue;
            boolean match = false;
            if (p.destFull != null) {
                String destD =
                        p.destFull.replaceAll("[^0-9]", "");
                if (!destD.isEmpty() && destD.equals(numD)) {
                    match = true;
                } else if (destD.length() >= 4
                        && numD.length() >= 4
                        && destD.substring(destD.length() - 4)
                                .equals(numD.substring(
                                        numD.length() - 4))) {
                    match = true;
                }
            }
            if (!match && p.destLast4 != null
                    && numD.length() >= 4) {
                match = numD.substring(numD.length() - 4)
                        .equals(p.destLast4);
            }
            if (match) {
                p.cls = 1;
                p.cardName = c.optString("name", "Tarjeta");
                p.cardType = c.optString("type", "");
                return;
            }
        }
        p.cls = 2;
    }

    private static String extractAmount(String body) {
        Matcher m = AMOUNT_AFTER.matcher(body);
        if (!m.find()) {
            m = AMOUNT_BEFORE.matcher(body);
            if (!m.find()) return null;
        }
        return normalizeAmount(m.group(1));
    }

    /** "1.016,24" → "1016.24" · "1000,00" → "1000.00" · "1000.00" igual. */
    private static String normalizeAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }
        return s;
    }

    private static String extractTxn(String body) {
        Matcher k = TXN_KEYWORD.matcher(body);
        // TODAS las coincidencias, no solo la primera
        // (v1.5.3): en «Nro. Transaccion T263…» la
        // primera captura es la palabra «Transaccion»
        // (sin dígitos: se descarta) — y esa captura
        // se TRAGA la palabra clave buena, así que al
        // descartarla se sigue buscando DESDE ella:
        // ahí mismo está «Transaccion» con el código
        // detrás.
        int from = 0;
        while (true) {
            k.region(from, body.length());
            if (!k.find()) break;
            String cand = k.group(1);
            if (cand != null && hasLetterAndDigit(cand)) {
                return cand;
            }
            int gs = k.start(1);
            if (gs <= from) break;
            from = gs;
        }
        Matcher m1 = TXN_KW.matcher(body);
        if (m1.find()) return m1.group(1);
        Matcher m2 = TXN_T.matcher(body);
        if (m2.find()) return m2.group(1);
        Matcher m3 = TXN_MIXED.matcher(body);
        if (m3.find()) return m3.group(1);
        return null;
    }

    private static boolean hasLetterAndDigit(String s) {
        boolean letter = false, digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    /** Enmascara las tarjetas (con espacios o corridas largas): solo últimos 4. */
    public static String redact(String body) {
        if (body == null) return "";
        StringBuffer sb = new StringBuffer();
        Matcher m = CARD_SPACED.matcher(body);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "····" + digits.substring(digits.length() - 4)));
        }
        m.appendTail(sb);
        String out = sb.toString();
        sb = new StringBuffer();
        Matcher lm = LONG_DIGITS.matcher(out);
        while (lm.find()) {
            String run = lm.group();
            lm.appendReplacement(sb, Matcher.quoteReplacement(
                    "····" + run.substring(run.length() - 4)));
        }
        lm.appendTail(sb);
        return sb.toString();
    }
}
