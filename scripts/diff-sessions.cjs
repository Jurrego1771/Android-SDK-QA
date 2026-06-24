#!/usr/bin/env node
// ============================================================================
// diff-sessions.cjs — Comparador DETERMINISTA de sesiones entre 2 versiones del SDK.
//
// Lee los JSON normalizados de Capa C (SessionExporter) de una versión BASELINE y una
// versión NUEVA, los empareja por escenario, y produce ai-output/session-diff.md con:
//   - Δ ESTRUCTURAL  : secuencia de tipos de evento (orden de callbacks) — igual / cambió;
//                       eventos nuevos / ausentes. Esto es lo que NO debe variar por red.
//   - Δ MÉTRICAS      : ttffMs, rebufferCount/Ms, bitrateSwitches, droppedFrames, loadErrorCount,
//                       resolution, videoCodec. Los numéricos varían por red → se marcan, no se juzgan.
//
// El comparador NO interpreta (eso lo hace el agente /version-comparator). Solo reporta Δ con un
// flag heurístico: structural-change = candidato a regresión/cambio de comportamiento.
//
// Uso:  node scripts/diff-sessions.cjs <dir-baseline> <dir-nueva> [out.md]
//   ej: node scripts/diff-sessions.cjs ai-output/sessions-10.0.7 ai-output/report/sessions
// Cada dir contiene los <escenario>-sdk<ver>-<ts>.json de esa versión.
// ============================================================================
const fs = require('fs');
const path = require('path');

const [baseDir, newDir, outArg] = process.argv.slice(2);
const OUT = outArg || 'ai-output/session-diff.md';

if (!baseDir || !newDir) {
  console.error('Uso: node scripts/diff-sessions.cjs <dir-baseline> <dir-nueva> [out.md]');
  process.exit(2);
}

// Tolerancias para métricas numéricas (ruido de red, no regresión). Ver CORE-LEARN-015.
const TTFF_TOL_MS = 1500;   // TTFF varía mucho por red/cold-start
const REBUF_TOL_MS = 1500;

// --- Cargar los JSON de un dir, indexados por escenario (último por escenario si hay varios) ---
function loadByScenario(dir) {
  const out = {};
  let files = [];
  try { files = fs.readdirSync(dir).filter(f => f.endsWith('.json')); }
  catch (e) { console.error(`No se pudo leer ${dir}: ${e.message}`); process.exit(2); }
  for (const f of files.sort()) {              // orden alfabético → el ts mayor (más reciente) gana
    let d; try { d = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); } catch { continue; }
    const key = d.scenario || f;
    out[key] = d;                              // sobrescribe con el más reciente
  }
  return out;
}

const seq = d => (d.timeline || []).map(e => `${e.source}:${e.type}`);
const esc = s => String(s);

// --- Gate de completitud: GIGO guard. Distingue una captura CONCLUYENTE de una CORTADA.
// Una captura es concluyente (comparable) si alcanzó un estado terminal observable:
//   (a) renderizó → evento `first_frame` presente (reproducción OK), o
//   (b) falló de verdad → `player_error`/`onPlaybackErrors` (fallo reproducible, comparable entre
//       versiones — `loadErrorCount` es una métrica; un Live caído que sigue caído ES un dato, no ruido).
// Es CORTADA (→ RECAPTURAR) solo si no tiene NINGUNO de los dos: quedó en estado indeterminado
// (p.ej. alpha08 VOD: onReady/onPlay/onPause sin first_frame ni error → la sesión se cerró antes de pintar).
// `truncated=true` también la marca cortada. NO se usa ttffMs como criterio: en un error legítimo
// ttffMs=-1 es correcto (nunca hubo frame porque falló), no señal de captura mala.
function assessCompleteness(d) {
  const types = (d.timeline || []).map(e => e.type);
  const hasFirstFrame = types.includes('first_frame');
  const hasTerminalError = types.includes('player_error') || types.includes('onPlaybackErrors');
  const reasons = [];
  if (d.truncated === true) reasons.push('timeline truncado');
  if (!hasFirstFrame && !hasTerminalError) {
    reasons.push('ni first_frame ni error terminal — sesión cortada en estado indeterminado');
  }
  return { complete: reasons.length === 0, reasons, hasFirstFrame, hasTerminalError };
}

const base = loadByScenario(baseDir);
const neu = loadByScenario(newDir);
const baseVer = Object.values(base)[0]?.sdk?.version || '(baseline)';
const newVer = Object.values(neu)[0]?.sdk?.version || '(nueva)';

const scenarios = [...new Set([...Object.keys(base), ...Object.keys(neu)])].sort();

let md = `# Diff de sesiones — comparación entre versiones del SDK\n\n`;
md += `**Baseline:** ${baseVer}  ·  **Nueva:** ${newVer}\n`;
md += `**Generado:** ${new Date().toISOString()}\n\n`;
md += `> Comparación determinista (script). La interpretación (esperado/regresión/ruido) la hace \`/version-comparator\`.\n`;
md += `> Δ ESTRUCTURAL (orden de callbacks) = señal fuerte; Δ MÉTRICAS numéricas = pueden ser ruido de red (tol TTFF ±${TTFF_TOL_MS}ms).\n\n`;

let structuralChanges = 0;
let incompleteCaptures = 0;
let missingScenarios = 0;

for (const sc of scenarios) {
  const b = base[sc], n = neu[sc];
  md += `## ${esc(sc)}\n\n`;
  if (!b) { md += `⚠️ Sin baseline para este escenario (solo en versión nueva) — set DESALINEADO.\n\n`; missingScenarios++; continue; }
  if (!n) { md += `⚠️ Sin sesión en la versión nueva (solo en baseline) — set DESALINEADO, ¿falló la captura?\n\n`; missingScenarios++; continue; }

  // --- Gate de completitud (GIGO): no diffear capturas que no reprodujeron ---
  const cb = assessCompleteness(b), cn = assessCompleteness(n);
  if (!cb.complete || !cn.complete) {
    incompleteCaptures++;
    md += `🟥 **NO COMPARABLE — captura incompleta → RECAPTURAR.**\n`;
    if (!cb.complete) md += `- baseline (${esc(baseVer)}): ${cb.reasons.join('; ')}\n`;
    if (!cn.complete) md += `- nueva (${esc(newVer)}): ${cn.reasons.join('; ')}\n`;
    md += `- Se omite el diff de este escenario (no se cuenta como cambio estructural).\n\n`;
    continue;
  }

  // --- Δ ESTRUCTURAL: secuencia de tipos de evento ---
  const bs = seq(b), ns = seq(n);
  const sameSeq = bs.join('|') === ns.join('|');
  const newEvents = [...new Set(ns.filter(x => !bs.includes(x)))];
  const goneEvents = [...new Set(bs.filter(x => !ns.includes(x)))];

  if (sameSeq) {
    md += `**Timeline:** ✅ orden de callbacks idéntico (${bs.length} eventos)\n\n`;
  } else {
    structuralChanges++;
    md += `**Timeline:** 🔶 CAMBIÓ el orden/contenido de callbacks\n`;
    md += `- baseline (${bs.length}): \`${bs.join(' → ')}\`\n`;
    md += `- nueva (${ns.length}): \`${ns.join(' → ')}\`\n`;
    if (newEvents.length) md += `- eventos NUEVOS: ${newEvents.map(e => `\`${e}\``).join(', ')}\n`;
    if (goneEvents.length) md += `- eventos AUSENTES: ${goneEvents.map(e => `\`${e}\``).join(', ')}\n`;
    md += `\n`;
  }

  // --- offMainThread (cambio de threading = señal fuerte) ---
  const bOff = (b.offMainThread || []).join(','), nOff = (n.offMainThread || []).join(',');
  if (bOff !== nOff) {
    structuralChanges++;
    md += `**Threading:** 🔶 offMainThread cambió — baseline: [${bOff || 'ninguno'}] → nueva: [${nOff || 'ninguno'}]\n\n`;
  }

  // --- Δ MÉTRICAS ---
  const bm = b.metrics || {}, nm = n.metrics || {};
  const rows = [];
  const num = (k, tol, unit = '') => {
    const bv = bm[k], nv = nm[k];
    if (bv == null || nv == null) return;
    const delta = nv - bv;
    let flag = '';
    if (tol != null && Math.abs(delta) > tol) flag = ' ⚠️ (>tol)';
    rows.push(`| ${k} | ${bv}${unit} | ${nv}${unit} | ${delta >= 0 ? '+' : ''}${delta}${unit}${flag} |`);
  };
  num('ttffMs', TTFF_TOL_MS, 'ms');
  num('rebufferCount', 0);
  num('rebufferMs', REBUF_TOL_MS, 'ms');
  num('bitrateSwitches', null);
  num('droppedFrames', 0);
  num('loadErrorCount', 0);
  // categóricas
  const cat = (k) => {
    const bv = bm[k], nv = nm[k];
    if (bv == null && nv == null) return;
    const changed = bv !== nv;
    if (changed) structuralChanges++;
    rows.push(`| ${k} | ${bv} | ${nv} | ${changed ? '🔶 cambió' : 'igual'} |`);
  };
  cat('resolution');
  cat('videoCodec');

  if (rows.length) {
    md += `**Métricas:**\n\n| métrica | ${esc(baseVer)} | ${esc(newVer)} | Δ |\n|---|---|---|---|\n${rows.join('\n')}\n\n`;
  }
}

const comparable = scenarios.length - incompleteCaptures - missingScenarios;
md += `---\n\n`;
md += `## Resumen\n`;
md += `- Escenarios en el set: ${scenarios.length} · comparados de verdad: **${comparable}**\n`;
md += `- Capturas incompletas (no comparables → RECAPTURAR): **${incompleteCaptures}**\n`;
md += `- Escenarios desalineados (solo en una versión): **${missingScenarios}**\n`;
md += `- Cambios estructurales detectados (orden callbacks / threading / formato): **${structuralChanges}**\n`;
if (incompleteCaptures > 0 || missingScenarios > 0) {
  md += `- ⚠️ El diff NO es concluyente: hay capturas incompletas o set desalineado. Recapturar (set completo, dejar reproducir hasta first_frame + soak) y re-correr antes de emitir veredicto.\n`;
} else if (structuralChanges === 0) {
  md += `- ✅ Set completo y alineado, sin cambios estructurales — diferencias solo en métricas numéricas (posible ruido de red).\n`;
} else {
  md += `- 🔶 Set completo, pero hay cambios estructurales → revisar con /version-comparator si son esperados por el changelog o regresiones.\n`;
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, md);
console.log(`✓ ${OUT} (${comparable}/${scenarios.length} comparables, ${incompleteCaptures} incompletas, ${structuralChanges} cambios estructurales)`);
// Exit 5 = diff no concluyente (capturas incompletas / set desalineado) → el pipeline puede decidir recapturar.
process.exit((incompleteCaptures > 0 || missingScenarios > 0) ? 5 : 0);
