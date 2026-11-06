package com.gennis.admin3force;

import android.content.Context;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

/**
 * Permanencia de datos sin conexión (v0.26.0, su diseño): lo
 * último que el panel devolvió bien — cuentas, túneles y matriz —
 * queda guardado en el teléfono y la app trabaja sobre eso cuando
 * no hay internet, actualizándolo en cuanto la red responde.
 *
 * LIGERO a propósito (su orden: nada de acumulación de basura):
 * UN archivo por bloque, que se SOBRESCRIBE entero en cada carga
 * buena (vía archivo temporal + renombrado, para no dejar un
 * archivo a medias). Sin historiales, sin copias, sin base de
 * datos: tres archivos JSON pequeños y la hora en que se
 * guardaron.
 */
public final class DataCache {

    private DataCache() {}

    private static final String PREFS = "admin3force_cache";

    public static void save(Context ctx, String key, JSONArray items) {
        if (ctx == null || items == null) return;
        try {
            String text = items.toString();
            File tmp = new File(ctx.getFilesDir(), "cache_" + key + ".tmp");
            FileOutputStream os = new FileOutputStream(tmp);
            os.write(text.getBytes("UTF-8"));
            os.close();
            File dst = new File(ctx.getFilesDir(), "cache_" + key + ".json");
            if (!tmp.renameTo(dst)) {
                FileOutputStream os2 = new FileOutputStream(dst);
                os2.write(text.getBytes("UTF-8"));
                os2.close();
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("saved_at_" + key,
                            System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            // La caché nunca debe tumbar la app.
        }
    }

    /** El bloque guardado, o null si nunca se guardó o está roto. */
    public static JSONArray load(Context ctx, String key) {
        if (ctx == null) return null;
        try {
            File f = new File(ctx.getFilesDir(), "cache_" + key + ".json");
            if (!f.exists() || f.length() == 0) return null;
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            br.close();
            if (sb.length() == 0) return null;
            return new JSONArray(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** La hora del guardado más reciente entre los tres bloques (0 si nada). */
    public static long latestSavedAt(Context ctx) {
        if (ctx == null) return 0;
        android.content.SharedPreferences sp =
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long m = 0;
        for (String k : new String[]{"users", "tunnels", "matrix"}) {
            m = Math.max(m, sp.getLong("saved_at_" + k, 0));
        }
        return m;
    }
}
