#!/usr/bin/env bash
# =============================================================================
# explore.sh — Session controller del harness de testing exploratorio (Fase 1)
#
# Selecciona device, asegura el debug build instalado, y corre una exploración
# (deep link → observar → oráculos → reporte) vía el servidor MCP custom.
#
# Uso:
#   ./scripts/explore.sh [--device <serial>] [--scenario <key>] [--skip-build]
#
# Ejemplos:
#   ./scripts/explore.sh --scenario vod
#   ./scripts/explore.sh --device R5CTB1W92KY --scenario vod --skip-build
#
# Claves de escenario: vod live livedvr ads episode pip drm reels ... (ver
# DeepLinkRouterActivity.SCENARIOS). Salida en ai-output/exploration-<ts>/.
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
log()  { echo -e "${CYAN}[explore]${NC} $*"; }
warn() { echo -e "${YELLOW}[explore]${NC} $*"; }
die()  { echo -e "${RED}[explore]${NC} $*" >&2; exit 1; }

DEVICE_SERIAL=""
SCENARIO="vod"
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --device)   DEVICE_SERIAL="$2"; shift 2 ;;
    --scenario) SCENARIO="$2";      shift 2 ;;
    --skip-build) SKIP_BUILD=true;  shift   ;;
    -h|--help)  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Opción desconocida: $1" ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MCP_DIR="${PROJECT_ROOT}/tools/exploratory-mcp"
APK_APP="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"

command -v adb &>/dev/null || die "adb no encontrado en PATH."
command -v node &>/dev/null || die "node no encontrado en PATH."

if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
  GRADLEW="${PROJECT_ROOT}/gradlew.bat"
else
  GRADLEW="${PROJECT_ROOT}/gradlew"
fi

# ── Selección de device ───────────────────────────────────────────────────────
if [[ -z "$DEVICE_SERIAL" ]]; then
  mapfile -t _devs < <(adb devices | tail -n +2 | grep -w device | awk '{print $1}')
  [[ ${#_devs[@]} -eq 0 ]] && die "Sin dispositivos conectados (adb devices)."
  [[ ${#_devs[@]} -gt 1 ]] && die "Múltiples dispositivos: ${_devs[*]}. Especifica --device."
  DEVICE_SERIAL="${_devs[0]}"
fi
log "Device: ${DEVICE_SERIAL}"

# ── Build + install (debug) ───────────────────────────────────────────────────
if [[ "$SKIP_BUILD" == true ]]; then
  warn "Saltando build (--skip-build)"
  [[ -f "$APK_APP" ]] || die "APK no encontrado: $APK_APP — corre sin --skip-build."
else
  log "Compilando debug APK..."
  "$GRADLEW" assembleDebug --quiet 2>&1 | grep -E "BUILD|error:|FAILURE" || true
  [[ -f "$APK_APP" ]] || die "Build falló — APK no generado."
fi
log "Instalando app..."
adb -s "$DEVICE_SERIAL" install -r -t "$APK_APP" >/dev/null 2>&1 || die "adb install falló."

# ── Dependencias del MCP server ───────────────────────────────────────────────
if [[ ! -d "${MCP_DIR}/node_modules" ]]; then
  log "Instalando dependencias del servidor MCP..."
  (cd "$MCP_DIR" && npm install --no-audit --no-fund >/dev/null 2>&1) || die "npm install falló en $MCP_DIR"
fi

# ── Exploración ───────────────────────────────────────────────────────────────
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${PROJECT_ROOT}/ai-output/exploration-${TS}"
mkdir -p "$OUT_DIR"
log "Explorando escenario '${SCENARIO}' → ${OUT_DIR}"
echo ""

set +e
ANDROID_SERIAL="$DEVICE_SERIAL" node "${MCP_DIR}/explore.mjs" "$SCENARIO" "$OUT_DIR"
RC=$?
set -e

echo ""
if [[ $RC -eq 0 ]]; then
  echo -e "${GREEN}${BOLD}✓ Exploración completa${NC} — ${OUT_DIR}/report.md"
else
  echo -e "${RED}${BOLD}✗ Hallazgos de severidad alta${NC} — revisa ${OUT_DIR}/report.md"
fi
exit $RC
