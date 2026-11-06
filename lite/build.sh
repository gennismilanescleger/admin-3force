#!/usr/bin/env bash
# ============================================================================
# Admin 3FORCE Lite — build reproducible del APK de pruebas (hitos Login + Home)
#
# Java puro + framework Android, CERO dependencias: por eso la cadena es la
# del SDK a secas (aapt2 → javac → d8 → zipalign → apksigner), sin Gradle
# (que además no puede correr en esta VM: la capa de permisos bloquea el TCP
# de procesos Java — ver ../android/BUILD-LOGIN.md).
#
# Toolchain (todo bajo ~/workspace/toolchain):
#   JAVA_HOME    = ~/workspace/toolchain/jdk          (Temurin 17)
#   Android SDK  = ~/workspace/toolchain/android-sdk  (platform 34, build-tools 34.0.0)
#   R8 8.5.35    = ~/workspace/toolchain/r8/r8-8.5.35.jar (respaldo de d8)
#   Keystore     = ~/workspace/toolchain/debug.keystore (debug, pass "android")
# ============================================================================
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
TC="$HOME/workspace/toolchain"

export JAVA_HOME="$TC/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

BT="$TC/android-sdk/build-tools/34.0.0"
ANDROID_JAR="$TC/android-sdk/platforms/android-34/android.jar"
BUILD="$PROJ/build"
# Patrón de entregables (pedido del propietario): Admin3FORCE-<versionName>.apk
# — la versión va en el NOMBRE DEL ARCHIVO, para que el teléfono no añada
# (1), (2)… al descargar varias builds. La etiqueta de la app no cambia.
VERSION_NAME=1.5.116-lite
OUT_APK="$HOME/workspace/admin-3force/Admin3FORCE-${VERSION_NAME}.apk"
KEYSTORE="$TC/debug.keystore"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen"

# --- 1) Recursos (v1.5.30: los iconos de la app) y enlace
# del manifiesto con aapt2. La UI sigue programática; res/
# solo trae los iconos (launcher, adaptativo y la silueta
# de notificación) y genera la clase R.
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" \
  -o "$BUILD/res.zip"
"$BT/aapt2" link \
  -o "$BUILD/base.apk" \
  --manifest "$PROJ/app/src/main/AndroidManifest.xml" \
  -A "$PROJ/app/src/main/assets" \
  --java "$BUILD/gen" \
  -I "$ANDROID_JAR" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 251 \
  --version-name "$VERSION_NAME" \
  "$BUILD/res.zip"

# --- 2) Compilar el Java contra android.jar (solo framework).
# shellcheck disable=SC2046
javac -source 8 -target 8 -encoding UTF-8 \
  -cp "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  $(find "$PROJ/app/src/main/java" -name '*.java') \
  $(find "$BUILD/gen" -name '*.java')

# --- 3) Dexar con min-api 21, sin shrinking.
# OJO: d8 (tanto el de build-tools 34 como el R8 suelto) RECHAZA el directorio
# de clases como entrada ("Unsupported source file type", mismo escollo que en
# el hito anterior con kotlinc): hay que empaquetar las clases en un jar.
( cd "$BUILD/classes" && jar --create --file "$BUILD/classes.jar" . )
# Primero el d8 de build-tools 34; si falla, el jar R8 8.5.35 suelto.
if ! "$BT/d8" --min-api 21 --lib "$ANDROID_JAR" \
      --output "$BUILD/dex" "$BUILD/classes.jar"; then
  echo "d8 de build-tools falló; usando R8 8.5.35…"
  rm -rf "$BUILD/dex"; mkdir -p "$BUILD/dex"
  java -cp "$TC/r8/r8-8.5.35.jar" com.android.tools.r8.D8 --release --min-api 21 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD/dex" "$BUILD/classes.jar"
fi

# --- 4) Empaquetar y alinear.
cp "$BUILD/base.apk" "$BUILD/app-unsigned.apk"
( cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes*.dex )
"$BT/zipalign" -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

# --- 5) Firmar con v1 Y v2 (clave debug) y verificar.
"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --out "$BUILD/Admin3FORCE-lite.apk" "$BUILD/app-aligned.apk"

echo "=== apksigner verify --verbose ==="
"$BT/apksigner" verify --verbose "$BUILD/Admin3FORCE-lite.apk"

cp "$BUILD/Admin3FORCE-lite.apk" "$OUT_APK"

echo "=== aapt2 dump badging ==="
"$BT/aapt2" dump badging "$OUT_APK"

echo "APK generado: $OUT_APK"
ls -la "$OUT_APK"
