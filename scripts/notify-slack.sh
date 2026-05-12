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

command -v curl &>/dev/null || { log_warn "curl no encontrado — saltando notificación"; exit 0; }
command -v node &>/dev/null || { log_warn "node no encontrado — saltando notificación"; exit 0; }

# ─── Leer resultados y construir payload ──────────────────────────────────────
log_info "Preparando mensaje Slack..."

PAYLOAD=$(node - "$RESULTS_JSON" "$DEVICE_INFO" "$RUN_URL" "$SDK_VERSION" <<'JSEOF'
const fs = require('fs');
const [resultsJson, deviceInfo, runUrl, sdkVersion] = process.argv.slice(2);

let data;
try { data = JSON.parse(fs.readFileSync(resultsJson, 'utf8')); }
catch(e) { console.log(JSON.stringify({text: ':warning: SDK QA: no se pudo leer resultados (' + e.message + ')'})); process.exit(0); }

const s       = data.summary || {};
const passed  = s.passed   || 0;
const failed  = s.failed   || 0;
const duration= s.duration || '?';
const tests   = data.tests || [];
const now     = new Date().toISOString().slice(0,16).replace('T',' ');

const allPassed   = failed === 0;
const headerEmoji = allPassed ? ':white_check_mark:' : ':red_circle:';
const headerText  = 'SDK QA Smoke — ' + (allPassed ? 'PASÓ' : 'FALLÓ');
const color       = allPassed ? '#22c55e' : '#ef4444';

const fields = [
    {type:'mrkdwn', text:`*Tests:*\n✓ ${passed} pasaron   ✗ ${failed} fallaron`},
    {type:'mrkdwn', text:`*Duración:*\n${duration}`},
    {type:'mrkdwn', text:`*SDK Version:*\n\`${sdkVersion}\``},
    {type:'mrkdwn', text:`*Device:*\n${deviceInfo}`},
    {type:'mrkdwn', text:`*Fecha:*\n${now}`},
];

const blocks = [
    {type:'header', text:{type:'plain_text', text:`${headerEmoji}  ${headerText}`, emoji:true}},
    {type:'section', fields}
];

const failedTests = tests.filter(t => t.status === 'failed');
if (failedTests.length) {
    const lines = failedTests.slice(0,10).map(t => {
        const cls = (t.class||'').split('.').pop();
        const first = (t.error||'').trim().split('\n')[0].slice(0,120);
        return first ? `• *${cls}.${t.name}*\n  \`${first}\`` : `• *${cls}.${t.name}*`;
    });
    if (failedTests.length > 10) lines.push(`_... y ${failedTests.length - 10} más_`);
    blocks.push({type:'section', text:{type:'mrkdwn', text:'*Tests fallidos:*\n' + lines.join('\n')}});
}

if (runUrl) blocks.push({type:'actions', elements:[{type:'button',
    text:{type:'plain_text', text:'Ver detalles →', emoji:true},
    url:runUrl, style: allPassed ? 'primary' : 'danger'}]});

blocks.push({type:'divider'});
console.log(JSON.stringify({attachments:[{color, blocks}]}));
JSEOF
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
