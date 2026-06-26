#!/usr/bin/env node
// ============================================================================
// report-render.cjs — Renderiza los fragmentos HTML del reporte de QA.
//
// Extraído de report.sh porque los heredocs node dentro de $(...) con paréntesis en el cuerpo
// ROMPEN en bash 3.2 (el /bin/bash de macOS): el parser cierra el $() en un ')' del JS. Como .cjs
// es portable a cualquier bash (consistente con parse-junit-xml.cjs, diff-sessions.cjs, etc.).
//
// Uso:  node report-render.cjs <modo> <args…>
//   tests    <results.json> <screenshots_dir>   → filas <tr> de la tabla de tests (+ detalle)
//   sessions <sessions_dir>                       → cards de sesión (gauges QoE + timeline) + <script>__TL
//   change   <report.json>                        → sección "Cambio probado" (veredictos + tests generados)
// Imprime el fragmento a stdout. Si no hay datos, imprime vacío (exit 0).
// ============================================================================
const fs = require('fs'), path = require('path');
const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const mode = process.argv[2];

// ── modo: tests ──────────────────────────────────────────────────────────────
function renderTests(resultsJson, screenshotsDir) {
  let data; try { data = JSON.parse(fs.readFileSync(resultsJson, 'utf8')); } catch (_) { return ''; }
  const pill = { passed: 'pass', failed: 'fail', skipped: 'skip' };
  let html = '';
  for (const test of (data.tests || [])) {
    const name = test.name || '', cls = test.class || '';
    const status = test.status || 'skipped', dur = test.duration || '—';
    const error = test.error || '';
    const p = pill[status] || 'skip';
    let shot = '';
    if (status === 'failed' && screenshotsDir && fs.existsSync(screenshotsDir)) {
      const safe = name.replace(/[ /]/g, '_').slice(0, 80);
      const cands = [
        path.join(screenshotsDir, cls + '_' + safe + '.png'),
        path.join(screenshotsDir, cls.split('.').pop() + '_' + safe + '.png'),
        ...fs.readdirSync(screenshotsDir).filter(f => f.includes(safe.slice(0, 30))).map(f => path.join(screenshotsDir, f)),
      ];
      for (const f of cands) if (fs.existsSync(f)) {
        const b64 = fs.readFileSync(f).toString('base64');
        shot = '<div class="shot"><img src="data:image/png;base64,' + b64 + '" alt="screenshot del fallo" onclick="this.classList.toggle(\'zoom\')"/><p>Screenshot al momento del fallo</p></div>';
        break;
      }
    }
    let detail = '';
    if (error || shot) {
      const fired = (test.callbacks_fired || []).join(', ');
      const missing = (test.callbacks_missing || []).join(', ');
      const cb = (fired || missing) ? '<div class="cb"><span class="cb-ok">✓ recibidos: ' + esc(fired || 'ninguno') + '</span><span class="cb-no">✗ faltantes: ' + esc(missing || 'ninguno') + '</span></div>' : '';
      detail = '<tr class="detail"><td colspan="3"><div class="err">' + (error ? '<pre>' + esc(error) + '</pre>' : '') + cb + shot + '</div></td></tr>';
    }
    const shortCls = cls.split('.').pop();
    html += '<tr class="trow ' + p + (detail ? ' has-detail' : '') + '" onclick="toggleDetail(this)">'
         +  '<td><span class="pill ' + p + '">' + status.slice(0, 4) + '</span></td>'
         +  '<td class="tname"><span class="tcls">' + esc(shortCls) + '</span>.' + esc(name) + '</td>'
         +  '<td class="dur">' + esc(dur) + '</td></tr>' + detail;
  }
  return html || '<tr><td colspan="3" style="color:var(--muted);padding:18px">Sin tests registrados.</td></tr>';
}

// ── modo: sessions ───────────────────────────────────────────────────────────
function renderSessions(dir) {
  if (!dir || !fs.existsSync(dir)) return '';
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.json')).sort();
  if (!files.length) return '';
  const norm = v => (v == null || v < 0) ? null : v;
  const sev = {
    ttff:   v => v == null ? ['', 0] : v < 2000 ? ['good', Math.min(v / 4000, 1)] : v < 4000 ? ['warn', Math.min(v / 4000, 1)] : ['crit', 1],
    rebuf:  v => v == null ? ['', 0] : v === 0 ? ['good', .04] : v <= 2 ? ['warn', v / 3] : ['crit', 1],
    drop:   v => v == null ? ['', 0] : v === 0 ? ['good', .04] : v <= 30 ? ['warn', v / 60] : ['crit', 1],
    switch: v => v == null ? ['', 0] : ['good', Math.min((v || 0) / 10, .4)],
  };
  const gauge = (label, val, unit, cls, frac) => {
    const v = val == null ? '—' : (val + (unit ? '<span class="g-unit"> ' + unit + '</span>' : ''));
    return '<div class="gauge ' + (cls || '') + '"><div class="g-label"><span class="dot-s"></span>' + label + '</div>'
         + '<div class="g-val">' + v + '</div><div class="g-bar"><i style="width:' + Math.round((frac || 0) * 100) + '%"></i></div></div>';
  };
  let cards = '', tl = {};
  files.forEach((f, i) => {
    let d; try { d = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); } catch (_) { return; }
    const m = d.metrics || {}, id = 'tl-' + i;
    const fmt = (d.playback && d.playback.format || '?').toUpperCase();
    const res = m.resolution || '?', codec = m.videoCodec || '';
    const offMain = Array.isArray(d.offMainThread) ? d.offMainThread.length : 0;
    const threadChip = offMain === 0 ? '<span class="chip thread-ok">● main-only</span>' : '<span class="chip thread-bad">● ' + offMain + ' off-main</span>';
    const errs = (d.timeline || []).filter(e => e.type === 'player_error' || e.type === 'load_error').length;
    const failed = errs > 0 || (m.loadErrorCount > 0) || norm(m.ttffMs) == null;
    const outcomeChip = failed ? '<span class="chip thread-bad">● con errores' + (errs ? ' (' + errs + ')' : '') + '</span>' : '';
    const [tc, tf] = sev.ttff(norm(m.ttffMs)), [rc, rf] = sev.rebuf(norm(m.rebufferCount)),
          [dc, df] = sev.drop(norm(m.droppedFrames)), [sc, sf] = sev.switch(norm(m.bitrateSwitches));
    cards += '<div class="session"><div class="session-head">'
      + '<h3>' + esc(d.scenario || f) + '</h3>'
      + '<span class="chip fmt">' + esc(fmt) + '</span>'
      + '<span class="chip">' + esc(res) + (codec ? ' · ' + esc(codec) : '') + '</span>'
      + '<span class="spacer"></span>' + outcomeChip + threadChip
      + '<a href="sessions/' + esc(f) + '" class="chip">JSON ↗</a></div>'
      + '<div class="gauges">'
      + gauge('TTFF', norm(m.ttffMs), 'ms', tc, tf)
      + gauge('Rebuffers', norm(m.rebufferCount), '', rc, rf)
      + gauge('Dropped frames', norm(m.droppedFrames), '', dc, df)
      + gauge('Bitrate switches', norm(m.bitrateSwitches), '', sc, sf)
      + '</div><div class="tl-wrap"><div class="tl" id="' + id + '"></div></div>'
      + '<div class="legend"><span><i style="background:var(--sdk)"></i>callback SDK</span>'
      + '<span><i style="background:var(--exo)"></i>evento ExoPlayer</span>'
      + '<span style="color:var(--accent)">▭ TTFF (0 → first frame)</span></div></div>';
    const tline = (d.timeline || []).map(e => ({ off: e.offsetMs || 0, src: e.source === 'exo' ? 'exo' : 'sdk', type: e.type || '?', hi: e.type === 'first_frame' }));
    const total = tline.length ? Math.max(...tline.map(e => e.off), 1) : 1;
    const ff = tline.find(e => e.type === 'first_frame');
    const ttffOff = ff ? ff.off : (norm(m.ttffMs) || 0);
    tline.forEach((e, k) => e.up = (k % 2 === 0));
    tl[id] = { total, ttff: ttffOff, events: tline };
  });
  cards += '<script>window.__TL=' + JSON.stringify(tl) + ';</script>';
  return cards;
}

// ── modo: change (sección "Cambio probado" desde report.json) ─────────────────
function renderChange(reportJson) {
  let R; try { R = JSON.parse(fs.readFileSync(reportJson, 'utf8')); } catch (_) { return ''; }
  const ch = R.change || {}, v = R.verdicts || {}, c = R.counts || {}, gen = R.generated_tests || [];
  const pill = s => s === 'PASS' ? '<span class="pill pass">pasa</span>' : s === 'FAIL' ? '<span class="pill fail">falla</span>' : '<span class="pill skip">—</span>';
  let gh = '';
  if (gen.length) {
    gh = '<table style="margin-top:10px"><thead><tr><th>Test generado</th><th>Tipo</th><th>Feature</th></tr></thead><tbody>'
       + gen.map(g => '<tr><td class="tname">' + esc(g.name) + '</td><td><span class="pill skip">' + esc(g.type) + '</span></td><td>' + esc(g.feature || '') + '</td></tr>').join('')
       + '</tbody></table>';
  }
  return '<section><div class="eyebrow">Cambio probado</div>'
    + '<div class="session"><div class="session-head"><h3>' + esc(ch.summary || '(sin resumen)') + '</h3>'
    + '<span class="chip fmt">' + esc(ch.sdk_version || '?') + '</span><span class="chip">' + esc(ch.change_type || '?') + '</span></div>'
    + '<div class="gauges">'
    + '<div class="gauge"><div class="g-label">Veredicto cambio</div><div class="g-val">' + pill(v.change) + '</div></div>'
    + '<div class="gauge"><div class="g-label">Veredicto regresión</div><div class="g-val">' + pill(v.regression) + '</div></div>'
    + '<div class="gauge ' + ((c.real_failures || 0) > 0 ? 'crit' : 'good') + '"><div class="g-label">Fallos reales</div><div class="g-val">' + (c.real_failures || 0) + '</div></div>'
    + '<div class="gauge warn"><div class="g-label">Flaky · entorno</div><div class="g-val">' + (c.flaky || 0) + ' · ' + (c.environment || 0) + '</div></div>'
    + '</div>' + gh + '</div></section>';
}

let out = '';
if (mode === 'tests')         out = renderTests(process.argv[3], process.argv[4]);
else if (mode === 'sessions') out = renderSessions(process.argv[3]);
else if (mode === 'change')   out = renderChange(process.argv[3]);
else { console.error('modo desconocido: ' + mode); process.exit(2); }
process.stdout.write(out);
