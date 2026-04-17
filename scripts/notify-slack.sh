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

command -v curl    &>/dev/null || { log_warn "curl no encontrado — saltando notificación"; exit 0; }
command -v python3 &>/dev/null || { log_warn "python3 no encontrado — saltando notificación"; exit 0; }

# ─── Leer resultados y construir payload ──────────────────────────────────────
log_info "Preparando mensaje Slack..."

PAYLOAD=$(PYTHONUTF8=1 python3 - "$RESULTS_JSON" "$DEVICE_INFO" "$RUN_URL" "$SDK_VERSION" <<'PYEOF'
import json, sys, datetime

results_json = sys.argv[1]
device_info  = sys.argv[2]
run_url      = sys.argv[3]
sdk_version  = sys.argv[4]

try:
    with open(results_json) as f:
        data = json.load(f)
except Exception as e:
    print(json.dumps({"text": f":warning: SDK QA: no se pudo leer resultados ({e})"}))
    sys.exit(0)

summary  = data.get("summary", {})
total    = summary.get("total",    0)
passed   = summary.get("passed",   0)
failed   = summary.get("failed",   0)
duration = summary.get("duration", "?")
tests    = data.get("tests", [])
now      = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")

all_passed = failed == 0
header_emoji = ":white_check_mark:" if all_passed else ":red_circle:"
header_text  = f"SDK QA Smoke — {'PASÓ' if all_passed else 'FALLÓ'}"
color        = "#22c55e" if all_passed else "#ef4444"

# Campos del resumen
fields = [
    {"type": "mrkdwn", "text": f"*Tests:*\n✓ {passed} pasaron   ✗ {failed} fallaron"},
    {"type": "mrkdwn", "text": f"*Duración:*\n{duration}"},
    {"type": "mrkdwn", "text": f"*SDK Version:*\n`{sdk_version}`"},
    {"type": "mrkdwn", "text": f"*Device:*\n{device_info}"},
    {"type": "mrkdwn", "text": f"*Fecha:*\n{now}"},
]

blocks = [
    {
        "type": "header",
        "text": {"type": "plain_text", "text": f"{header_emoji}  {header_text}", "emoji": True}
    },
    {
        "type": "section",
        "fields": fields
    }
]

# Lista de fallos (máx 10 para no saturar el mensaje)
failed_tests = [t for t in tests if t.get("status") == "failed"]
if failed_tests:
    fail_lines = []
    for t in failed_tests[:10]:
        name  = t.get("name", "")
        cls   = t.get("class", "").split(".")[-1]
        error = t.get("error", "")
        # Primera línea del error (la más informativa)
        first_line = error.strip().split("\n")[0][:120] if error else ""
        fail_lines.append(f"• *{cls}.{name}*\n  `{first_line}`" if first_line else f"• *{cls}.{name}*")
    if len(failed_tests) > 10:
        fail_lines.append(f"_... y {len(failed_tests) - 10} más_")

    blocks.append({
        "type": "section",
        "text": {"type": "mrkdwn", "text": "*Tests fallidos:*\n" + "\n".join(fail_lines)}
    })

# Botón con link al run (si está disponible)
if run_url:
    blocks.append({
        "type": "actions",
        "elements": [{
            "type": "button",
            "text": {"type": "plain_text", "text": "Ver detalles →", "emoji": True},
            "url": run_url,
            "style": "primary" if all_passed else "danger"
        }]
    })

blocks.append({"type": "divider"})

payload = {
    "attachments": [{
        "color": color,
        "blocks": blocks
    }]
}

print(json.dumps(payload, ensure_ascii=False))
PYEOF
)

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
