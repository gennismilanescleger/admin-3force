package com.gennis.admin3force;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Clave AES-256 del Android Keystore.
 *
 * OJO: esta clase referencia KeyGenParameterSpec / KeyProperties, que solo
 * existen en API 23+. Por eso vive en su propia clase y SOLO se carga desde
 * SessionStore cuando Build.VERSION.SDK_INT >= 23. Nunca debe referenciarse
 * desde código que corra en API 21/22.
 */
final class KeystoreAes {

    private static final String KEY_ALIAS = "admin3force_session_key";

    private KeystoreAes() {}

    static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
