# Admin 3FORCE Lite — APK ligero de pruebas (hito LOGIN)

Construido el 2026-10-30, después de que el primer APK (Kotlin + AndroidX,
minSdk 26, 2,6 MB) diera en el teléfono de Gennis «Se produjo un error en el
análisis del paquete» al instalar. Decisión de Gennis: app ligera para probar
los flujos ahora; la nativa completa vendrá después, cuando todo funcione.

- **APK:** `~/workspace/admin-3force/Admin3FORCE-lite.apk` — **25 003 bytes**
- **Paquete:** `com.gennis.admin3force` · versionCode 2 · versionName `0.2.0-lite`
- **minSdk 21 (Android 5+) · targetSdk 34 · compileSdk 34**
- **Permisos:** INTERNET, USE_BIOMETRIC, USE_FINGERPRINT (ninguno más)
- **Firma:** clave debug de Android de `~/workspace/toolchain/debug.keystore`,
  con **v1 y v2 activadas** explícitamente.
- **Reproducir el build:** `cd ~/workspace/admin-3force/lite && ./build.sh`

## Qué es (y qué no es)

**Java puro, SOLO framework Android, CERO dependencias**: sin AndroidX, sin
Kotlin, sin appcompat. Una sola Activity (`MainActivity`) con la UI construida
por código (sin layouts XML ni recursos), de ahí el tamaño: 25 KB frente a los
2,6 MB de la build anterior. La lógica es idéntica a la del hito Login
anterior, reimplementada en Java simple:

1. **Login** — Cabecera nativa simple (título «Admin 3FORCE» en cian `#22D3EE`
   sobre fondo `#070F1E`, y el host del panel debajo) + el **WebView real** de
   `https://reseller.3forcevpn.com/auth/login`. Sin campos nativos: Gennis
   teclea sus credenciales y resuelve el captcha **en la página del panel**.
   Su contraseña nunca se captura ni se guarda.
2. **Captura genérica** (sin inventar claves ni endpoints) — Al salir de
   `/auth/login`, se vuelcan `localStorage` y `sessionStorage` con
   `evaluateJavascript` y se leen las cookies con `CookieManager`; se buscan
   valores con forma de JWT (3 segmentos base64url, payload JSON decodificable
   con `exp`/`iat`/`sub`/`iss`); access = pista de nombre o vida más corta,
   refresh = nombre o vida más larga. Todo se guarda cifrado **AES-256-GCM
   con Android Keystore** (en API 23+) en almacenamiento privado.
3. **Pantalla Sesión** — Solo datos reales del token: host del panel, los
   claims presentes (sin claim no hay fila), la **fuente del token**
   (localStorage/sessionStorage/cookie + nombre de la clave — el dato
   diagnóstico clave), fecha de captura y **cuenta atrás viva hasta `exp`**.
   Botones «Abrir panel» y «Cerrar sesión» (borra el almacén cifrado y las
   cookies/storage del WebView, y vuelve al login). Cero datos simulados.
4. **Bloqueo de app** — Solo en **API 28+**, con el `BiometricPrompt` **del
   framework** (`android.hardware.biometrics`), con respaldo de la credencial
   del dispositivo. Si la biometría falla o se cancela, la app queda en la
   pantalla de bloqueo con el mensaje del sistema y botón «Reintentar».
5. **Access expirado** — No se inventó ningún endpoint de refresh: se vuelve
   al WebView de login (las cookies pueden restaurar la sesión).

## Verificaciones (salida real del build)

`apksigner verify --verbose`:

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Number of signers: 1
```

`aapt2 dump badging` (extracto):

```
package: name='com.gennis.admin3force' versionCode='2' versionName='0.2.0-lite'
    platformBuildVersionName='14' platformBuildVersionCode='34' compileSdkVersion='34'
sdkVersion:'21'
targetSdkVersion:'34'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.USE_BIOMETRIC'
uses-permission: name='android.permission.USE_FINGERPRINT'
application: label='Admin 3FORCE'
launchable-activity: name='com.gennis.admin3force.MainActivity'
```

El `classes.dex` (31 028 bytes) contiene las 7 clases de la app
(`MainActivity`, `SessionCapture`, `JwtUtils`, `SessionStore`, `KeystoreAes`,
`BiometricLock`, `PanelConfig`, más sus clases internas), comprobado con
`dexdump`.

## La cadena de compilación (sin Gradle, como en el hito anterior)

`aapt2 link` (solo manifiesto; no hay recursos) → `javac` contra
`android.jar` (source/target 8) → `d8 --min-api 21` (sin shrinking) →
`zipalign` → `apksigner` (v1+v2). Escollo encontrado y resuelto, el mismo del
hito anterior: d8 rechaza el **directorio** de clases como entrada
(«Unsupported source file type», tanto el d8 de build-tools 34 como el R8
8.5.35 suelto), así que las clases se empaquetan antes en un jar — `build.sh`
lo hace y, si el d8 de build-tools fallara por otro motivo, cae al jar R8
8.5.35 de `~/workspace/toolchain/r8/` (en la build final dexó el d8 de
build-tools, ya con el jar como entrada).

## Limitaciones honestas

- **La captura real del token solo se prueba cuando Gennis inicie sesión en
  su teléfono.** Si el panel guardara su JWT solo en una variable JavaScript
  en memoria (ni storage ni cookies), el escaneo no lo encontraría y habría
  que buscar otro gancho con la sesión real abierta. Es el riesgo principal,
  igual que en la build anterior.
- **En API 21–27 no hay bloqueo de app** (el `BiometricPrompt` del framework
  nace en API 28): con sesión vigente se entra directo. Limitación aceptada
  de la Lite; la nativa futura usará AndroidX Biometric para cubrir más
  versiones.
- **En API 21/22 la clave de cifrado no está respaldada por hardware**: el
  AndroidKeyStore no admite claves AES antes de API 23, así que la Lite
  genera una clave aleatoria y la guarda en las preferencias privadas de la
  app. Desde API 23 sí es Keystore de verdad.
- El **flujo biométrico** y el render del panel real (incluido su captcha)
  dentro del WebView están compilados pero **no probados en un dispositivo**.
- Es un APK **debug** (firmado con clave debug), para instalar y probar los
  flujos; no es el APK de producción.
- Detalle cosmético: el icono del lanzador es el genérico por defecto de
  Android (la Lite no incluye icono propio).

## Archivos

- `lite/app/src/main/AndroidManifest.xml`
- `lite/app/src/main/java/com/gennis/admin3force/` — `MainActivity.java`
  (única Activity: estados Login/Bloqueo/Sesión, UI programática),
  `SessionCapture.java`, `JwtUtils.java`, `SessionStore.java`,
  `KeystoreAes.java` (solo se carga en API 23+), `BiometricLock.java`
  (solo se carga en API 28+), `PanelConfig.java`.
- `lite/build.sh` — build reproducible completo.
- APK final: `~/workspace/admin-3force/Admin3FORCE-lite.apk`.
