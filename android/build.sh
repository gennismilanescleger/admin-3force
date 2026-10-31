#!/usr/bin/env bash
# ============================================================================
# Admin 3FORCE — build reproducible del APK debug (hito Login)
#
# Rutas del toolchain (todo vive bajo ~/workspace/toolchain):
#   JAVA_HOME        = ~/workspace/toolchain/jdk            (Temurin 17)
#   ANDROID_HOME     = ~/workspace/toolchain/android-sdk    (platform 34 +
#                                                            build-tools 34.0.0)
#   Gradle 8.7       = ~/workspace/toolchain/gradle-8.7     (instalado, pero
#                       ver la nota de abajo)
#   kotlinc 1.9.24   = ~/workspace/toolchain/kotlinc
#   GRADLE_USER_HOME = ~/workspace/toolchain/gradle-home
#
# NOTA — por qué este script NO usa Gradle en esta VM:
#   La capa de permisos de red de la VM (Muse → Permissions → Direct
#   network protocols → other_tcp = Deny) intercepta TODAS las conexiones
#   TCP de procesos Java, incluido el socket local cliente↔daemon de
#   Gradle: el daemon arranca pero el cliente recibe un aviso de denegación
#   en vez del protocolo ("Could not receive a message from the daemon").
#   sdkmanager también es Java y tampoco puede descargar paquetes.
#   Por eso: los paquetes del SDK y las dependencias se descargaron con
#   curl (zips oficiales de Google / POM de Google Maven y Maven Central)
#   y el APK se compila con las herramientas del propio SDK
#   (aapt2 + kotlinc + d8 + zipalign + apksigner), sin Gradle.
#   Si algún día other_tcp pasa a "Ask"/"Allow", el camino Gradle vuelve a
#   ser viable tal cual: ./gradlew assembleDebug con este mismo entorno.
# ============================================================================
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
TC="$HOME/workspace/toolchain"

export JAVA_HOME="$TC/jdk"
export ANDROID_HOME="$TC/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$TC/gradle-home"
export PATH="$JAVA_HOME/bin:$TC/kotlinc/bin:$ANDROID_HOME/build-tools/34.0.0:$ANDROID_HOME/cmdline-tools/latest/bin:$TC/gradle-8.7/bin:$PATH"

# local.properties para un eventual build con Gradle/AGP.
echo "sdk.dir=$ANDROID_HOME" > "$PROJ/local.properties"

BT="$ANDROID_HOME/build-tools/34.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
BUILD="$PROJ/build/manual"
DEPS_DIR="$PROJ/deps"
OUT_APK="$HOME/workspace/admin-3force/Admin3FORCE-login-debug.apk"

# --- Dependencias AndroidX: si faltan, se resuelven desde los POM oficiales.
if [ ! -f "$DEPS_DIR/deps.txt" ]; then
  python3 "$PROJ/tools/resolve_deps.py" \
    androidx.appcompat:appcompat:1.7.0 \
    androidx.core:core-ktx:1.13.1 \
    androidx.biometric:biometric:1.1.0
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/aars" "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

# --- 1) Descomprimir AARs: classes.jar, libs/*.jar, res/, assets/, paquete.
CP_JARS=()
DEX_JARS=()
RES_ZIPS=()
ASSET_DIRS=()
EXTRA_PKGS=()
for aar in "$DEPS_DIR"/*.aar; do
  name="$(basename "$aar" .aar)"
  dest="$BUILD/aars/$name"
  mkdir -p "$dest"
  unzip -qo "$aar" -d "$dest"
  if [ -f "$dest/classes.jar" ]; then
    CP_JARS+=("$dest/classes.jar")
    DEX_JARS+=("$dest/classes.jar")
  fi
  for j in "$dest"/libs/*.jar; do
    [ -f "$j" ] || continue
    CP_JARS+=("$j")
    DEX_JARS+=("$j")
  done
  if [ -d "$dest/res" ] && [ -n "$(ls -A "$dest/res" 2>/dev/null)" ]; then
    "$BT/aapt2" compile --dir "$dest/res" -o "$BUILD/compiled/$name.zip"
    RES_ZIPS+=("$BUILD/compiled/$name.zip")
    pkg="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$dest/AndroidManifest.xml" | head -1)"
    [ -n "$pkg" ] && EXTRA_PKGS+=("$pkg")
  fi
  [ -d "$dest/assets" ] && ASSET_DIRS+=("$dest/assets")
done
# JARs puros (kotlin-stdlib, annotation, coroutines, etc.)
for jar in "$DEPS_DIR"/*.jar; do
  case "$(basename "$jar")" in
    # La stdlib 1.8+ ya los integra; incluirlos duplicaría clases en d8.
    kotlin-stdlib-common-*|kotlin-stdlib-jdk7-*|kotlin-stdlib-jdk8-*) ;;
    *) CP_JARS+=("$jar"); DEX_JARS+=("$jar") ;;
  esac
done

# --- 2) Recursos de la app + enlace con aapt2 (genera R de app y librerías).
# El manifiesto para Gradle usa namespace (sin package=); aapt2 lo exige,
# así que se genera una copia temporal con el package inyectado.
sed 's/<manifest /<manifest package="com.gennis.admin3force" /' \
  "$PROJ/app/src/main/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"

"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o "$BUILD/compiled/app.zip"

EXTRA_ARG=()
if [ "${#EXTRA_PKGS[@]}" -gt 0 ]; then
  EXTRA_ARG=(--extra-packages "$(IFS=,; echo "${EXTRA_PKGS[*]}")")
fi
ASSET_ARGS=()
for d in "${ASSET_DIRS[@]:-}"; do
  [ -n "$d" ] && ASSET_ARGS+=(-A "$d")
done

"$BT/aapt2" link \
  -o "$BUILD/base.apk" \
  --manifest "$BUILD/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  --java "$BUILD/gen" \
  --auto-add-overlay \
  "${EXTRA_ARG[@]}" \
  "${ASSET_ARGS[@]}" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name "0.1.0-login" \
  "$BUILD/compiled/app.zip" "${RES_ZIPS[@]}"

# --- 3) Compilar Kotlin + los R.java generados.
CP="$(IFS=:; echo "${CP_JARS[*]}")"
# shellcheck disable=SC2046
"$TC/kotlinc/bin/kotlinc" \
  -jvm-target 11 \
  -classpath "$ANDROID_JAR:$CP" \
  -d "$BUILD/classes" \
  $(find "$PROJ/app/src/main/java" -name '*.kt') \
  $(find "$BUILD/gen" -name '*.java')

# --- 4) Dexar todo (app + librerías + stdlib).
# OJO 1: d8 rechaza el DIRECTORIO de clases porque kotlinc deja dentro
# ficheros .kotlin_module ("Unsupported source file type"); empaquetadas en
# un jar, esas entradas se tratan como recursos y todo va bien.
# OJO 2: el d8 de build-tools 34.0.0 (R8 8.2.2-dev) tiene un bug que lo hace
# reventar con NPE sobre clases de appcompat 1.7.0; AGP nunca usa ese d8,
# usa su propio R8. Aquí se usa el R8 8.5.35 suelto (el que corresponde a
# AGP 8.5), descargado de Google Maven a ~/workspace/toolchain/r8/.
( cd "$BUILD/classes" && jar --create --file "$BUILD/classes.jar" . )
java -cp "$TC/r8/r8-8.5.35.jar" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" \
  "$BUILD/classes.jar" "${DEX_JARS[@]}"

# --- 5) Empaquetar, alinear y firmar con la clave debug.
cp "$BUILD/base.apk" "$BUILD/app-unsigned.apk"
( cd "$BUILD/dex" && zip -q "$BUILD/app-unsigned.apk" classes*.dex )
"$BT/zipalign" -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

KEYSTORE="$TC/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10957 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" -noprompt
fi
"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$BUILD/Admin3FORCE-login-debug.apk" "$BUILD/app-aligned.apk"
"$BT/apksigner" verify "$BUILD/Admin3FORCE-login-debug.apk"

cp "$BUILD/Admin3FORCE-login-debug.apk" "$OUT_APK"
echo "APK generado: $OUT_APK"
ls -la "$OUT_APK"
