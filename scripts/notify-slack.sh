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

# Prefiere report.json (estructurado, con el cambio + veredictos + clasificación de fallos);
# si no existe, cae al test-results.json crudo (compat). El titular distingue REALES de flaky/entorno.
REPORT_JSON="$(dirname "$RESULTS_JSON")/report.json"
PAYLOAD=$(node - "$RESULTS_JSON" "$DEVICE_INFO" "$RUN_URL" "$SDK_VERSION" "$REPORT_JSON" <<'JSEOF'
const fs = require('fs');
const [resultsJson, deviceInfo, runUrl, sdkVersion, reportJson] = process.argv.slice(2);
const now = new Date().toISOString().slice(0,16).replace('T',' ');
const esc = s => String(s||'').replace(/`/g,'').slice(0,140);

let R = null;
try { R = JSON.parse(fs.readFileSync(reportJson, 'utf8')); } catch (_) {}

// ── Camino RICO: report.json del test-analyzer ───────────────────────────────
if (R && R.counts) {
  const c = R.counts, v = R.verdicts || {};
  const real = c.real_failures || 0, flaky = c.flaky || 0, env = c.environment || 0;
  const ok = real === 0;                       // verde si 0 fallos REALES (flaky/entorno no cuentan)
  const color = ok ? '#22c55e' : '#ef4444';
  const emoji = ok ? ':white_check_mark:' : ':red_circle:';
  const verdict = s => s==='PASS'?':white_check_mark: pasa':s==='FAIL'?':x: falla':'—';
  const ch = R.change || {};
  const blocks = [
    {type:'header', text:{type:'plain_text', text:`${emoji}  SDK QA — ${ok?'sin fallos reales':real+' fallo(s) real(es)'}`, emoji:true}},
    {type:'section', text:{type:'mrkdwn', text:
      `*Cambio probado:* ${esc(ch.summary)||'(sin resumen)'}\n`+
      `*Versión:* \`${esc(ch.sdk_version)||sdkVersion}\`  ·  *Tipo:* ${esc(ch.change_type)||'?'}  ·  *Device:* ${esc(deviceInfo)}`}},
    {type:'section', fields:[
      {type:'mrkdwn', text:`*Veredicto cambio:*\n${verdict(v.change)}`},
      {type:'mrkdwn', text:`*Veredicto regresión:*\n${verdict(v.regression)}`},
      {type:'mrkdwn', text:`*Fallos:*\n:x: ${real} reales   :warning: ${flaky} flaky   :globe_with_meridians: ${env} entorno`},
      {type:'mrkdwn', text:`*Pasaron:*\n:white_check_mark: ${c.passed||0}   (${(R.generated_tests||[]).length} generados)`},
    ]},
  ];
  // Top fallos REALES (los que importan al dev), con su error específico.
  const reals = (R.failures||[]).filter(f => f.type==='real' || f.type==='test-defect');
  if (reals.length) {
    const lines = reals.slice(0,5).map(f =>
      `• *${esc(f.test)}*  _(${f.type})_\n  \`${esc(f.error)}\`` + (f.recommendation?`\n  → ${esc(f.recommendation)}`:''));
    if (reals.length>5) lines.push(`_… y ${reals.length-5} más (ver detalle)_`);
    blocks.push({type:'section', text:{type:'mrkdwn', text:'*Fallos reales:*\n'+lines.join('\n')}});
  }
  if (flaky+env>0) blocks.push({type:'context', elements:[{type:'mrkdwn',
    text:`:information_source: ${flaky} flaky + ${env} de entorno NO cuentan como fallo (reintentos/red). Ver detalle.`}]});
  if (runUrl) blocks.push({type:'actions', elements:[{type:'button',
    text:{type:'plain_text', text:'Ver reporte completo →', emoji:true}, url:runUrl, style: ok?'primary':'danger'}]});
  blocks.push({type:'divider'});
  console.log(JSON.stringify({attachments:[{color, blocks}]}));
  process.exit(0);
}

// ── Camino COMPAT: solo test-results.json (sin clasificación) ────────────────
let data;
try { data = JSON.parse(fs.readFileSync(resultsJson, 'utf8')); }
catch(e) { console.log(JSON.stringify({text: ':warning: SDK QA: no se pudo leer resultados (' + e.message + ')'})); process.exit(0); }
const s = data.summary||{}, passed=s.passed||0, failed=s.failed||0, tests=data.tests||[];
const allPassed = failed===0, color = allPassed?'#22c55e':'#ef4444';
const blocks = [
  {type:'header', text:{type:'plain_text', text:`${allPassed?':white_check_mark:':':red_circle:'}  SDK QA — ${allPassed?'PASO':'FALLO'}`, emoji:true}},
  {type:'section', fields:[
    {type:'mrkdwn', text:`*Tests:*\n:white_check_mark: ${passed} pasaron   :x: ${failed} fallaron`},
    {type:'mrkdwn', text:`*SDK:*\n\`${sdkVersion}\``},
    {type:'mrkdwn', text:`*Device:*\n${deviceInfo}`},
    {type:'mrkdwn', text:`*Fecha:*\n${now}`},
  ]},
];
const failedTests = tests.filter(t => t.status==='failed');
if (failedTests.length) blocks.push({type:'section', text:{type:'mrkdwn', text:'*Tests fallidos:*\n'+
  failedTests.slice(0,10).map(t=>`• *${(t.class||'').split('.').pop()}.${t.name}*`+((t.error||'').trim()?`\n  \`${esc((t.error||'').split('\n')[0])}\``:'')).join('\n')}});
if (runUrl) blocks.push({type:'actions', elements:[{type:'button', text:{type:'plain_text', text:'Ver detalles →', emoji:true}, url:runUrl, style: allPassed?'primary':'danger'}]});
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
