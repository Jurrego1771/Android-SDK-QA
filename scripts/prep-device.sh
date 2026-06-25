#!/usr/bin/env bash
# ============================================================================
# prep-device.sh — Deja el device listo para una corrida desatendida (idempotente).
#
# Algunos ajustes (deviceidle disable) se resetean al reiniciar el teléfono, así que el pipeline
# llama a esto al INICIO de cada corrida para garantizar que el cel no se suspenda ni mate la app
# durante los ~30 min de tests.
#
#   - despierta la pantalla
#   - stay_on_while_plugged_in=7 (no se apaga mientras está enchufado)
#   - deshabilita Doze (deep + light) — evita que el SO suspenda ADB/instrumentación
#   - whitelist de la app QA + test runner (no las mata por optimización de batería)
#
# Uso: bash scripts/prep-device.sh [serial]   (sin serial = el único conectado)
# Exit: 0 ok · 2 sin device.
# ============================================================================
set -uo pipefail

DEV="${1:-}"
if [[ -z "$DEV" ]]; then
  DEV="$(adb devices | awk '/\tdevice$/{print $1; exit}')"
fi
[[ -n "$DEV" ]] || { echo "✗ prep-device: ningún device conectado" >&2; exit 2; }

echo "▶ Preparando device $DEV para corrida desatendida…"
adb -s "$DEV" shell input keyevent KEYCODE_WAKEUP                       >/dev/null 2>&1 || true
adb -s "$DEV" shell settings put global stay_on_while_plugged_in 7      >/dev/null 2>&1 || true
adb -s "$DEV" shell dumpsys deviceidle disable                          >/dev/null 2>&1 || true
adb -s "$DEV" shell dumpsys deviceidle whitelist +com.example.sdk_qa    >/dev/null 2>&1 || true
adb -s "$DEV" shell dumpsys deviceidle whitelist +com.example.sdk_qa.test >/dev/null 2>&1 || true

STAY="$(adb -s "$DEV" shell settings get global stay_on_while_plugged_in 2>/dev/null | tr -d '\r')"
WAKE="$(adb -s "$DEV" shell dumpsys power 2>/dev/null | grep -oE 'mWakefulness=[A-Za-z]+' | head -1)"
echo "  ✓ stay_on=${STAY} · ${WAKE}"
[[ "$STAY" == "7" ]] || echo "  ⚠ stay_on no quedó en 7 (revisar)"
exit 0
