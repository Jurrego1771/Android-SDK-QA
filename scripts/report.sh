#!/usr/bin/env bash
# =============================================================================
# report.sh — Genera un HTML report (tema claro) con resultados, sesiones y evidencia.
#
# Uso (llamado por run-tests.sh automáticamente, o manual):
#   ./scripts/report.sh <device_serial> <results_json> <output_dir>
#
# El report se genera en: ai-output/report/index.html
# Diseño: dashboard claro con timeline de sesión (waterfall de eventos) + gauges QoE
# color-coded por umbral de industria. Datos: test-results.json + sessions/*.json (schema v2).
# =============================================================================

set -uo pipefail

DEVICE_SERIAL="${1:-}"
RESULTS_JSON="${2:-ai-output/test-results.json}"
OUTPUT_DIR="${3:-ai-output/report}"
AI_OUTPUT="$(dirname "$OUTPUT_DIR")"   # report.json/analysis.md viven en ai-output/ (padre de report/)
APP_PACKAGE="com.example.sdk_qa"
DEVICE_EVIDENCE_DIR="/sdcard/Android/data/${APP_PACKAGE}/files/sdk_qa_evidence"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
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

# ─── 3. Metadatos del header ──────────────────────────────────────────────────
log_info "Generando HTML report..."
REPORT_DATE=$(date "+%Y-%m-%d %H:%M")
HAS_VIDEO=false
[[ -f "$OUTPUT_DIR/videos/session.mp4" ]] && HAS_VIDEO=true

# Versión del SDK bajo test (única casa: build.gradle.kts)
SDK_VER=$(grep -oE 'mediastreamplatformsdkandroid:[^"]+' "${PROJECT_ROOT}/app/build.gradle.kts" 2>/dev/null | head -1 | cut -d: -f2)
SDK_VER="${SDK_VER:-?}"

# Label del device (getprop si hay serial; si no, "local")
if [[ -n "$DEVICE_SERIAL" ]]; then
    _P=$(adb -s "$DEVICE_SERIAL" shell "getprop ro.product.model && getprop ro.build.version.release && getprop ro.build.version.sdk" 2>/dev/null | tr -d '\r')
    _MODEL=$(sed -n '1p' <<< "$_P"); _REL=$(sed -n '2p' <<< "$_P"); _API=$(sed -n '3p' <<< "$_P")
    DEVICE_LABEL="${_MODEL:-device} · Android ${_REL:-?} (API ${_API:-?})"
else
    DEVICE_LABEL="local"
fi

# Run / evento (si corre en GitHub Actions)
RUN_LABEL=""
[[ -n "${GITHUB_RUN_NUMBER:-}" ]] && RUN_LABEL="#${GITHUB_RUN_NUMBER} · ${GITHUB_EVENT_NAME:-ci}"

_json_field() { node -e "try{const d=JSON.parse(require('fs').readFileSync('$RESULTS_JSON','utf8'));console.log(d.summary['$1']);}catch(e){console.log('?');}" 2>/dev/null || echo "?"; }
TOTAL=$(_json_field total)
PASSED=$(_json_field passed)
FAILED=$(_json_field failed)
DURATION=$(_json_field duration)

# ─── 4. Filas de tests (con detalle expandible: error + screenshot embebido) ──
TESTS_HTML=$(node - "$RESULTS_JSON" "$OUTPUT_DIR/screenshots" <<'JSEOF'
const fs = require('fs'), path = require('path');
const [resultsJson, screenshotsDir] = process.argv.slice(2);
let data; try { data = JSON.parse(fs.readFileSync(resultsJson, 'utf8')); } catch(_) { process.exit(0); }
const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const pill = { passed:'pass', failed:'fail', skipped:'skip' };
let html = '';
for (const test of (data.tests || [])) {
    const name = test.name || '', cls = test.class || '';
    const status = test.status || 'skipped', dur = test.duration || '—';
    const error = test.error || '';
    const p = pill[status] || 'skip';

    let shot = '';
    if (status === 'failed' && fs.existsSync(screenshotsDir)) {
        const safe = name.replace(/[ /]/g,'_').slice(0,80);
        const cands = [
            path.join(screenshotsDir, cls + '_' + safe + '.png'),
            path.join(screenshotsDir, cls.split('.').pop() + '_' + safe + '.png'),
            ...fs.readdirSync(screenshotsDir).filter(f => f.includes(safe.slice(0,30))).map(f => path.join(screenshotsDir, f))
        ];
        for (const f of cands) if (fs.existsSync(f)) {
            const b64 = fs.readFileSync(f).toString('base64');
            shot = '<div class="shot"><img src="data:image/png;base64,'+b64+'" alt="screenshot del fallo" onclick="this.classList.toggle(\'zoom\')"/><p>Screenshot al momento del fallo</p></div>';
            break;
        }
    }
    let detail = '';
    if (error || shot) {
        const fired = (test.callbacks_fired || []).join(', ');
        const missing = (test.callbacks_missing || []).join(', ');
        const cb = (fired || missing) ? '<div class="cb"><span class="cb-ok">✓ recibidos: '+esc(fired||'ninguno')+'</span><span class="cb-no">✗ faltantes: '+esc(missing||'ninguno')+'</span></div>' : '';
        detail = '<tr class="detail"><td colspan="3"><div class="err">'+(error?'<pre>'+esc(error)+'</pre>':'')+cb+shot+'</div></td></tr>';
    }
    const shortCls = cls.split('.').pop();
    html += '<tr class="trow '+p+(detail?' has-detail':'')+'" onclick="toggleDetail(this)">'
         +  '<td><span class="pill '+p+'">'+status.slice(0,4)+'</span></td>'
         +  '<td class="tname"><span class="tcls">'+esc(shortCls)+'</span>.'+esc(name)+'</td>'
         +  '<td class="dur">'+esc(dur)+'</td></tr>' + detail;
}
process.stdout.write(html || '<tr><td colspan="3" style="color:var(--muted);padding:18px">Sin tests registrados.</td></tr>');
JSEOF
)
[[ -n "$TESTS_HTML" ]] || TESTS_HTML='<tr><td colspan="3">—</td></tr>'

# ─── 5. Sesiones: cards (head + gauges QoE) + datos del timeline para el JS ────
SESSIONS_CARDS=$(node - "$OUTPUT_DIR/sessions" <<'JSESS'
const fs = require('fs'), path = require('path');
const dir = process.argv[2];
if (!fs.existsSync(dir)) process.exit(0);
const files = fs.readdirSync(dir).filter(f => f.endsWith('.json')).sort();
if (!files.length) process.exit(0);
const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

// Normaliza métricas "no medidas": el SDK reporta -1 cuando nunca se alcanzó (p.ej. ttff si
// la sesión falló antes del primer frame). -1/null → n/a (no se pinta como "good").
const norm = v => (v==null || v<0) ? null : v;
// Severidad QoE por umbral de industria (alineado al HUD del debug panel). null = n/a neutral.
const sev = {
  ttff:   v => v==null?['',0] : v<2000?['good',Math.min(v/4000,1)] : v<4000?['warn',Math.min(v/4000,1)] : ['crit',1],
  rebuf:  v => v==null?['',0] : v===0?['good',.04] : v<=2?['warn',v/3] : ['crit',1],
  drop:   v => v==null?['',0] : v===0?['good',.04] : v<=30?['warn',v/60] : ['crit',1],
  switch: v => v==null?['',0] : ['good',Math.min((v||0)/10,.4)],
};
function gauge(label, val, unit, cls, frac){
  const v = val==null ? '—' : (val + (unit?'<span class="g-unit"> '+unit+'</span>':''));
  return '<div class="gauge '+(cls||'')+'"><div class="g-label"><span class="dot-s"></span>'+label+'</div>'
       + '<div class="g-val">'+v+'</div><div class="g-bar"><i style="width:'+Math.round((frac||0)*100)+'%"></i></div></div>';
}
let cards = '', tl = {};
files.forEach((f, i) => {
  let d; try { d = JSON.parse(fs.readFileSync(path.join(dir,f),'utf8')); } catch(_) { return; }
  const m = d.metrics || {}, id = 'tl-'+i;
  const fmt = (d.playback && d.playback.format || '?').toUpperCase();
  const res = m.resolution || '?', codec = m.videoCodec || '';
  const offMain = Array.isArray(d.offMainThread) ? d.offMainThread.length : 0;
  const threadChip = offMain===0
    ? '<span class="chip thread-ok">● main-only</span>'
    : '<span class="chip thread-bad">● '+offMain+' off-main</span>';
  // Estado de la sesión: errores de carga/player en el timeline → falló.
  const errs = (d.timeline||[]).filter(e => e.type==='player_error' || e.type==='load_error').length;
  const failed = errs>0 || (m.loadErrorCount>0) || norm(m.ttffMs)==null;
  const outcomeChip = failed ? '<span class="chip thread-bad">● con errores'+(errs?' ('+errs+')':'')+'</span>' : '';
  const [tc,tf] = sev.ttff(norm(m.ttffMs)), [rc,rf] = sev.rebuf(norm(m.rebufferCount)),
        [dc,df] = sev.drop(norm(m.droppedFrames)), [sc,sf] = sev.switch(norm(m.bitrateSwitches));
  cards += '<div class="session"><div class="session-head">'
    + '<h3>'+esc(d.scenario||f)+'</h3>'
    + '<span class="chip fmt">'+esc(fmt)+'</span>'
    + '<span class="chip">'+esc(res)+(codec?' · '+esc(codec):'')+'</span>'
    + '<span class="spacer"></span>'+outcomeChip+threadChip
    + '<a href="sessions/'+esc(f)+'" class="chip">JSON ↗</a></div>'
    + '<div class="gauges">'
    + gauge('TTFF', norm(m.ttffMs), 'ms', tc, tf)
    + gauge('Rebuffers', norm(m.rebufferCount), '', rc, rf)
    + gauge('Dropped frames', norm(m.droppedFrames), '', dc, df)
    + gauge('Bitrate switches', norm(m.bitrateSwitches), '', sc, sf)
    + '</div><div class="tl-wrap"><div class="tl" id="'+id+'"></div></div>'
    + '<div class="legend"><span><i style="background:var(--sdk)"></i>callback SDK</span>'
    + '<span><i style="background:var(--exo)"></i>evento ExoPlayer</span>'
    + '<span style="color:var(--accent)">▭ TTFF (0 → first frame)</span></div></div>';

  // Datos del timeline para el render JS del browser.
  const tline = (d.timeline || []).map(e => ({
    off: e.offsetMs||0, src: e.source==='exo'?'exo':'sdk', type: e.type||'?',
    hi: e.type==='first_frame'
  }));
  const total = tline.length ? Math.max(...tline.map(e=>e.off), 1) : 1;
  const ff = tline.find(e=>e.type==='first_frame');
  const ttffOff = ff ? ff.off : (norm(m.ttffMs) || 0);   // barra TTFF: evento first_frame o metric
  // alternar etiqueta arriba/abajo para que no se pisen
  tline.forEach((e,k)=> e.up = (k%2===0));
  tl[id] = { total, ttff: ttffOff, events: tline };
});
cards += '<script>window.__TL=' + JSON.stringify(tl) + ';</script>';
process.stdout.write(cards);
JSESS
)

NUM_SESSIONS=$(ls "$OUTPUT_DIR/sessions"/*.json 2>/dev/null | wc -l | tr -d ' ')

VIDEO_SECTION=""
if $HAS_VIDEO; then
VIDEO_SECTION='<section><div class="eyebrow">Grabación de sesión</div>
  <video controls style="width:100%;max-width:840px;border:1px solid var(--border);border-radius:10px">
    <source src="videos/session.mp4" type="video/mp4">Tu navegador no soporta video HTML5.
  </video></section>'
fi

SESSIONS_SECTION=""
[[ -n "$SESSIONS_CARDS" ]] && SESSIONS_SECTION="<section><div class=\"eyebrow\">Sesiones de reproducción · timeline + QoE</div>${SESSIONS_CARDS}</section>"

# ─── Sección "Cambio probado" + veredictos + tests generados (de report.json) ──
# La historia para el dev: QUÉ se probó, los 2 veredictos, qué se generó. Solo si hay report.json.
CHANGE_SECTION=$(node - "${AI_OUTPUT}/report.json" <<'JCHG'
const fs=require('fs'); let R; try{R=JSON.parse(fs.readFileSync(process.argv[2],'utf8'))}catch{process.exit(0)}
const esc=s=>String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const ch=R.change||{}, v=R.verdicts||{}, c=R.counts||{}, gen=R.generated_tests||[];
const pill=s=>s==='PASS'?'<span class="pill pass">pasa</span>':s==='FAIL'?'<span class="pill fail">falla</span>':'<span class="pill skip">—</span>';
let gh=''; if(gen.length){ gh='<table style="margin-top:10px"><thead><tr><th>Test generado</th><th>Tipo</th><th>Feature</th></tr></thead><tbody>'+
  gen.map(g=>`<tr><td class="tname">${esc(g.name)}</td><td><span class="pill skip">${esc(g.type)}</span></td><td>${esc(g.feature||'')}</td></tr>`).join('')+'</tbody></table>'; }
process.stdout.write(
 '<section><div class="eyebrow">Cambio probado</div>'+
 '<div class="session"><div class="session-head"><h3>'+esc(ch.summary||'(sin resumen)')+'</h3>'+
 '<span class="chip fmt">'+esc(ch.sdk_version||'?')+'</span><span class="chip">'+esc(ch.change_type||'?')+'</span></div>'+
 '<div class="gauges">'+
 '<div class="gauge"><div class="g-label">Veredicto cambio</div><div class="g-val">'+pill(v.change)+'</div></div>'+
 '<div class="gauge"><div class="g-label">Veredicto regresión</div><div class="g-val">'+pill(v.regression)+'</div></div>'+
 '<div class="gauge '+((c.real_failures||0)>0?'crit':'good')+'"><div class="g-label">Fallos reales</div><div class="g-val">'+(c.real_failures||0)+'</div></div>'+
 '<div class="gauge warn"><div class="g-label">Flaky · entorno</div><div class="g-val">'+(c.flaky||0)+' · '+(c.environment||0)+'</div></div>'+
 '</div>'+gh+'</div></section>'
);
JCHG
)

# ─── 6. Render del HTML (heredoc SIN comillas: expande \$VAR de bash) ─────────
# OJO: el render JS del timeline usa concatenación (no template literals) para no chocar con
# la expansión del heredoc. No introducir `\$` ni backticks crudos en el CSS/JS de abajo.
cat > "$OUTPUT_DIR/index.html" <<HTML
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>SDK QA — Reporte de sesión · $REPORT_DATE</title>
<style>
  :root{
    --bg:#f4f7fb; --surface:#ffffff; --surface-2:#eef2f8; --border:#dde4ee;
    --text:#162038; --muted:#5e6b85; --faint:#97a2ba;
    --accent:#0a72d6; --good:#0f9d6b; --warn:#bd7400; --crit:#d6383a;
    --sdk:#0a72d6; --exo:#7c52e0;
    --shadow:0 1px 2px #1a25380a, 0 4px 14px #1a253810;
    --mono:ui-monospace,"SF Mono","Cascadia Mono","Consolas",monospace;
    --sans:ui-sans-serif,system-ui,"Segoe UI",sans-serif;
  }
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:var(--bg);color:var(--text);font-family:var(--sans);font-size:14px;line-height:1.5;
    background-image:radial-gradient(1100px 520px at 88% -12%, #e3edfa, transparent)}
  .wrap{max-width:1080px;margin:0 auto;padding:0 24px 64px}
  a{color:var(--accent);text-decoration:none} a:hover{text-decoration:underline}
  :focus-visible{outline:2px solid var(--accent);outline-offset:2px;border-radius:4px}
  header{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;
    padding:34px 0 22px;border-bottom:1px solid var(--border);margin-bottom:28px;flex-wrap:wrap}
  .brand{display:flex;align-items:center;gap:12px}
  .brand .dot{width:10px;height:10px;border-radius:50%;flex:none}
  .brand .dot.ok{background:var(--good);box-shadow:0 0 0 4px #0f9d6b22}
  .brand .dot.bad{background:var(--crit);box-shadow:0 0 0 4px #d6383a22}
  .brand h1{font-size:19px;font-weight:650;letter-spacing:-.01em}
  .brand .sub{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.14em;margin-top:2px}
  .meta{display:flex;gap:22px;font-family:var(--mono);font-size:12px}
  .meta div{display:flex;flex-direction:column;gap:3px}
  .meta .k{color:var(--faint);text-transform:uppercase;letter-spacing:.08em;font-size:10px;font-family:var(--sans)}
  .meta .v{color:var(--text);font-variant-numeric:tabular-nums}
  .summary{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:32px}
  .stat{background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:16px 18px;
    position:relative;overflow:hidden;box-shadow:var(--shadow)}
  .stat .label{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.1em}
  .stat .num{font-family:var(--mono);font-size:30px;font-weight:600;font-variant-numeric:tabular-nums;margin-top:6px;letter-spacing:-.02em}
  .stat.ok .num{color:var(--good)} .stat.bad .num{color:var(--crit)}
  .stat .stripe{position:absolute;left:0;top:0;bottom:0;width:3px}
  .stat.ok .stripe{background:var(--good)} .stat.bad .stripe{background:var(--crit)} .stat.neutral .stripe{background:var(--accent)}
  section{margin-bottom:36px}
  .eyebrow{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.16em;margin-bottom:14px;display:flex;align-items:center;gap:10px}
  .eyebrow::after{content:"";flex:1;height:1px;background:var(--border)}
  .session{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:20px 22px;margin-bottom:16px;box-shadow:var(--shadow)}
  .session-head{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:18px}
  .session-head h3{font-size:15px;font-weight:600}
  .chip{font-family:var(--mono);font-size:11px;padding:3px 9px;border-radius:6px;background:var(--surface-2);border:1px solid var(--border);color:var(--muted)}
  .chip.fmt{color:var(--accent);border-color:#bcd8f5;background:#eaf4fe}
  .chip.thread-ok{color:var(--good);border-color:#b6e6d2;background:#e9f8f1}
  .chip.thread-bad{color:var(--crit);border-color:#f3c2c2;background:#fdeaea}
  .session-head .spacer{flex:1}
  .gauges{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:20px}
  .gauge{background:var(--surface-2);border:1px solid var(--border);border-radius:9px;padding:12px 14px}
  .gauge .g-label{font-size:10px;color:var(--muted);text-transform:uppercase;letter-spacing:.08em;display:flex;align-items:center;gap:6px}
  .gauge .g-val{font-family:var(--mono);font-size:22px;font-weight:600;font-variant-numeric:tabular-nums;margin-top:4px}
  .gauge .g-unit{font-size:12px;color:var(--faint);font-weight:400}
  .gauge .g-bar{height:4px;border-radius:2px;background:#1a253814;margin-top:9px;overflow:hidden}
  .gauge .g-bar i{display:block;height:100%;border-radius:2px}
  .dot-s{width:7px;height:7px;border-radius:50%;flex:none;background:var(--faint)}
  .good .g-val,.good .dot-s{color:var(--good)} .good .g-bar i{background:var(--good)} .good .dot-s{background:var(--good)}
  .warn .g-val,.warn .dot-s{color:var(--warn)} .warn .g-bar i{background:var(--warn)} .warn .dot-s{background:var(--warn)}
  .crit .g-val,.crit .dot-s{color:var(--crit)} .crit .g-bar i{background:var(--crit)} .crit .dot-s{background:var(--crit)}
  .tl-wrap{overflow-x:auto}
  .tl{position:relative;min-width:640px;height:96px;margin-top:6px}
  .tl .axis{position:absolute;left:0;right:0;top:54px;height:2px;background:var(--border)}
  .tl .grid{position:absolute;top:0;bottom:18px;width:1px;background:#1a253810}
  .tl .grid span{position:absolute;bottom:-16px;left:0;transform:translateX(-50%);font-family:var(--mono);font-size:10px;color:var(--faint);font-variant-numeric:tabular-nums}
  .tl .ttff{position:absolute;top:48px;height:13px;border-radius:7px;background:linear-gradient(90deg,#0a72d622,#0a72d640);border:1px solid #0a72d655}
  .tl .ttff span{position:absolute;top:-17px;left:50%;transform:translateX(-50%);white-space:nowrap;font-family:var(--mono);font-size:10px;color:var(--accent);font-weight:600}
  .ev{position:absolute;top:54px;transform:translate(-50%,-50%);display:flex;flex-direction:column;align-items:center}
  .ev .pin{width:11px;height:11px;border-radius:50%;border:2px solid var(--surface);position:relative;z-index:2;box-shadow:0 1px 3px #1a253833}
  .ev.sdk .pin{background:var(--sdk)} .ev.exo .pin{background:var(--exo)}
  .ev.hilite .pin{width:14px;height:14px;box-shadow:0 0 0 4px #0a72d622,0 1px 3px #1a253833}
  .ev .lbl{position:absolute;font-family:var(--mono);font-size:10px;white-space:nowrap;color:var(--muted)}
  .ev.up .lbl{bottom:14px} .ev.down .lbl{top:14px}
  .ev .lbl b{color:var(--text);font-weight:600}
  .legend{display:flex;gap:18px;margin-top:8px;font-size:11px;color:var(--muted);font-family:var(--mono);flex-wrap:wrap}
  .legend i{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:5px;vertical-align:middle}
  table{width:100%;border-collapse:collapse;background:var(--surface);border:1px solid var(--border);border-radius:10px;overflow:hidden;box-shadow:var(--shadow)}
  th{text-align:left;font-size:10px;color:var(--muted);text-transform:uppercase;letter-spacing:.1em;padding:11px 16px;border-bottom:1px solid var(--border);font-weight:600;background:var(--surface-2)}
  td{padding:11px 16px;border-bottom:1px solid var(--border);font-size:13px}
  tr:last-child td{border:none}
  .trow.has-detail{cursor:pointer} .trow.has-detail:hover{background:var(--surface-2)}
  .tname{font-family:var(--mono);font-size:12.5px} .tcls{color:var(--accent)}
  td.dur{text-align:right;font-family:var(--mono);color:var(--muted);font-variant-numeric:tabular-nums;width:90px}
  .pill{font-size:10px;font-weight:600;padding:2px 9px;border-radius:9999px;text-transform:uppercase;letter-spacing:.04em}
  .pill.pass{color:var(--good);background:#0f9d6b18} .pill.skip{color:var(--warn);background:#bd740018} .pill.fail{color:var(--crit);background:#d6383a18}
  .detail{display:none} .detail.show{display:table-row}
  .err{background:#fdf4f4;border:1px solid #f3c2c2;border-radius:8px;padding:14px 16px;margin:4px 0}
  .err pre{font-family:var(--mono);font-size:12px;color:#a12a2a;white-space:pre-wrap;line-height:1.6}
  .cb{display:flex;gap:18px;margin-top:10px;font-family:var(--mono);font-size:12px}
  .cb-ok{color:var(--good)} .cb-no{color:var(--crit)}
  .shot{margin-top:12px} .shot img{max-width:300px;border:1px solid var(--border);border-radius:8px;cursor:zoom-in;display:block} .shot img.zoom{max-width:100%;cursor:zoom-out} .shot p{color:var(--muted);font-size:11px;margin-top:4px}
  footer{margin-top:40px;padding-top:18px;border-top:1px solid var(--border);color:var(--faint);font-size:11px;font-family:var(--mono);display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px}
  @media(max-width:720px){.summary,.gauges{grid-template-columns:repeat(2,1fr)}.meta{gap:14px}}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="brand">
      <span class="dot $([ "$FAILED" = "0" ] && echo ok || echo bad)"></span>
      <div><h1>SDK QA — Reporte de sesión</h1><div class="sub">Mediastream Player · caja negra</div></div>
    </div>
    <div class="meta">
      <div><span class="k">SDK</span><span class="v">$SDK_VER</span></div>
      <div><span class="k">Device</span><span class="v">$DEVICE_LABEL</span></div>
      $([ -n "$RUN_LABEL" ] && echo "<div><span class=\"k\">Run</span><span class=\"v\">$RUN_LABEL</span></div>")
      <div><span class="k">Fecha</span><span class="v">$REPORT_DATE</span></div>
    </div>
  </header>

  <div class="summary">
    <div class="stat ok"><span class="stripe"></span><div class="label">Tests pasados</div><div class="num">$PASSED</div></div>
    <div class="stat bad"><span class="stripe"></span><div class="label">Fallidos</div><div class="num">$FAILED</div></div>
    <div class="stat neutral"><span class="stripe"></span><div class="label">Sesiones</div><div class="num">$NUM_SESSIONS</div></div>
    <div class="stat neutral"><span class="stripe"></span><div class="label">Duración</div><div class="num">$DURATION</div></div>
  </div>

  $CHANGE_SECTION

  $SESSIONS_SECTION

  $VIDEO_SECTION

  <section>
    <div class="eyebrow">Tests instrumentados</div>
    <table>
      <thead><tr><th>Estado</th><th>Test</th><th style="text-align:right">Duración</th></tr></thead>
      <tbody>$TESTS_HTML</tbody>
    </table>
  </section>

  <footer>
    <span>Generado por scripts/report.sh · schema sdkqa.session.v2</span>
    <span>Mediastream Platform SDK Android · QA caja negra</span>
  </footer>
</div>

<script>
function toggleDetail(row){
  var n = row.nextElementSibling;
  if (n && n.classList.contains('detail')) n.classList.toggle('show');
}
(function(){
  var TL = window.__TL || {};
  Object.keys(TL).forEach(function(id){
    var s = TL[id], el = document.getElementById(id); if(!el) return;
    var pad = 4, span = 92;
    function x(ms){ return pad + (ms / (s.total||1)) * span; }
    var h = '<div class="axis"></div>';
    for (var t = 0; t <= s.total; t += 1000){
      h += '<div class="grid" style="left:' + x(t) + '%"><span>' + (t/1000).toFixed(0) + 's</span></div>';
    }
    h += '<div class="ttff" style="left:' + x(0) + '%;width:' + (x(s.ttff) - x(0)) + '%"><span>TTFF ' + s.ttff + 'ms</span></div>';
    s.events.forEach(function(e){
      var cls = 'ev ' + e.src + ' ' + (e.up ? 'up' : 'down') + (e.hi ? ' hilite' : '');
      var lbl = e.type === 'first_frame' ? '<b>first frame</b>' : (e.type.indexOf('on') === 0 ? '<b>' + e.type + '</b>' : e.type);
      h += '<div class="' + cls + '" style="left:' + x(e.off) + '%"><span class="pin"></span><span class="lbl">' + lbl + '</span></div>';
    });
    el.innerHTML = h;
  });
})();
</script>
</body>
</html>
HTML

log_ok "Report generado: $OUTPUT_DIR/index.html"

# Abrir automáticamente si hay browser disponible (solo uso local/interactivo)
if [[ -z "${CI:-}" ]]; then
  if command -v xdg-open &>/dev/null; then xdg-open "$OUTPUT_DIR/index.html" &>/dev/null &
  elif command -v open &>/dev/null; then open "$OUTPUT_DIR/index.html"
  elif command -v explorer.exe &>/dev/null; then explorer.exe "$(wslpath -w "$OUTPUT_DIR/index.html" 2>/dev/null || echo "$OUTPUT_DIR/index.html")" 2>/dev/null || true
  fi
fi
