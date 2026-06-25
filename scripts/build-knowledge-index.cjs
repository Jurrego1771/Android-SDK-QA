#!/usr/bin/env node
// ============================================================================
// build-knowledge-index.cjs — Regenera qa-knowledge/INDEX.yaml desde el disco.
//
// Round-trip sobre el propio INDEX.yaml: lo lee como SEED (campos curados:
// slug, id_prefix, title, group, order, status, deeplinks, path_current, notes),
// y refresca los campos DERIVABLES desde el estado real del repo:
//   - counts   (cuenta `- id:` en cada YAML de la feature)
//   - migrated (existe qa-knowledge/<slug>/ con YAMLs)
//   - generated (fecha)
// Además VALIDA y reporta (no escribe basura):
//   - colisiones de slug / id_prefix
//   - deeplinks del INDEX que ya NO existen en el router (stale)
//   - keys del router NO mapeadas a ninguna feature (ni en unmapped_deeplinks)
//   - features cuyo directorio activo no existe
//
// Uso:
//   node scripts/build-knowledge-index.cjs           # regenera INDEX.yaml
//   node scripts/build-knowledge-index.cjs --check   # CI: no escribe; exit 1 si cambiaría
//                                                     #     o si hay errores de validación
// Exit: 0 ok · 1 (en --check) habría cambios o validación falló · 2 error de invocación
// ============================================================================
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(__dirname, '..');
const INDEX = path.join(ROOT, 'qa-knowledge', 'INDEX.yaml');
const ROUTER = path.join(ROOT, 'app/src/debug/java/com/example/sdk_qa/debug/DeepLinkRouterActivity.kt');
const CHECK = process.argv.includes('--check');

const HEADER = `# =============================================================================
# qa-knowledge/INDEX.yaml — Puerta ÚNICA del conocimiento por-feature para agentes.
#
# Un agente que necesita "¿qué tests hay para X?" o "¿riesgos de este cambio?" entra AQUÍ
# primero: mapea el cambio/feature → slug → carpeta, y abre solo lo que necesita. Una parada.
#
# GENERADO por scripts/build-knowledge-index.cjs — NO editar los campos derivados a mano
# (counts, migrated, generated). Los campos curados (title, group, order, deeplinks, notes,
# id_prefix, path_current, merge_candidates, unmapped_deeplinks) SÍ se editan aquí y el
# generador los preserva. Regenerar tras cualquier cambio:  node scripts/build-knowledge-index.cjs
# =============================================================================
`;

function die(msg, code = 2) { console.error('✗ ' + msg); process.exit(code); }

// ── counts: nº de IDs de un TIPO concreto en un YAML (TC/RISK/DEF/US/AC) ──────
// Cuenta solo IDs tipados `<PREFIX>-<TYPE>-NNN`, así ignora ids anidados (pasos) y
// entradas de otra naturaleza (p.ej. GAP-CORE-001 en tests.yaml = brechas, no casos).
function countTyped(file, type) {
  if (!fs.existsSync(file)) return null;
  const txt = fs.readFileSync(file, 'utf8');
  const re = new RegExp(`id:\\s*[A-Z0-9]+-${type}-\\d`, 'g');
  const m = txt.match(re);
  return m ? m.length : 0;
}
// Brechas de cobertura declaradas (GAP-*) en tests.yaml — informativo, no son tests.
function countGaps(file) {
  if (!fs.existsSync(file)) return 0;
  const m = fs.readFileSync(file, 'utf8').match(/id:\s*GAP-[A-Z0-9-]+/g);
  return m ? m.length : 0;
}

// ── extrae las keys del router ("key" to Activity::class.java) ────────────────
function routerKeys() {
  if (!fs.existsSync(ROUTER)) { console.warn('⚠ router no encontrado: ' + ROUTER); return null; }
  const txt = fs.readFileSync(ROUTER, 'utf8');
  const keys = new Set();
  for (const m of txt.matchAll(/"([a-z0-9-]+)"\s+to\s+/g)) keys.add(m[1]);
  return keys;
}

// ── main ──────────────────────────────────────────────────────────────────
if (!fs.existsSync(INDEX)) die('no existe ' + INDEX);
const before = fs.readFileSync(INDEX, 'utf8');
const data = yaml.load(before);
if (!data || !Array.isArray(data.features)) die('INDEX.yaml sin lista features');

const problems = [];      // errores duros (rompen --check)
const warnings = [];      // avisos (no rompen)
const seenSlug = new Map();
const seenPrefix = new Map();
const mappedKeys = new Set();

for (const f of data.features) {
  // colisiones
  if (seenSlug.has(f.slug)) problems.push(`slug duplicado: ${f.slug}`);
  seenSlug.set(f.slug, true);
  if (f.id_prefix) {
    if (seenPrefix.has(f.id_prefix)) problems.push(`id_prefix duplicado: ${f.id_prefix} (${f.slug} y ${seenPrefix.get(f.id_prefix)})`);
    else seenPrefix.set(f.id_prefix, f.slug);
  }

  // migrated: ¿existe qa-knowledge/<slug>/ con al menos un YAML?
  const homeDir = path.join(ROOT, 'qa-knowledge', f.slug);
  const isHome = fs.existsSync(homeDir) &&
    fs.readdirSync(homeDir).some(n => n.endsWith('.yaml'));
  f.migrated = isHome;

  // directorio activo (casa si migrada, legado si no)
  const activeRel = isHome ? `qa-knowledge/${f.slug}` : f.path_current;
  const activeDir = activeRel ? path.join(ROOT, activeRel) : null;
  if (!activeDir || !fs.existsSync(activeDir)) {
    problems.push(`${f.slug}: directorio activo no existe (${activeRel || 'sin path_current'})`);
  } else {
    // counts derivados (por tipo de ID)
    const c = {
      tests: countTyped(path.join(activeDir, 'tests.yaml'), 'TC') ?? 0,
      risks: countTyped(path.join(activeDir, 'risks.yaml'), 'RISK') ?? 0,
      defects: countTyped(path.join(activeDir, 'defects.yaml'), 'DEF') ?? 0,
      user_stories: countTyped(path.join(activeDir, 'user-stories.yaml'), 'US') ?? 0,
    };
    const ac = countTyped(path.join(activeDir, 'acceptance.yaml'), 'AC');
    if (ac != null) c.acceptance = ac;
    const gaps = countGaps(path.join(activeDir, 'tests.yaml'));
    if (gaps > 0) c.gaps = gaps;
    f.counts = c;
  }

  for (const k of (f.deeplinks || [])) mappedKeys.add(k);
}

// ── validación de deeplinks contra el router real ────────────────────────────
const rk = routerKeys();
if (rk) {
  for (const f of data.features)
    for (const k of (f.deeplinks || []))
      if (!rk.has(k)) problems.push(`${f.slug}: deeplink "${k}" no existe en el router`);

  const declaredUnmapped = new Set((data.unmapped_deeplinks || []).map(u => u.key));
  for (const k of rk)
    if (!mappedKeys.has(k) && !declaredUnmapped.has(k))
      warnings.push(`deeplink del router sin mapear ni declarar: "${k}"  (asignar a una feature o agregar a unmapped_deeplinks)`);
}

// ── índice inverso affected_file → slug (para el blast radius del change-analyzer) ──
// Extrae los `affected_files` de risks.yaml/defects.yaml de cada feature, normaliza la ruta
// (quita ":línea" y "(paréntesis)": "MediastreamPlayer.kt (lambda…)" → "MediastreamPlayer.kt") y
// mapea archivo del SDK → [slugs]. Un git-diff de la rama del SDK se resuelve a features con esto.
function affectedFilesOf(file) {
  if (!fs.existsSync(file)) return [];
  let d; try { d = yaml.load(fs.readFileSync(file, 'utf8')); } catch { return []; }
  const out = [];
  for (const val of Object.values(d || {}))
    if (Array.isArray(val))
      for (const item of val)
        for (const raw of (item?.affected_files || []))
          if (typeof raw === 'string')
            // una entrada puede listar varios archivos separados por coma
            for (let part of raw.split(',')) {
              part = part.replace(/\(.*$/, '').replace(/:.*$/, '');        // sin (…) ni :línea
              const base = (part.split('/').pop() || '').replace(/[\s ]+/g, '').trim(); // basename sin espacios (incl. NBSP)
              if (/\.(kt|java)$/.test(base)) out.push(base);
            }
  return out;
}
const reverse = {};   // { "MediastreamPlayer.kt": ["core-player","drm",...] }
for (const f of data.features) {
  if (!f.id_prefix) continue;
  const dir = path.join(ROOT, (f.migrated ? `qa-knowledge/${f.slug}` : f.path_current || '').replace(/\/+$/, ''));
  const files = new Set([...affectedFilesOf(path.join(dir, 'risks.yaml')), ...affectedFilesOf(path.join(dir, 'defects.yaml'))]);
  for (const file of files) (reverse[file] ||= []).push(f.slug);
}
const AFFECTED = path.join(ROOT, 'qa-knowledge', 'affected-files.json');
const affectedJson = JSON.stringify({
  _comment: 'GENERADO por build-knowledge-index.cjs. Mapa archivo_SDK(basename) → slugs de feature, desde los affected_files de risks/defects. Lo usa change-analyzer para el blast radius (git-diff → features). NO editar a mano.',
  generated: new Date().toISOString().slice(0, 10),
  map: Object.fromEntries(Object.entries(reverse).map(([k, v]) => [k, [...new Set(v)].sort()])),
}, null, 2) + '\n';

// ── fecha de generación ───────────────────────────────────────────────────
data.generated = new Date().toISOString().slice(0, 10);

// ── emitir ────────────────────────────────────────────────────────────────
const body = yaml.dump(data, { lineWidth: -1, noRefs: true, sortKeys: false, quotingType: '"' });
const after = HEADER + body;

// ── reporte ────────────────────────────────────────────────────────────────
const totals = data.features.reduce((a, f) => {
  a.tests += (f.counts?.tests || 0); a.migrated += f.migrated ? 1 : 0; return a;
}, { tests: 0, migrated: 0 });
console.log(`\nFeatures: ${data.features.length}  ·  migradas: ${totals.migrated}/${data.features.length}  ·  tests catalogados: ${totals.tests}`);
console.log(`Prefijos únicos: ${seenPrefix.size}  ·  slugs: ${seenSlug.size}`);
if (warnings.length) { console.log('\n⚠ Avisos:'); warnings.forEach(w => console.log('  - ' + w)); }
if (problems.length) { console.log('\n✗ Errores de validación:'); problems.forEach(p => console.log('  - ' + p)); }

const beforeAffected = fs.existsSync(AFFECTED) ? fs.readFileSync(AFFECTED, 'utf8') : '';
// comparar ignorando la línea `generated` (cambia cada día y no es deriva real)
const stripGen = s => s.replace(/"generated":\s*"[^"]*",?\n/g, '');
const changed = after !== before || stripGen(affectedJson) !== stripGen(beforeAffected);

if (CHECK) {
  if (problems.length) { console.error('\n✗ --check: validación falló.'); process.exit(1); }
  if (changed) { console.error('\n✗ --check: INDEX.yaml / affected-files.json desactualizado (corré build-knowledge-index sin --check).'); process.exit(1); }
  console.log(`\n✓ --check: INDEX.yaml + affected-files.json al día. Mapa inverso: ${Object.keys(reverse).length} archivos.`);
  process.exit(0);
}

if (problems.length) { console.error('\n✗ Hay errores de validación — no se reescribe. Corregí y reintentá.'); process.exit(1); }
fs.writeFileSync(INDEX, after);
fs.writeFileSync(AFFECTED, affectedJson);
console.log(`\n✓ ${changed ? 'INDEX.yaml + affected-files.json regenerados' : 'ya estaban al día'}. Mapa inverso: ${Object.keys(reverse).length} archivos del SDK → features.`);
