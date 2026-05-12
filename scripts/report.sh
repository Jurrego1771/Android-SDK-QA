#!/usr/bin/env bash
# =============================================================================
# report.sh — Genera un HTML report con resultados, screenshots y video
#
# Uso (llamado por run-tests.sh automáticamente, o manual):
#   ./scripts/report.sh <device_serial> <results_json> <output_dir>
#
# El report se genera en: ai-output/report/index.html
# =============================================================================

set -euo pipefail

DEVICE_SERIAL="${1:-}"
RESULTS_JSON="${2:-ai-output/test-results.json}"
OUTPUT_DIR="${3:-ai-output/report}"
APP_PACKAGE="com.example.sdk_qa"
DEVICE_EVIDENCE_DIR="/sdcard/Android/data/${APP_PACKAGE}/files/sdk_qa_evidence"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()   { echo -e "${GREEN}[ OK ]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

mkdir -p "$OUTPUT_DIR/screenshots" "$OUTPUT_DIR/videos"

# ─── 1. Descargar screenshots del device ──────────────────────────────────────
if [[ -n "$DEVICE_SERIAL" ]]; then
    log_info "Descargando screenshots del device..."
    adb -s "$DEVICE_SERIAL" pull \
        "${DEVICE_EVIDENCE_DIR}/screenshots/" \
        "$OUTPUT_DIR/screenshots/" 2>/dev/null \
        && log_ok "Screenshots descargados" \
        || log_warn "No se encontraron screenshots (normal si todos pasaron)"

    # El video lo graba scrcpy directo en el host — no necesita adb pull
    if [[ -f "$OUTPUT_DIR/videos/session.mp4" ]]; then
        log_ok "Video disponible: $OUTPUT_DIR/videos/session.mp4"
    else
        log_warn "No se encontró video de sesión"
    fi
fi

# ─── 2. Leer resultados ───────────────────────────────────────────────────────
if [[ ! -f "$RESULTS_JSON" ]]; then
    log_warn "No se encontró $RESULTS_JSON — generando report vacío"
    echo '{"tests":[],"summary":{"total":0,"passed":0,"failed":0,"duration":"0s"}}' > "$RESULTS_JSON"
fi

# ─── 3. Generar HTML report ───────────────────────────────────────────────────
log_info "Generando HTML report..."

REPORT_DATE=$(date "+%Y-%m-%d %H:%M:%S")
HAS_VIDEO=false
[[ -f "$OUTPUT_DIR/videos/session.mp4" ]] && HAS_VIDEO=true

# Leer JSON con node
_json_field() { node -e "try{const d=JSON.parse(require('fs').readFileSync('$RESULTS_JSON','utf8'));console.log(d.summary['$1']);}catch(e){console.log('?');}" 2>/dev/null || echo "?"; }
TOTAL=$(_json_field total)
PASSED=$(_json_field passed)
FAILED=$(_json_field failed)
DURATION=$(_json_field duration)

# Generar filas de tests desde el JSON
TESTS_HTML=$(node - "$RESULTS_JSON" "$OUTPUT_DIR/screenshots" <<'JSEOF'
const fs = require('fs'), path = require('path');
const [resultsJson, screenshotsDir] = process.argv.slice(2);

let data;
try { data = JSON.parse(fs.readFileSync(resultsJson, 'utf8')); }
catch(_) { process.exit(0); }

const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const statusMap = { passed: ['pass','✓'], failed: ['fail','✗'], skipped: ['skip','⊘'] };

let html = '';
for (const test of (data.tests || [])) {
    const name = test.name || '', cls = test.class || '';
    const status = test.status || 'unknown', dur = test.duration || '';
    const error = test.error || '';
    const [statusClass, statusIcon] = statusMap[status] || ['skip','?'];

    // Screenshot embebido en base64
    let screenshotHtml = '';
    if (status === 'failed' && fs.existsSync(screenshotsDir)) {
        const safe = name.replace(/[ /]/g,'_').slice(0,80);
        const candidates = [
            path.join(screenshotsDir, `${cls}_${safe}.png`),
            path.join(screenshotsDir, `${cls.split('.').pop()}_${safe}.png`),
            ...fs.readdirSync(screenshotsDir)
                .filter(f => f.includes(safe.slice(0,30)))
                .map(f => path.join(screenshotsDir, f))
        ];
        for (const p of candidates) {
            if (fs.existsSync(p)) {
                const b64 = fs.readFileSync(p).toString('base64');
                screenshotHtml = `<div class="screenshot"><img src="data:image/png;base64,${b64}" alt="screenshot" onclick="this.classList.toggle('zoom')"/><p>Screenshot al momento del fallo</p></div>`;
                break;
            }
        }
    }

    let errorHtml = '';
    if (error) {
        const cbFired   = (test.callbacks_fired   || []).join(', ') || 'ninguno';
        const cbMissing = (test.callbacks_missing || []).join(', ') || 'ninguno';
        const cbHtml = (test.callbacks_fired || test.callbacks_missing) ? `
            <div class="callbacks">
                <span class="cb-fired">✓ Recibidos: ${esc(cbFired)}</span>
                <span class="cb-missing">✗ Faltantes: ${esc(cbMissing)}</span>
            </div>` : '';
        errorHtml = `<div class="error-detail"><pre class="error-msg">${esc(error)}</pre>${cbHtml}${screenshotHtml}</div>`;
    }

    const shortCls = cls.split('.').pop();
    html += `\n    <tr class="${statusClass}" onclick="toggleDetail(this)">
        <td class="status-icon">${statusIcon}</td>
        <td class="test-name"><span class="class-name">${esc(shortCls)}</span>.${esc(name)}</td>
        <td class="duration">${esc(dur)}</td>
    </tr>`;
    if (errorHtml) html += `\n<tr class="detail-row ${statusClass}-detail"><td colspan="3">${errorHtml}</td></tr>`;
}
process.stdout.write(html);
JSEOF
2>/dev/null || echo "<!-- no test data -->")

VIDEO_SECTION=""
if $HAS_VIDEO; then
VIDEO_SECTION='<div class="section video-section">
    <h2>Grabación de Sesión</h2>
    <video controls width="100%" style="max-width:800px">
        <source src="videos/session.mp4" type="video/mp4">
        Tu browser no soporta video HTML5.
    </video>
</div>'
fi

cat > "$OUTPUT_DIR/index.html" <<HTML
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>SDK QA Report — $REPORT_DATE</title>
<style>
  :root {
    --pass:  #22c55e; --fail: #ef4444; --skip: #94a3b8;
    --bg:    #0f172a; --surface: #1e293b; --border: #334155;
    --text:  #e2e8f0; --muted: #94a3b8; --accent: #38bdf8;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { background: var(--bg); color: var(--text); font-family: 'Segoe UI', system-ui, sans-serif; font-size: 14px; }

  .header { background: var(--surface); border-bottom: 1px solid var(--border); padding: 20px 32px; display: flex; align-items: center; justify-content: space-between; }
  .header h1 { font-size: 18px; font-weight: 600; }
  .header .date { color: var(--muted); font-size: 12px; }

  .summary { display: flex; gap: 16px; padding: 24px 32px; }
  .stat { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 16px 24px; min-width: 120px; }
  .stat .value { font-size: 32px; font-weight: 700; }
  .stat .label { color: var(--muted); font-size: 12px; margin-top: 4px; }
  .stat.pass .value { color: var(--pass); }
  .stat.fail .value { color: var(--fail); }

  .section { padding: 0 32px 32px; }
  .section h2 { font-size: 14px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 12px; }

  table { width: 100%; border-collapse: collapse; background: var(--surface); border-radius: 8px; overflow: hidden; border: 1px solid var(--border); }
  tr { border-bottom: 1px solid var(--border); }
  tr:last-child { border-bottom: none; }
  tr.pass:hover, tr.fail:hover, tr.skip:hover { background: #ffffff08; cursor: pointer; }
  td { padding: 10px 14px; vertical-align: middle; }

  .status-icon { width: 32px; text-align: center; font-size: 16px; font-weight: 700; }
  tr.pass .status-icon { color: var(--pass); }
  tr.fail .status-icon { color: var(--fail); }
  tr.skip .status-icon { color: var(--skip); }

  .test-name { font-family: 'Consolas', monospace; font-size: 13px; }
  .class-name { color: var(--accent); }
  .duration { color: var(--muted); font-size: 12px; text-align: right; width: 80px; }

  .detail-row { display: none; }
  .detail-row.visible { display: table-row; }
  .detail-row td { padding: 0; }

  .error-detail { background: #1a0a0a; border-top: 2px solid var(--fail); padding: 16px 20px; }
  .error-msg { font-family: 'Consolas', monospace; font-size: 12px; color: #fca5a5; white-space: pre-wrap; overflow-x: auto; margin-bottom: 12px; line-height: 1.6; }

  .callbacks { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; font-family: monospace; font-size: 12px; padding: 8px 12px; background: #0f172a; border-radius: 4px; }
  .cb-fired   { color: var(--pass); }
  .cb-missing { color: var(--fail); }

  .screenshot { margin-top: 12px; }
  .screenshot img { max-width: 360px; border: 2px solid var(--border); border-radius: 6px; cursor: zoom-in; transition: transform 0.2s; display: block; }
  .screenshot img.zoom { max-width: 100%; cursor: zoom-out; }
  .screenshot p { color: var(--muted); font-size: 11px; margin-top: 4px; }

  .video-section video { border: 1px solid var(--border); border-radius: 8px; }

  .badge { display: inline-block; padding: 2px 8px; border-radius: 9999px; font-size: 11px; font-weight: 600; }
  .badge.pass { background: #14532d; color: var(--pass); }
  .badge.fail { background: #450a0a; color: var(--fail); }
</style>
</head>
<body>

<div class="header">
  <div>
    <h1>Mediastream SDK QA — Test Report</h1>
    <div class="date">$REPORT_DATE &nbsp;·&nbsp; Device: ${DEVICE_SERIAL:-local}</div>
  </div>
  <span class="badge $([ "$FAILED" = "0" ] && echo pass || echo fail)">
    $([ "$FAILED" = "0" ] && echo "ALL PASSED" || echo "$FAILED FAILED")
  </span>
</div>

<div class="summary">
  <div class="stat"><div class="value">$TOTAL</div><div class="label">Total</div></div>
  <div class="stat pass"><div class="value">$PASSED</div><div class="label">Pasaron</div></div>
  <div class="stat fail"><div class="value">$FAILED</div><div class="label">Fallaron</div></div>
  <div class="stat"><div class="value">$DURATION</div><div class="label">Duración</div></div>
</div>

$VIDEO_SECTION

<div class="section">
  <h2>Tests</h2>
  <table>
    $TESTS_HTML
  </table>
</div>

<script>
function toggleDetail(row) {
  const next = row.nextElementSibling;
  if (next && next.classList.contains('detail-row')) {
    next.classList.toggle('visible');
  }
}
</script>
</body>
</html>
HTML

log_ok "Report generado: $OUTPUT_DIR/index.html"

# Abrir automáticamente si hay browser disponible
if command -v xdg-open &>/dev/null; then
    xdg-open "$OUTPUT_DIR/index.html" &>/dev/null &
elif command -v open &>/dev/null; then
    open "$OUTPUT_DIR/index.html"
elif command -v explorer.exe &>/dev/null; then
    # Windows (Git Bash)
    explorer.exe "$(wslpath -w "$OUTPUT_DIR/index.html" 2>/dev/null || echo "$OUTPUT_DIR/index.html")" 2>/dev/null || true
fi
