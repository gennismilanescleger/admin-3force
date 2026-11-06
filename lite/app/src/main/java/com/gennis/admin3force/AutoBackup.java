package com.gennis.admin3force;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Respaldo automático (v1.0.0, su orden): a las 2:00 AM,
 * cada día o cada semana según Ajustes › Datos y
 * respaldo, la app guarda sola el MISMO respaldo
 * completo del exportar manual en Descargas › Admin
 * 3FORCE › Respaldos. Despertador propio con el patrón
 * del Vigía (setAndAllowWhileIdle + rearme al arrancar
 * el teléfono). Sin notificaciones de sistema: queda
 * anotado en el Historial de la app.
 */
public class AutoBackup {

    public static final String ACTION =
            "com.gennis.admin3force.AUTO_BACKUP";
    private static final int REQ = 7302;

    public static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(
                "admin3force_settings", Context.MODE_PRIVATE);
    }

    /** 0 = nunca, 1 = diario, 2 = semanal. */
    public static int mode(Context ctx) {
        return sp(ctx).getInt("auto_backup_mode", 0);
    }

    private static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, BackupReceiver.class);
        i.setAction(ACTION);
        return PendingIntent.getBroadcast(ctx, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Deja el despertador puesto para la próxima
     *  2:00 AM (o lo quita si el modo es Nunca). */
    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx
                    .getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            if (mode(ctx) == 0) {
                am.cancel(pending(ctx));
                return;
            }
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 2);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis()
                    <= System.currentTimeMillis()) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    c.getTimeInMillis(), pending(ctx));
        } catch (Exception ignored) {
            // sin despertador: el respaldo manual sigue
        }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx
                    .getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(ctx));
        } catch (Exception ignored) {
        }
    }

    /** La corrida de las 2 AM: en semanal, solo corre
     *  si pasaron 6+ días del último respaldo
     *  automático (así «semanal» no depende del día de
     *  la semana en que se encendió). Guarda, anota en
     *  el Historial y rearma la siguiente noche. */
    public static void run(Context ctx) {
        try {
            int m = mode(ctx);
            if (m == 0) return;
            long now = System.currentTimeMillis();
            if (m == 2) {
                long last = sp(ctx).getLong(
                        "auto_backup_last", 0);
                if (last > 0 && now - last
                        < 6L * 24 * 3600 * 1000) {
                    return;
                }
            }
            JSONObject root = BackupBuilder.build(ctx);
            String fn = "respaldo-admin3force-"
                    + new SimpleDateFormat("yyyy-MM-dd",
                            Locale.US).format(new Date())
                    + ".json";
            boolean ok = save(ctx, "Respaldos", fn,
                    root.toString(2).getBytes("UTF-8"));
            if (ok) {
                sp(ctx).edit()
                        .putLong("auto_backup_last", now)
                        .apply();
                HistoryLog.add(ctx, "sistema",
                        "Respaldo automático guardado",
                        fn + " · Descargas › Admin 3FORCE › "
                                + "Respaldos", null);
            } else {
                notifyFailure(ctx,
                        "No se pudo guardar el archivo");
            }
        } catch (Exception e) {
            notifyFailure(ctx, "Falló al prepararlo");
        } finally {
            schedule(ctx);
        }
    }

    /** Aviso de respaldo fallido
     *  (v1.5.97, su #4): antes un fallo
     *  no despertaba a nadie y él creía
     *  estar respaldado. Una vez al día
     *  como máximo: Historial + campana
     *  + Android. */
    private static void notifyFailure(Context ctx,
            String why) {
        try {
            SharedPreferences st = sp(ctx);
            String today = new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.US)
                    .format(new Date());
            if (today.equals(st.getString(
                    "auto_backup_fail_day", ""))) {
                return;
            }
            st.edit().putString(
                    "auto_backup_fail_day", today)
                    .apply();
            String title =
                    "El respaldo automático falló";
            String sub = why + " · revisa el espacio "
                    + "del teléfono; tu último "
                    + "respaldo bueno sigue guardado";
            HistoryLog.add(ctx, "sistema", title, sub,
                    null);
            if (!st.getBoolean("n_sistema", true)) {
                return;
            }
            try {
                SharedPreferences np =
                        ctx.getSharedPreferences(
                                "admin3force_notices",
                                Context.MODE_PRIVATE);
                org.json.JSONArray a;
                try {
                    String sj = np.getString(
                            "items_json", null);
                    a = sj != null
                            ? new org.json.JSONArray(sj)
                            : new org.json.JSONArray();
                } catch (Exception e2) {
                    a = new org.json.JSONArray();
                }
                JSONObject o = new JSONObject();
                o.put("kind", "sistema");
                o.put("title", title);
                o.put("sub", sub);
                o.put("target", "home");
                o.put("ts",
                        System.currentTimeMillis());
                a.put(o);
                while (a.length() > 100) a.remove(0);
                np.edit().putString("items_json",
                        a.toString()).apply();
            } catch (Exception ignored) {
            }
            SysNotices.post(ctx, "sistema", title, sub,
                    "home", -1, null);
        } catch (Exception ignored) {
        }
    }

    /** El guardado en la carpeta de la app, igual que
     *  MainActivity.saveToAppFolder (MediaStore en
     *  Android 10+; archivo directo en los viejos). */
    private static boolean save(Context ctx, String sub,
                                String fileName, byte[] bytes) {
        String rel = "Download/Admin 3FORCE/" + sub;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr =
                        ctx.getContentResolver();
                Uri coll = MediaStore.Files
                        .getContentUri("external");
                try {
                    cr.delete(coll,
                            MediaStore.Files.FileColumns
                                    .DISPLAY_NAME + "=? AND "
                                    + MediaStore.Files
                                            .FileColumns
                                            .RELATIVE_PATH
                                    + "=?",
                            new String[]{fileName, rel + "/"});
                } catch (Exception ignored) {
                    // nada que reemplazar
                }
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Files.FileColumns
                        .DISPLAY_NAME, fileName);
                cv.put(MediaStore.Files.FileColumns
                        .RELATIVE_PATH, rel);
                Uri uri = cr.insert(coll, cv);
                if (uri == null) return false;
                OutputStream os =
                        cr.openOutputStream(uri);
                if (os == null) {
                    try {
                        cr.delete(uri, null, null);
                    } catch (Exception ignored) {
                    }
                    return false;
                }
                os.write(bytes);
                os.close();
                return true;
            }
            File base = Environment
                    .getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
            File dir = new File(
                    new File(base, "Admin 3FORCE"), sub);
            if (!dir.exists()) dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(
                    new File(dir, fileName));
            fos.write(bytes);
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
