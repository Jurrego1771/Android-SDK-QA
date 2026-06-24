#!/usr/bin/env bash
# ============================================================================
# capture-layout-baseline.sh — Bootstrap de la baseline de regresión de layout.
#
# Corre PlayerControlsLayoutRegressionTest en modo CAPTURE (-e captureBaseline true),
# baja los JSON de layout del device y los copia (sin sufijo de versión) a
# app/src/androidTest/assets/layout-baselines/ para commitearlos como baseline.
#
# Uso:
#   1. Fijar en app/build.gradle.kts el SDK BUENO conocido (10.0.7) antes de correr.
#   2. ./scripts/capture-layout-baseline.sh [serial]
#   3. Revisar a OJO los screenshots/el contenido y commitear los .layout.json.
#
# El gate real (assert) corre en --size large o con --class; este script solo captura.
# ============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1   # evita el mangling de /sdcard en Git Bash (Windows)

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

APP_PKG="com.example.sdk_qa"
TEST_CLASS="com.example.sdk_qa.regression.PlayerControlsLayoutRegressionTest"
DEVICE_LAYOUTS="/sdcard/Android/data/${APP_PKG}/files/layouts"
ASSET_DIR="app/src/androidTest/assets/layout-baselines"
PULL_TMP="$(mktemp -d)"

# --- Resolver device serial: arg > ANDROID_SERIAL > único conectado --------
SERIAL="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVS < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
  if [[ ${#DEVS[@]} -eq 1 ]]; then SERIAL="${DEVS[0]}"
  else echo "ERROR: especifica el serial (hay ${#DEVS[@]} devices): $0 <serial>"; exit 1; fi
fi
export ANDROID_SERIAL="$SERIAL"
echo "▶ Device: $SERIAL"
echo "▶ SDK declarado: $(grep -oE 'mediastreamplatformsdkandroid:[^"]+' app/build.gradle.kts | head -1)"

# --- Limpiar layouts previos en el device ----------------------------------
adb -s "$SERIAL" shell rm -rf "$DEVICE_LAYOUTS" 2>/dev/null || true

# --- Build + install (app + test APK) --------------------------------------
echo "▶ Build + install…"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -q
adb -s "$SERIAL" install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$SERIAL" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

# --- Correr la batería en modo CAPTURE vía `am instrument` DIRECTO ----------
# NO usar `gradlew connectedAndroidTest`: el Orchestrator corre con clearPackageData,
# que borra getExternalFilesDir tras cada test → los .layout.json se perderían.
echo "▶ Capturando baseline (modo captureBaseline)…"
adb -s "$SERIAL" shell am instrument -w \
  -e captureBaseline true \
  -e class "$TEST_CLASS" \
  "${APP_PKG}.test/androidx.test.runner.AndroidJUnitRunner"

# --- Bajar los JSON del device ---------------------------------------------
echo "▶ adb pull $DEVICE_LAYOUTS"
adb -s "$SERIAL" pull "$DEVICE_LAYOUTS" "$PULL_TMP" >/dev/null

mkdir -p "$ASSET_DIR"
COUNT=0
for f in "$PULL_TMP"/layouts/*.layout.json; do
  [[ -e "$f" ]] || continue
  bn="$(basename "$f")"
  key="${bn%%-sdk*}"                       # vod-sdk10.0.7.layout.json → vod
  cp "$f" "$ASSET_DIR/${key}.layout.json"
  echo "  ✓ ${key}.layout.json"
  COUNT=$((COUNT+1))
done
rm -rf "$PULL_TMP"

echo ""
echo "✅ $COUNT baseline(s) copiada(s) a $ASSET_DIR"
echo "   REVISA a ojo el layout/screenshot, luego commitea los .layout.json."
echo "   Para validar el gate: ./scripts/run-tests.sh --class PlayerControlsLayoutRegressionTest --size large"
