package com.gennis.admin3force;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import java.util.concurrent.Executor;

/**
 * Bloqueo de la app con el BiometricPrompt DEL FRAMEWORK
 * (android.hardware.biometrics), usando la huella/rostro ya registrados en
 * el teléfono, con respaldo de la credencial del dispositivo (PIN/patrón).
 *
 * OJO: BiometricPrompt existe solo en API 28+. Esta clase vive separada y
 * SOLO se carga desde MainActivity cuando SDK_INT >= 28.
 */
final class BiometricLock {

    interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private BiometricLock() {}

    /** ¿Hay con qué bloquear? (biometría utilizable o bloqueo de pantalla seguro). */
    static boolean canLock(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager bm =
                    (BiometricManager) activity.getSystemService(Context.BIOMETRIC_SERVICE);
            if (bm != null) {
                int result;
                if (Build.VERSION.SDK_INT >= 30) {
                    result = bm.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                } else {
                    result = bm.canAuthenticate();
                }
                if (result == BiometricManager.BIOMETRIC_SUCCESS) return true;
            }
        }
        KeyguardManager kg =
                (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        return kg != null && kg.isDeviceSecure();
    }

    /** Muestra el prompt del sistema. El fallo/cancelación llega por onError. */
    static void prompt(Activity activity, final Callback callback) {
        Executor executor = activity.getMainExecutor();
        BiometricPrompt.AuthenticationCallback authCallback =
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        callback.onError(errString != null
                                ? errString.toString()
                                : "No se pudo desbloquear");
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // El propio prompt del sistema ya lo muestra; el candado sigue.
                    }
                };

        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                .setTitle("Admin 3FORCE")
                .setSubtitle("Desbloquea para ver tu sesión");
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            // API 28/29: permitir también la credencial del dispositivo.
            builder.setDeviceCredentialAllowed(true);
        }
        builder.build().authenticate(new CancellationSignal(), executor, authCallback);
    }
}
