#!/usr/bin/env bash
# ============================================================================
# retry-failed-tests.sh — Reintenta los tests FALLIDOS para distinguir flaky de consistente.
#
# Es la EVIDENCIA que el test-analyzer necesita para clasificar (un fail aislado es ambiguo).
# Lee test-results.json, reintenta cada test fallido N veces (am instrument -e class <FQCN>#<método>),
# y reescribe el json marcando cada uno:
#   flaky=true            → pasó en algún reintento (no es bug real, es inestabilidad)
#   flaky=false (consist.) → falló en TODOS los reintentos (fallo consistente → candidato a bug real)
#   retries=N · retry_passed=M
#
# Desacoplado de run-tests.sh a propósito (run-tests.sh se está migrando a Mac). El orquestador
# (qa-core.sh) lo invoca tras la corrida.
#
# Uso:  retry-failed-tests.sh <device_serial> [results_json] [n_retries]
# Exit: 0 siempre (reescribe el json; no falla el pipeline por reintentar).
# ============================================================================
set -uo pipefail

DEVICE="${1:?uso: retry-failed-tests.sh <device_serial> [results_json] [n_retries]}"
RESULTS="${2:-ai-output/test-results.json}"
N="${3:-2}"
APP_PACKAGE="com.example.sdk_qa"
TEST_RUNNER="${APP_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"

[[ -f "$RESULTS" ]] || { echo "retry: no existe $RESULTS — nada que reintentar" >&2; exit 0; }

# Lista de fallidos: "FQCN|método" (node lee el json; evita depender de jq).
# while-read en vez de mapfile (mapfile es bash 4+, no existe en el /bin/bash 3.2 de macOS).
FAILED=()
while IFS= read -r _line; do [[ -n "$_line" ]] && FAILED+=("$_line"); done < <(node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
  for (const t of (d.tests||[])) if (t.status==="failed") console.log((t.class||"")+"|"+(t.name||""));
' "$RESULTS")

if [[ ${#FAILED[@]} -eq 0 ]]; then echo "retry: 0 fallidos — nada que reintentar"; exit 0; fi
echo "retry: ${#FAILED[@]} test(s) fallido(s), hasta ${N} reintento(s) c/u…"

# Reintenta cada fallido; construye RETRY_JSON ("entry=passed" por línea) directo en el loop.
# (Sin array asociativo `declare -A` — es bash 4+; no existe en bash 3.2.)
RETRY_JSON=""
for entry in "${FAILED[@]}"; do
  cls="${entry%%|*}"; method="${entry##*|}"
  [[ -z "$cls" || -z "$method" ]] && continue
  passed=0
  for ((i=1; i<=N; i++)); do
    raw="$(adb -s "$DEVICE" shell am instrument -w -e class "${cls}#${method}" "$TEST_RUNNER" 2>&1)"
    if grep -qE "^OK \([1-9]" <<< "$raw" && ! grep -q "FAILURES!!!" <<< "$raw"; then
      passed=$((passed+1))
    fi
  done
  RETRY_JSON+="${entry}=${passed}"$'\n'
  if [[ $passed -gt 0 ]]; then echo "  ⚠ FLAKY: ${cls##*.}.${method} (pasó ${passed}/${N} en reintento)";
  else echo "  ✗ CONSISTENTE: ${cls##*.}.${method} (falló ${N}/${N})"; fi
done
node -e '
  const fs=require("fs"); const file=process.argv[1]; const n=parseInt(process.argv[2],10);
  const map={}; for (const line of process.argv[3].split("\n")) { if(!line) continue; const i=line.lastIndexOf("="); map[line.slice(0,i)]=parseInt(line.slice(i+1),10); }
  const d=JSON.parse(fs.readFileSync(file,"utf8"));
  let flaky=0, consistent=0;
  for (const t of (d.tests||[])) {
    if (t.status!=="failed") continue;
    const key=(t.class||"")+"|"+(t.name||""); const passed=map[key]||0;
    t.retries=n; t.retry_passed=passed; t.flaky=passed>0;
    if (passed>0) flaky++; else consistent++;
  }
  d.summary=d.summary||{}; d.summary.flaky=flaky; d.summary.consistent_failures=consistent;
  fs.writeFileSync(file, JSON.stringify(d,null,2));
  console.log(`retry: marcados ${flaky} flaky · ${consistent} fallo(s) consistente(s) → ${file}`);
' "$RESULTS" "$N" "$RETRY_JSON"
