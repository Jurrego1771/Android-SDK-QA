#!/usr/bin/env node
// =============================================================================
// parse-junit-xml.js — Parsea los JUnit XML del Orchestrator (Gradle
// connectedAndroidTest) y produce:
//   1. test-results.json (mismo schema que consumen report.sh y notify-slack.sh)
//   2. líneas `eval`-ables para poblar PASSED_TESTS / FAILED_TESTS / FAILED_REASONS
//      en run-tests.sh (stdout)
//
// Reemplaza el scraping del formato "brief" de `am instrument` (puntos + "Error in"),
// que era frágil y reportaba conteos incorrectos. El XML es la fuente de verdad:
// nombre exacto de test, clase, duración y stacktrace de fallo.
//
// Uso:
//   node parse-junit-xml.js <xmlDir> <outJsonPath> <durationStr>
//
// Si no hay XML (p.ej. el runner murió antes de emitir resultados), escribe un
// JSON vacío y no imprime nada — run-tests.sh trata 0 tests como fallo de runner.
// =============================================================================

const fs = require('fs');
const path = require('path');

const [xmlDir, outJsonPath, durationStr = ''] = process.argv.slice(2);

function listXmlFiles(dir) {
  try {
    return fs.readdirSync(dir)
      .filter(f => f.startsWith('TEST-') && f.endsWith('.xml'))
      .map(f => path.join(dir, f));
  } catch (_) {
    return [];
  }
}

// Decodifica las entidades XML más comunes en atributos/texto.
function unescapeXml(s) {
  return String(s)
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#10;/g, '\n')
    .replace(/&#9;/g, '\t')
    .replace(/&amp;/g, '&'); // último, para no re-decodificar
}

function attr(tag, name) {
  const m = tag.match(new RegExp(name + '="([^"]*)"'));
  return m ? unescapeXml(m[1]) : '';
}

const passed = [];
const failed = [];

for (const file of listXmlFiles(xmlDir)) {
  let xml = '';
  try { xml = fs.readFileSync(file, 'utf8'); } catch (_) { continue; }

  // Cada <testcase ...> self-closing (pass) o con cuerpo <failure>/<error> (fail/skip).
  const re = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    const tagAttrs = m[1];
    const body = m[3] || '';
    const name = attr(tagAttrs, 'name') || '—';
    const cls = attr(tagAttrs, 'classname') || '';
    const time = attr(tagAttrs, 'time') || '';

    const failM = body.match(/<(failure|error)\b([^>]*)(\/>|>([\s\S]*?)<\/\1>)/);
    const skipM = body.match(/<skipped\b/);

    if (failM) {
      // mensaje: atributo message="..." o primera línea del cuerpo del stacktrace
      let reason = attr(failM[2], 'message');
      if (!reason && failM[4]) {
        reason = unescapeXml(failM[4]).split(/\r?\n/).map(s => s.trim())
          .find(s => s && !s.startsWith('at ')) || '';
      }
      reason = reason.replace(/\s+/g, ' ').trim().slice(0, 200);
      failed.push({ name, class: cls, status: 'failed', duration: time, error: reason });
    } else if (skipM) {
      // skipped: no cuenta como pass ni fail
    } else {
      passed.push({ name, class: cls, status: 'passed', duration: time, error: '' });
    }
  }
}

// 1) test-results.json
const result = {
  tests: [...failed, ...passed],
  summary: {
    total: passed.length + failed.length,
    passed: passed.length,
    failed: failed.length,
    duration: durationStr,
  },
};
try { fs.writeFileSync(outJsonPath, JSON.stringify(result, null, 2), 'utf8'); } catch (_) {}

// 2) líneas eval-ables para run-tests.sh
const q = s => "'" + String(s).replace(/'/g, "'\\''") + "'";
for (const t of passed) process.stdout.write('PASSED_TESTS+=(' + q(t.class + '.' + t.name) + ')\n');
for (const t of failed) {
  process.stdout.write('FAILED_TESTS+=(' + q(t.class + '.' + t.name) + ')\n');
  process.stdout.write('FAILED_REASONS+=(' + q(t.error) + ')\n');
}
