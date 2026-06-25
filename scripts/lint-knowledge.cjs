#!/usr/bin/env node
// ============================================================================
// lint-knowledge.cjs — Linter del grafo de conocimiento (qa-knowledge + docs/features).
//
// Convierte el knowledge de "documentación que se pudre en silencio" a "documentación que el CI
// obliga a mantener viva". Valida contra el CÓDIGO y el INDEX, no contra suposiciones.
//
// Reglas (ver qa-knowledge/CONVENTIONS.md):
//   R1  tests.yaml: cada TC con file:"X.kt:NN" → el archivo existe y el método (name) está ahí.
//   R2  @Ignore ↔ defects: TC status=ignored tiene @Ignore en el código y un defect_ref.
//   R3  cross-links: todo *_ref (ac_ref/defect_ref/risk_ref/us_ref) resuelve a un ID existente.
//   R4  IDs bien formados: <PREFIX>-<TIPO>-NNN con el prefijo de la feature (gaps = GAP-<PREFIX>-NNN).
//   R5  versión del SDK única: nadie cita una versión distinta a app/build.gradle.kts.
//   R6  INDEX al día (build-knowledge-index --check) + la regla (rules.md/business-rules.md) existe.
//
// Uso:   node scripts/lint-knowledge.cjs            # reporte completo
//        node scripts/lint-knowledge.cjs --quiet    # solo errores/avisos
// Exit:  0 sin errores (avisos no rompen) · 1 hay errores · 2 error de invocación
// ============================================================================
const fs = require('fs');
const path = require('path');
const cp = require('child_process');
const yaml = require('js-yaml');

const ROOT = path.resolve(__dirname, '..');
const INDEX = path.join(ROOT, 'qa-knowledge', 'INDEX.yaml');
const BUILD_GRADLE = path.join(ROOT, 'app', 'build.gradle.kts');
const ANDROID_TEST = path.join(ROOT, 'app/src/androidTest/java/com/example/sdk_qa');
const QUIET = process.argv.includes('--quiet');

const errors = [];
const warns = [];
const err = (rule, msg) => errors.push(`[${rule}] ${msg}`);
const warn = (rule, msg) => warns.push(`[${rule}] ${msg}`);

function loadYaml(file) {
  try { return yaml.load(fs.readFileSync(file, 'utf8')); }
  catch (e) { err('PARSE', `${rel(file)}: YAML inválido — ${e.message.split('\n')[0]}`); return null; }
}
const rel = p => path.relative(ROOT, p).replace(/\\/g, '/');

// Extrae todos los records (items con .id) de cualquier lista top-level de un YAML.
function records(file) {
  const d = loadYaml(file);
  if (!d || typeof d !== 'object') return [];
  const out = [];
  for (const [key, val] of Object.entries(d))
    if (Array.isArray(val))
      for (const item of val)
        if (item && typeof item === 'object' && item.id) out.push({ ...item, _list: key });
  return out;
}

// ── cargar INDEX + features ────────────────────────────────────────────────
if (!fs.existsSync(INDEX)) { console.error('✗ no existe ' + rel(INDEX)); process.exit(2); }
const index = yaml.load(fs.readFileSync(INDEX, 'utf8'));
const features = index.features || [];
const activeDir = f => path.join(ROOT, (f.migrated ? `qa-knowledge/${f.slug}` : f.path_current || '').replace(/\/+$/, ''));

// ── PASO 1: registrar todos los IDs (para R3) ──────────────────────────────
const idRegistry = new Map();   // id → {feature, file}
const TYPE_BY_FILE = { 'tests.yaml': 'TC', 'risks.yaml': 'RISK', 'defects.yaml': 'DEF', 'acceptance.yaml': 'AC', 'user-stories.yaml': 'US', 'rules.md': 'AC' };
const KNOWLEDGE_YAMLS = ['tests.yaml', 'risks.yaml', 'defects.yaml', 'acceptance.yaml', 'user-stories.yaml'];

for (const f of features) {
  const dir = activeDir(f);
  for (const name of KNOWLEDGE_YAMLS) {
    const file = path.join(dir, name);
    if (!fs.existsSync(file)) continue;
    for (const r of records(file)) {
      if (idRegistry.has(r.id)) warn('R4', `ID duplicado "${r.id}" (${f.slug}/${name} y ${idRegistry.get(r.id).where})`);
      else idRegistry.set(r.id, { feature: f.slug, where: `${f.slug}/${name}` });
    }
  }
}

// ── PASO 2: reglas por feature ─────────────────────────────────────────────
const idRe = /^([A-Z0-9]+)-(TC|RISK|DEF|AC|US)-(\d{2,})$/;
const gapRe = /^GAP-([A-Z0-9]+)-(\d{2,})$/;

for (const f of features) {
  if (!f.id_prefix) continue;                 // sdk-overview: narrativa, sin IDs
  const dir = activeDir(f);
  if (!fs.existsSync(dir)) { err('R6', `${f.slug}: directorio activo no existe (${rel(dir)})`); continue; }
  const P = f.id_prefix;

  // ---- R4: IDs bien formados + prefijo correcto ----
  // Los archivos que se pliegan (user-stories/acceptance) se reportan como aviso, no error.
  const LEGACY_DROP = new Set(['user-stories.yaml', 'acceptance.yaml']);
  for (const name of KNOWLEDGE_YAMLS) {
    const file = path.join(dir, name);
    if (!fs.existsSync(file)) continue;
    const expectType = TYPE_BY_FILE[name];
    const report = LEGACY_DROP.has(name) ? (rule, msg) => warn(rule, msg + ' (archivo a plegar)') : err;
    for (const r of records(file)) {
      const m = idRe.exec(r.id), g = gapRe.exec(r.id);
      if (g) {                                  // gap (solo válido en tests.yaml)
        if (name !== 'tests.yaml') report('R4', `${f.slug}/${name}: id GAP fuera de tests.yaml → ${r.id}`);
        else if (g[1] !== P) report('R4', `${f.slug}/tests.yaml: gap con prefijo ajeno → ${r.id} (esperado GAP-${P}-)`);
        continue;
      }
      if (!m) { report('R4', `${f.slug}/${name}: id mal formado → ${r.id}`); continue; }
      if (m[1] !== P) report('R4', `${f.slug}/${name}: prefijo ajeno → ${r.id} (esperado ${P}-)`);
      if (m[2] !== expectType) report('R4', `${f.slug}/${name}: tipo ${m[2]} no corresponde a ${name} (esperado ${expectType}) → ${r.id}`);
    }
  }

  // ---- R3: cross-links resuelven ----
  for (const name of KNOWLEDGE_YAMLS) {
    const file = path.join(dir, name);
    if (!fs.existsSync(file)) continue;
    for (const r of records(file)) {
      for (const [k, v] of Object.entries(r)) {
        if (!/_ref$/.test(k) || v == null) continue;
        for (const ref of (Array.isArray(v) ? v : [v])) {
          if (typeof ref !== 'string') continue;
          if (!idRegistry.has(ref)) err('R3', `${f.slug}/${name}: ${r.id}.${k} → "${ref}" no existe`);
        }
      }
    }
  }

  // ---- R1 + R2: tests.yaml contra el código ----
  const testsFile = path.join(dir, 'tests.yaml');
  if (fs.existsSync(testsFile)) {
    const ignoredByFile = new Map();            // relpath → nº TC status=ignored
    for (const r of records(testsFile)) {
      if (!/^.+-TC-\d+$/.test(r.id)) continue;  // solo tests reales (no gaps)
      if (!r.file) { warn('R1', `${f.slug}: ${r.id} sin campo file:`); continue; }
      const mm = /^(.+\.kt):(\d+)$/.exec(r.file);
      const relp = mm ? mm[1] : r.file;
      const line = mm ? parseInt(mm[2], 10) : null;
      const kt = path.join(ANDROID_TEST, relp);
      if (!fs.existsSync(kt)) { err('R1', `${f.slug}: ${r.id} apunta a archivo inexistente → ${r.file}`); continue; }
      const src = fs.readFileSync(kt, 'utf8');
      // R1: el método (name) está en el archivo. Las entradas a nivel-clase ("(suite)" o sin
      // :línea) solo verifican que el archivo exista — no hay un único método que buscar.
      const isSuite = /\(suite\)/i.test(r.name || '') || !line;
      if (r.name && !isSuite) {
        const reName = new RegExp(`fun\\s+\`?${r.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\`?\\s*\\(`);
        if (!reName.test(src)) err('R1', `${f.slug}: ${r.id} name "${r.name}" no se encuentra en ${relp} (¿renombrado?)`);
        else if (line) {                        // línea aproximada
          const srcLines = src.split('\n');
          const at = srcLines.findIndex(l => reName.test(l)) + 1;
          if (at && Math.abs(at - line) > 5) warn('R1', `${f.slug}: ${r.id} línea ${line} desfasada (método en ${at}) en ${relp}`);
        }
      }
      // R2: status=ignored ↔ @Ignore + defect_ref
      if (r.status === 'ignored') {
        ignoredByFile.set(relp, (ignoredByFile.get(relp) || 0) + 1);
        if (!/@Ignore/.test(src)) err('R2', `${f.slug}: ${r.id} status=ignored pero ${relp} no tiene @Ignore`);
        if (!r.defect_ref) warn('R2', `${f.slug}: ${r.id} ignorado sin defect_ref (¿qué bug lo justifica?)`);
      }
    }
    // R2 converso (ligero): @Ignore en código sin TC ignorado que lo catalogue
    for (const [relp, nIgnored] of ignoredByFile) {
      const kt = path.join(ANDROID_TEST, relp);
      if (!fs.existsSync(kt)) continue;
      const nAtIgnore = (fs.readFileSync(kt, 'utf8').match(/@Ignore/g) || []).length;
      if (nAtIgnore > nIgnored) warn('R2', `${f.slug}: ${relp} tiene ${nAtIgnore} @Ignore pero solo ${nIgnored} TC status=ignored lo catalogan`);
    }
  }

  // ---- R6: la regla (rules.md / business-rules.md) existe ----
  const hasRules = fs.existsSync(path.join(dir, 'rules.md'));
  const otherMd = fs.existsSync(dir) && fs.readdirSync(dir).some(n => n.endsWith('.md') && n !== 'rules.md');
  if (!hasRules && !otherMd) err('R6', `${f.slug}: falta la regla (ningún .md en ${rel(dir)})`);
  else if (!hasRules && otherMd) warn('R6', `${f.slug}: la regla vive en un .md legado — consolidar en rules.md al migrar`);
}

// ── R5: versión del SDK única ──────────────────────────────────────────────
function buildVersion() {
  const t = fs.readFileSync(BUILD_GRADLE, 'utf8');
  const m = /mediastreamplatformsdkandroid:(\d+)\.(\d+)\.(\d+)/.exec(t);
  return m ? { major: +m[1], minor: +m[2], raw: `${m[1]}.${m[2]}.${m[3]}` } : null;
}
const bv = buildVersion();
if (!bv) warn('R5', 'no pude leer la versión del SDK de build.gradle.kts');
else {
  // CONVENTIONS.md se excluye: documenta la convención y menciona "v11" al explicar la deriva.
  const scan = [
    'docs/ai-context/sdk-api-contract.md', 'docs/ai-context/business-rules.md',
    'docs/ai-context/test-patterns.md', 'docs/ai-context/feature-test-matrix.md',
    'docs/README.md', 'CLAUDE.md',
  ];
  const SANE = v => v >= 9 && v <= 30;          // rango plausible de versión mayor del SDK
  for (const relp of scan) {
    const file = path.join(ROOT, relp);
    if (!fs.existsSync(file)) continue;
    // Líneas con la marca `lint-knowledge:allow-version` se excluyen (menciones históricas/didácticas).
    const txt = fs.readFileSync(file, 'utf8')
      .split('\n').filter(l => !l.includes('lint-knowledge:allow-version')).join('\n');
    const seen = new Set();
    // "vNN" (major suelto, p.ej. v11, v9.9.0)
    for (const m of txt.matchAll(/\bv(\d{1,2})(?:\.\d+){0,2}\b/gi)) {
      const maj = +m[1], tok = m[0];
      if (SANE(maj) && maj !== bv.major && !seen.has(tok)) { seen.add(tok); err('R5', `${relp}: cita "${tok}" pero el SDK es ${bv.raw} (build.gradle)`); }
    }
    // "X.Y.Z" — excluyendo IPs (4º octeto) vía lookarounds, y majors fuera de rango
    for (const m of txt.matchAll(/(?<![\d.])(\d+)\.(\d+)\.(\d+)(?![\d.])/g)) {
      const tok = m[0];
      if (SANE(+m[1]) && (+m[1] !== bv.major || +m[2] !== bv.minor) && !seen.has(tok)) { seen.add(tok); err('R5', `${relp}: cita "${tok}" pero el SDK es ${bv.raw} (build.gradle)`); }
    }
  }
}

// ── R6: INDEX al día ───────────────────────────────────────────────────────
try {
  cp.execFileSync('node', [path.join(__dirname, 'build-knowledge-index.cjs'), '--check'], { stdio: 'pipe' });
} catch (e) {
  err('R6', 'INDEX.yaml desactualizado o inválido (corré: node scripts/build-knowledge-index.cjs)');
}

// ── reporte ────────────────────────────────────────────────────────────────
if (!QUIET) {
  console.log(`\nFeatures revisadas: ${features.length}  ·  IDs registrados: ${idRegistry.size}`);
  if (bv) console.log(`Versión del SDK (build.gradle): ${bv.raw}`);
}
if (warns.length) { console.log(`\n⚠ Avisos (${warns.length}):`); warns.forEach(w => console.log('  - ' + w)); }
if (errors.length) { console.log(`\n✗ Errores (${errors.length}):`); errors.forEach(e => console.log('  - ' + e)); }
else console.log('\n✓ Sin errores de knowledge.');

process.exit(errors.length ? 1 : 0);
