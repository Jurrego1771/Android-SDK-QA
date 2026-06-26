#!/usr/bin/env bash
# =============================================================================
# notify-slack.sh — Envía resumen de resultados de tests a un canal de Slack
#
# Uso:
#   ./scripts/notify-slack.sh <results_json> <device_info> <run_url>
#
# Variables de entorno requeridas:
#   SLACK_WEBHOOK_URL  — URL del Incoming Webhook de Slack
#
# Ejemplo:
#   SLACK_WEBHOOK_URL="https://hooks.slack.com/..." \
#     ./scripts/notify-slack.sh ai-output/test-results.json "Pixel 6 API34" ""
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_JSON="${1:-ai-output/test-results.json}"
DEVICE_INFO="${2:-dispositivo local}"
RUN_URL="${3:-}"
SDK_VERSION="${4:-desconocida}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info() { echo -e "\033[0;34m[INFO]\033[0m  $*"; }
log_ok()   { echo -e "${GREEN}[ OK ]\033[0m  $*"; }
log_warn() { echo -e "${YELLOW}[WARN]\033[0m  $*"; }

# ─── Validar webhook ──────────────────────────────────────────────────────────
if [[ -z "${SLACK_WEBHOOK_URL:-}" ]]; then
    log_warn "SLACK_WEBHOOK_URL no definida — saltando notificación Slack"
    exit 0
fi

command -v curl &>/dev/null || { log_warn "curl no encontrado — saltando notificación"; exit 0; }
command -v node &>/dev/null || { log_warn "node no encontrado — saltando notificación"; exit 0; }

# ─── Leer resultados y construir payload ──────────────────────────────────────
log_info "Preparando mensaje Slack..."

# Prefiere report.json (estructurado, con el cambio + veredictos + clasificación de fallos);
# si no existe, cae al test-results.json crudo (compat). El titular distingue REALES de flaky/entorno.
REPORT_JSON="$(dirname "$RESULTS_JSON")/report.json"
PAYLOAD=$(node "${SCRIPT_DIR}/slack-payload.cjs" "$RESULTS_JSON" "$DEVICE_INFO" "$RUN_URL" "$SDK_VERSION" "$REPORT_JSON")

# ─── Enviar a Slack ───────────────────────────────────────────────────────────
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST \
    -H 'Content-Type: application/json' \
    --data "$PAYLOAD" \
    "$SLACK_WEBHOOK_URL")

if [[ "$HTTP_CODE" == "200" ]]; then
    log_ok "Notificación enviada a Slack (HTTP $HTTP_CODE)"
else
    log_warn "Slack respondió HTTP $HTTP_CODE — verifica el webhook URL"
    exit 1
fi
