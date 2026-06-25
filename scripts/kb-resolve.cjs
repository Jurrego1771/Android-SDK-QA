#!/usr/bin/env node
// ============================================================================
// kb-resolve.cjs — Resolver de conocimiento: query → feature → rutas exactas.
//
// Es la "una parada" que hace un agente: en vez de barrer el árbol, entra al
// INDEX.yaml y resuelve un término (slug, id_prefix, deeplink, o palabra del
// título/notas) a la feature y a las rutas de sus 9 archivos.
//
// Uso:
//   node scripts/kb-resolve.cjs drm           # por slug / deeplink / keyword
//   node scripts/kb-resolve.cjs cast-vod       # por deeplink del router
//   node scripts/kb-resolve.cjs --json pip     # salida JSON (para agentes)
// Exit: 0 match único · 1 sin match · 3 múltiples matches (ambiguo)
// ============================================================================
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(__dirname, '..');
const INDEX = path.join(ROOT, 'qa-knowledge', 'INDEX.yaml');
// Schema mínimo canónico (3 curados + 1 derivado). Ver qa-knowledge/CONVENTIONS.md §3.
const FILES = ['rules.md', 'risks.yaml', 'defects.yaml', 'tests.yaml'];
// Archivos del schema legado (9 files) que aún pueden existir sin migrar — se listan como
// "legado" para no perderlos, pero no son el set objetivo.
const LEGACY_FILES = ['overview.md', 'business-rules.md', 'user-stories.yaml', 'acceptance.yaml', 'dependencies.yaml', 'learnings.yaml'];

const args = process.argv.slice(2);
const asJson = args.includes('--json');
const query = args.filter(a => a !== '--json').join(' ').trim().toLowerCase();
if (!query) { console.error('uso: kb-resolve.cjs [--json] <query>'); process.exit(1); }

const data = yaml.load(fs.readFileSync(INDEX, 'utf8'));

// Resolución por capas, de la más fuerte a la más débil:
function score(f) {
  const slug = (f.slug || '').toLowerCase();
  const pref = (f.id_prefix || '').toLowerCase();
  const dl = (f.deeplinks || []).map(s => s.toLowerCase());
  if (slug === query) return { w: 100, why: 'slug exacto' };
  if (pref === query) return { w: 95, why: 'id_prefix exacto' };
  if (dl.includes(query)) return { w: 90, why: `deeplink "${query}"` };
  const hay = `${slug} ${(f.title || '').toLowerCase()} ${(f.notes || '').toLowerCase()}`;
  if (hay.includes(query)) return { w: 50, why: 'keyword en slug/título/notas' };
  return null;
}

const hits = data.features
  .map(f => ({ f, s: score(f) }))
  .filter(x => x.s)
  .sort((a, b) => b.s.w - a.s.w);

if (!hits.length) {
  console.error(`✗ sin match para "${query}". Probá un slug, deeplink o keyword. Slugs: ${data.features.map(f => f.slug).join(', ')}`);
  process.exit(1);
}

// directorio activo: casa si migrada, legado si no.
function activeDir(f) {
  return (f.migrated ? `qa-knowledge/${f.slug}` : f.path_current || '').replace(/\/+$/, '');
}
function present(dir, name) { return fs.existsSync(path.join(ROOT, dir, name)); }

function describe(f) {
  const dir = activeDir(f);
  return {
    slug: f.slug, id_prefix: f.id_prefix, title: f.title, group: f.group,
    status: f.status, migrated: f.migrated, dir, counts: f.counts,
    deeplinks: f.deeplinks || [],
    files: Object.fromEntries(FILES.map(n => [n, present(dir, n) ? `${dir}/${n}` : null])),
    legacy: LEGACY_FILES.filter(n => present(dir, n)).map(n => `${dir}/${n}`),
    notes: f.notes || null,
  };
}

const top = hits[0];
const ties = hits.filter(h => h.s.w === top.s.w);

if (asJson) {
  console.log(JSON.stringify(ties.length > 1
    ? { ambiguous: true, matches: ties.map(h => describe(h.f)) }
    : describe(top.f), null, 2));
  process.exit(ties.length > 1 ? 3 : 0);
}

// salida humana
for (const h of (ties.length > 1 ? ties : [top])) {
  const d = describe(h.f);
  console.log(`\n▶ ${d.slug}  (${h.s.why})`);
  console.log(`  ${d.title}  · grupo ${d.group} · ${d.migrated ? 'migrada' : 'LEGADO ' + d.dir} · prefijo ${d.id_prefix}`);
  const c = d.counts || {};
  console.log(`  cobertura: tests=${c.tests || 0}  gaps=${c.gaps || 0}  risks=${c.risks || 0}  defects=${c.defects || 0}  US=${c.user_stories || 0}`);
  if (d.deeplinks.length) console.log(`  deeplinks: ${d.deeplinks.join(', ')}`);
  console.log('  archivos (schema mínimo):');
  for (const [n, p] of Object.entries(d.files)) console.log(`    ${p ? '✓' : '·'} ${p || d.dir + '/' + n + '  (falta)'}`);
  if (d.legacy.length) console.log(`  legado presente (a plegar al migrar): ${d.legacy.map(p => path.basename(p)).join(', ')}`);
  if (d.notes) console.log(`  nota: ${d.notes}`);
}
if (ties.length > 1) { console.error(`\n⚠ ${ties.length} matches con igual peso — query ambigua.`); process.exit(3); }
process.exit(0);
