# Admin 3FORCE — Hito LOGIN (app real, Android)

Construido el 2026-10-30. APK debug generado y verificado:

- **APK:** `~/workspace/admin-3force/Admin3FORCE-login-debug.apk` (2 655 284 bytes)
- **Paquete:** `com.gennis.admin3force` · versionCode 1 · versionName `0.1.0-login`
- **minSdk 26 · targetSdk 34 · compileSdk 34**
- **Permisos:** INTERNET, USE_BIOMETRIC, USE_FINGERPRINT (ninguno más)
- **Firma:** clave debug de Android generada en `~/workspace/toolchain/debug.keystore`
- **Reproducir el build:** `cd ~/workspace/admin-3force/android && ./build.sh`

## Qué se construyó (solo el hito Login, sin datos simulados en ninguna pantalla)

Una sola actividad (`MainActivity`) con tres estados:

1. **Login** — Cabecera con la paleta del prototipo (fondo `#070F1E`, cian
   `#22D3EE`, texto `#F1F5F9`): título «Admin 3FORCE» y la dirección del panel
   debajo. Debajo, el **WebView real** de
   `https://reseller.3forcevpn.com/auth/login`. La app NO tiene campos nativos
   de usuario/contraseña: Gennis teclea sus credenciales y resuelve el captcha
   **en la propia página del panel**, como humano.
2. **Bloqueo** — Si hay una sesión guardada y el access token sigue vigente,
   la app pide desbloqueo con `BiometricPrompt` usando la huella/rostro ya
   registrados en el teléfono, con respaldo de la credencial del dispositivo
   (PIN/patrón). La biometría es solo el candado de la app, nunca un login.
3. **Sesión** (sustituto temporal del Home, SIN ningún dato de negocio) —
   Muestra solo datos reales derivados del token capturado: «Sesión activa»,
   el host del panel, los claims que el JWT traiga de verdad (`sub`, usuario,
   rol, emisor, `iat`, `exp` — si un claim no existe, esa fila no se muestra),
   la fuente del token, la fecha de captura y una **cuenta atrás en vivo**
   hasta la expiración del access (`exp`). Botones: «Abrir panel» (reabre el
   WebView, con botón «← Volver a la sesión») y «Cerrar sesión» (borra el
   almacén cifrado, las cookies y el storage del WebView, y vuelve al Login).

## Cómo funciona la captura del token

La documentación del panel (API v2.1.0) no dice dónde se guarda el JWT ni trae
esquemas, así que la captura es **genérica, sin inventar endpoints ni claves**:

- El `WebViewClient` vigila la navegación: en cuanto la URL sale de
  `/auth/login` hacia otra página del mismo host, se intentan capturas (al
  momento, +0,9 s y +2,2 s, porque el token puede escribirse un instante
  después de navegar).
- Cada intento vuelca `localStorage` y `sessionStorage` completos con
  `evaluateJavascript` y lee las cookies del dominio con `CookieManager`.
- Todo el texto se escanea buscando valores con forma de JWT (tres segmentos
  base64url); cada candidato se valida decodificando su payload y exigiendo
  claims típicos (`exp`/`iat`/`sub`/`iss`).
- El **access** se identifica primero por pista en el nombre de la clave
  («access»/«token» sin «refresh») y, si no hay pista, por ser el de **vida
  más corta** (la doc dice que el access vive 10 min; el refresh, mucho más).
  El **refresh** se identifica por su nombre o por ser el de vida más larga.
- Se guarda **todo** lo capturado (tokens con su fuente y clave, y las
  cookies) como un JSON cifrado con **AES-256-GCM**, con la clave en el
  **Android Keystore**, en `SharedPreferences` privadas de la app.
- La contraseña de Gennis **nunca** se captura ni se guarda: vive solo en la
  página del panel.

## Si el access expira con sesión guardada

**No se inventó ningún endpoint de refresh** (la doc no lo documenta). La app
vuelve a la pantalla de Login con el WebView — donde las cookies del panel
pueden restaurar la sesión o Gennis entra de nuevo — y si la navegación sale
del login con un JWT fresco, la captura lo guarda y sustituye la sesión.
Si la cuenta atrás de la pantalla Sesión llega a cero, también salta sola al
Login. Esto es una **limitación conocida y aceptada del hito**.

## El toolchain y el problema de red de esta VM (importante)

Instalado bajo `~/workspace/toolchain/`:

- **Temurin JDK 17.0.21** (`jdk/`) — tarball oficial de Adoptium.
- **Gradle 8.7** (`gradle-8.7/`) — zip oficial. Instalado y reservado.
- **Android SDK** (`android-sdk/`): command-line tools 11 (zip oficial),
  `platforms;android-34` y `build-tools;34.0.0`.
- **kotlinc 1.9.24** (`kotlinc/`) y **R8 8.5.35** (`r8/r8-8.5.35.jar`).

**Gradle y sdkmanager NO pueden funcionar en esta VM**, y no es un problema
del proyecto: la capa de permisos de red de Muse
(*Settings → Permissions → Direct network protocols → **other_tcp = Deny***)
intercepta **todas** las conexiones TCP de procesos Java. Prueba: un cliente
Java contra un servidor Java en `127.0.0.1` recibe, en lugar de los datos, el
texto inyectado `muse: Other TCP connections is turned off for this
assistant…` (el primer byte, `m` = tag 109, es exactamente lo que rompía el
protocolo del daemon de Gradle: «Could not receive a message from the
daemon»). El mismo bloqueo tumba los túneles HTTPS de sdkmanager. Python y
curl no están afectados. No se ha intentado ningún rodeo para evadir ese
permiso: **si Gennis cambia `other_tcp` a Ask/Allow, el camino Gradle
(`./gradlew assembleDebug`) vuelve a ser viable tal cual**, con el mismo
proyecto (`settings.gradle`, `build.gradle`, `app/build.gradle` están
escritos y coherentes; solo faltan los scripts `gradlew`, que genera el
propio Gradle cuando pueda ejecutarse).

Ruta usada en su lugar (todo oficial, sin atajos de seguridad):

- Los paquetes del SDK se instalaron desde los **zips oficiales de
  dl.google.com** elegidos con el `repository2-1.xml` oficial
  (`platform-34-ext12_r01.zip`, `build-tools_r34-linux.zip`), colocados en la
  estructura exacta que sdkmanager habría creado. (Las licencias no se
  pudieron «aceptar» con sdkmanager por el mismo bloqueo; los binarios
  aapt2/d8/zipalign/apksigner no las exigen para compilar.)
- Las dependencias AndroidX (appcompat 1.7.0, core-ktx 1.13.1, biometric
  1.1.0 y su grafo transitivo, 44 artefactos) se resolvieron leyendo los
  **POM reales** de Google Maven / Maven Central con
  `tools/resolve_deps.py` y se guardan en `deps/` con su `deps.txt`.
  Detalle: `androidx.annotation:annotation` 1.6.0 es un artefacto KMP vacío;
  el resolvedor lo sustituye por `annotation-jvm`, como haría Gradle.
- La compilación es el pipeline clásico del SDK, sin Gradle:
  **aapt2** compile+link (con `--extra-packages` para los R de librerías) →
  **kotlinc** (app + R generados) → **R8/D8 8.5.35** → **zipalign** →
  **apksigner**. Dos escollos reales, documentados también en `build.sh`:
  el d8 de build-tools 34 (R8 8.2.2-dev) revienta con NPE sobre clases de
  appcompat 1.7.0 (por eso se usa el R8 8.5.35 suelto, el que corresponde a
  AGP 8.5), y d8 rechaza el directorio de clases de kotlinc por el fichero
  `.kotlin_module` (se empaqueta antes en un jar).

## Qué quedó SIN verificar (honestidad completa)

- **La captura real del token solo se puede probar cuando Gennis inicie
  sesión en su teléfono.** No hay forma de probarla aquí: no tenemos sus
  credenciales (y no se piden ni se guardan) ni un dispositivo. Si el panel
  guardara su JWT solo en una variable de JavaScript en memoria (ni en
  storage ni en cookies), el escaneo no lo encontraría y habría que buscar
  otro gancho con la sesión real abierta — es el riesgo principal del hito.
  Tampoco se sabe aún el nombre exacto de la clave donde el panel guarda el
  token; la captura está hecha para no depender de eso.
- El **flujo biométrico** y el render del panel real (incluido su captcha)
  dentro del WebView están compilados pero **no probados en un dispositivo**.
- El APK es **debug** (firmado con clave debug), pensado para instalar y
  probar el login; no es el APK de producción.

## Archivos clave

- `android/app/src/main/java/com/gennis/admin3force/MainActivity.kt` —
  estados Login/Bloqueo/Sesión, WebView, biometría, cuenta atrás.
- `android/app/src/main/java/com/gennis/admin3force/SessionCapture.kt` —
  escaneo genérico de storage + cookies y montaje del JSON de sesión.
- `android/app/src/main/java/com/gennis/admin3force/JwtUtils.kt` —
  detección/validación de JWT y elección access/refresh.
- `android/app/src/main/java/com/gennis/admin3force/SessionStore.kt` —
  cifrado AES-GCM con Keystore.
- `android/build.sh` — build reproducible completo (rutas del toolchain
  incluidas).
- `android/tools/resolve_deps.py` — resolvedor de dependencias desde POM.
- `android/deps/` — los 44 artefactos AndroidX descargados + `deps.txt`.
- `android/settings.gradle`, `android/build.gradle`, `android/app/build.gradle`,
  `android/gradle.properties` — el proyecto Gradle equivalente, listo para
  cuando `other_tcp` lo permita.
