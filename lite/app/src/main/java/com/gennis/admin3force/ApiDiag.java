package com.gennis.admin3force;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Registro de las llamadas a la API para la pantalla «Diagnóstico API».
 *
 * Solo se guardan método, URL, código HTTP, duración y un resumen de CLAVES
 * de la respuesta (o el error literal, limpio de cualquier forma de token).
 * NUNCA se guardan valores de tokens ni datos de clientes.
 */
public final class ApiDiag {

    public static final class Entry {
        public final String label;
        public final String method;
        public final String url;
        public final int code;
        public final long ms;
        public final String time;
        public String detail;

        Entry(String label, String method, String url, int code, long ms, String detail) {
            this.label = label;
            this.method = method;
            this.url = url;
            this.code = code;
            this.ms = ms;
            this.detail = detail;
            this.time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        }
    }

    private static final int MAX_ENTRIES = 40;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ApiDiag() {}

    public static synchronized void add(String label, String method, String url,
                                        int code, long ms, String detail) {
        ENTRIES.add(new Entry(label, method, url, code, ms, detail));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(0);
    }

    /** Añade una nota de derivación a la entrada más reciente con esa etiqueta. */
    public static synchronized void annotate(String label, String note) {
        if (note == null || note.isEmpty()) return;
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            if (e.label.equals(label)) {
                e.detail = e.detail + " · " + note;
                return;
            }
        }
    }

    public static synchronized List<Entry> entries() {
        return new ArrayList<>(ENTRIES);
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    // Cualquier cosa con forma de JWT se tapa antes de mostrar un texto de error.
    private static final Pattern JWT_SHAPE =
            Pattern.compile("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{5,}");

    public static String scrub(String s) {
        if (s == null) return "";
        return JWT_SHAPE.matcher(s).replaceAll("[token]");
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    /** Recorte SIN tocar el contenido (para cuerpos literales de respuesta). */
    public static String truncateRaw(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
