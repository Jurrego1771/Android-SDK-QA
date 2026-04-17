#!/usr/bin/env bash
# =============================================================================
# daily-smoke.sh — Orquestador del smoke diario automático
#
# Ejecuta el smoke test suite, genera el reporte HTML y notifica a Slack.
# Diseñado para ser llamado por el Task Scheduler de Windows (o cron en Linux/Mac).
#
# Requiere: scripts/.env con SLACK_WEBHOOK_URL definida
#
# Logs en: ai-output/logs/daily-YYYY-MM-DD.log
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

AI_OUTPUT="${PROJECT_ROOT}/ai-output"
LOG_DIR="${AI_OUTPUT}/logs"
LOG_FILE="${LOG_DIR}/daily-$(date +%Y-%m-%d).log"
RESULTS_JSON="${AI_OUTPUT}/test-results.json"

mkdir -p "$LOG_DIR"

# Redirigir stdout + stderr al log (y también a la consola)
exec > >(tee -a "$LOG_FILE") 2>&1

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info() { echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $*"; }
log_ok()   { echo -e "${GREEN}[$(date +%H:%M:%S)] OK${NC} $*"; }
log_warn() { echo -e "${YELLOW}[$(date +%H:%M:%S)] WARN${NC} $*"; }
log_fail() { echo -e "${RED}[$(date +%H:%M:%S)] FAIL${NC} $*"; }

echo ""
echo "═══════════════════════════════════════════════════"
echo "  SDK QA — Daily Smoke  $(date '+%Y-%m-%d %H:%M:%S')"
echo "═══════════════════════════════════════════════════"

# ─── Cargar variables de entorno ──────────────────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ -f "$ENV_FILE" ]]; then
    # shellcheck source=/dev/null
    set -a; source "$ENV_FILE"; set +a
    log_info "Variables cargadas desde scripts/.env"
else
    log_warn "scripts/.env no encontrado — crea el archivo con SLACK_WEBHOOK_URL"
    log_warn "  Ver: scripts/.env.example"
fi

# ─── Verificar device conectado ───────────────────────────────────────────────
log_info "Verificando dispositivo ADB..."
DEVICE_SERIAL=""

if ! command -v adb &>/dev/null; then
    log_fail "adb no encontrado en PATH"
    notify_error "adb no encontrado en PATH del scheduler"
    exit 1
fi

DEVICES=$(adb devices 2>/dev/null | tail -n +2 | grep "device$" | awk '{print $1}' || true)
DEVICE_COUNT=$(echo "$DEVICES" | grep -c . 2>/dev/null || echo 0)

if [[ -z "$DEVICES" || "$DEVICE_COUNT" -eq 0 ]]; then
    log_fail "No hay dispositivos ADB conectados — abortando smoke"
    # Notificar a Slack el problema de infraestructura
    send_infra_alert "No hay dispositivos ADB conectados para el smoke diario."
    exit 1
fi

DEVICE_SERIAL=$(echo "$DEVICES" | head -1)
MODEL=$(adb -s "$DEVICE_SERIAL" shell getprop ro.product.model    2>/dev/null | tr -d '\r\n' || echo "?")
ANDROID=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r\n' || echo "?")
API=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk  2>/dev/null | tr -d '\r\n' || echo "?")
DEVICE_INFO="${MODEL} (Android ${ANDROID} / API ${API})"

log_ok "Device: $DEVICE_INFO  [$DEVICE_SERIAL]"

# ─── Función para alertas de infraestructura ──────────────────────────────────
send_infra_alert() {
    local msg="$1"
    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]] && command -v curl &>/dev/null; then
        curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
            --data "{\"text\":\":warning: *SDK QA Smoke — Error de infraestructura*\n${msg}\"}" \
            "$SLACK_WEBHOOK_URL" || true
    fi
}

# ─── Ejecutar smoke tests ─────────────────────────────────────────────────────
log_info "Iniciando smoke tests..."
TESTS_EXIT=0

bash "${SCRIPT_DIR}/run-tests.sh" \
    --smoke \
    --skip-build \
    --device "$DEVICE_SERIAL" \
    || TESTS_EXIT=$?

# run-tests.sh sale con código 1 si hay fallos (normal — no abortar aquí)
log_info "run-tests.sh terminó con código: $TESTS_EXIT"

# ─── Verificar que el JSON fue generado ───────────────────────────────────────
if [[ ! -f "$RESULTS_JSON" ]]; then
    log_fail "test-results.json no fue generado — fallo en el runner"
    send_infra_alert "run-tests.sh no generó test-results.json. Revisa el log: \`$LOG_FILE\`"
    exit 1
fi

# ─── Enviar notificación a Slack ──────────────────────────────────────────────
log_info "Enviando resultados a Slack..."

bash "${SCRIPT_DIR}/notify-slack.sh" \
    "$RESULTS_JSON" \
    "$DEVICE_INFO" \
    "${SLACK_RUN_URL:-}"

echo ""
if [[ $TESTS_EXIT -eq 0 ]]; then
    log_ok "Daily smoke completado — todos los tests pasaron"
else
    log_warn "Daily smoke completado — hay fallos (ver Slack para detalles)"
fi

echo "═══════════════════════════════════════════════════"
echo ""

exit $TESTS_EXIT
