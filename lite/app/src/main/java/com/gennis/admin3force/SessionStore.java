package com.gennis.admin3force;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Almacén de la sesión capturada, cifrado con AES-256-GCM.
 *
 * En API 23+ la clave vive en el Android Keystore (ver KeystoreAes) y nunca
 * sale del almacén seguro del sistema. En API 21/22 el AndroidKeyStore no
 * admite claves AES, así que la versión Lite genera una clave aleatoria y la
 * guarda en las propias SharedPreferences privadas de la app (sin respaldo
 * hardware): limitación aceptada y documentada de la Lite.
 *
 * El blob cifrado se guarda en SharedPreferences privadas de la app.
 * Solo se guardan los tokens y cookies capturados del panel. La contraseña
 * de Gennis NUNCA se guarda: la teclea él en la página del panel.
 */
public class SessionStore {

    private static final String PREFS_NAME = "admin3force_session_store";
    private static final String KEY_BLOB = "session_blob";
    private static final String KEY_FALLBACK_KEY = "fallback_key_b64";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasSession() {
        return prefs.contains(KEY_BLOB);
    }

    /** Guarda el JSON de la sesión cifrado. */
    public void save(String sessionJson) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(sessionJson.getBytes("UTF-8"));
            String blob = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString(KEY_BLOB, blob).apply();
        } catch (Exception e) {
            // Sin cifrado disponible no se guarda nada en claro.
        }
    }

    /** Devuelve el JSON de la sesión descifrado, o null si no hay o falla. */
    public String load() {
        String blob = prefs.getString(KEY_BLOB, null);
        if (blob == null) return null;
        try {
            String[] parts = blob.split(":", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] data = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(data), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** Borra la sesión guardada por completo. */
    public void clear() {
        prefs.edit().remove(KEY_BLOB).remove(KEY_FALLBACK_KEY).apply();
    }

    private SecretKey getKey() throws Exception {
        if (Build.VERSION.SDK_INT >= 23) {
            return KeystoreAes.getOrCreateKey();
        }
        // API 21/22: clave aleatoria guardada en prefs privadas de la app.
        String b64 = prefs.getString(KEY_FALLBACK_KEY, null);
        if (b64 != null) {
            return new SecretKeySpec(Base64.decode(b64, Base64.NO_WRAP), "AES");
        }
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256, new SecureRandom());
        SecretKey key = gen.generateKey();
        prefs.edit()
                .putString(KEY_FALLBACK_KEY, Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP))
                .apply();
        return key;
    }
}
