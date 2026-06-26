#!/usr/bin/env bash
# ============================================================================
# check-portability.sh — Smoke test del entorno: caza los bugs "bash del Mac ≠ Git Bash"
# ANTES de gastar device + tokens en un run real.
#
# Por qué existe: el desarrollo es en Git Bash 5.2 (Windows) pero el runner ejecuta /bin/bash
# (macOS, posiblemente 3.2). `bash -n` en 5.2 NO detecta lo que rompe en 3.2. Esta racha ya costó
# 3 runs (set -e en install, heredoc-en-$(), locale multibyte). Este checker es estático + se corre
# TAMBIÉN en el Mac (workflow scripts-smoke) para reportar la versión REAL de bash y validar in situ.
#
# Uso:  bash scripts/check-portability.sh
# Exit: 0 sin problemas duros · 1 hay problemas (features bash-4, parseo, CRLF)
# ============================================================================
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

errors=0; warns=0
err()  { echo "  ✗ $*"; errors=$((errors+1)); }
warn() { echo "  ⚠ $*"; warns=$((warns+1)); }

echo "── Entorno ──────────────────────────────────────────"
echo "  bash:   $(bash --version 2>/dev/null | head -1)"
echo "  /bin/bash: $(/bin/bash --version 2>/dev/null | head -1)"
echo "  node:   $(node --version 2>/dev/null)"
echo "  locale: LANG=${LANG:-(unset)} LC_ALL=${LC_ALL:-(unset)}"

# Scripts que efectivamente corren en el runner Mac (el flujo QA). Otros .sh son utilitarios locales.
MAC_SCRIPTS="run-tests.sh report.sh qa-core.sh watch-sdk.sh ingest-issue.sh run-changelog-pipeline.sh
  fetch-sdk-changelog.sh resolve-sdk-version.sh build-sdk-local.sh retry-failed-tests.sh
  create-findings-issues.sh notify-slack.sh publish-report-pages.sh prep-device.sh detect-changelog-change.sh
  fetch-changelog.sh check-maven-available.sh"

echo ""
echo "── 1. Parseo (bash -n) de los scripts del flujo Mac ──"
for s in $MAC_SCRIPTS; do
  [[ -f "scripts/$s" ]] || continue
  bash -n "scripts/$s" 2>/dev/null || err "scripts/$s no parsea (bash -n)"
done
[[ $errors -eq 0 ]] && echo "  ✓ todos parsean"

echo ""
echo "── 2. Features de bash 4+ (ROMPEN en /bin/bash 3.2 de macOS) ──"
# mapfile/readarray y arrays asociativos (declare -A) NO existen en bash 3.2.
# Se ignoran líneas de comentario (las que empiezan con #) para no marcar menciones didácticas.
nocmt() { grep -vE '^[[:space:]]*#' "$1"; }
for s in $MAC_SCRIPTS; do
  [[ -f "scripts/$s" ]] || continue
  nocmt "scripts/$s" | grep -qE '\b(mapfile|readarray)\b' && err "scripts/$s usa mapfile/readarray (bash 4+; reescribir con while-read)"
  nocmt "scripts/$s" | grep -qE 'declare -A|local -A'       && err "scripts/$s usa 'declare -A' (array asociativo, bash 4+; usar archivo temp o arrays paralelos)"
  nocmt "scripts/$s" | grep -qE '\$\{[a-zA-Z_]+(,,|\^\^)'   && err "scripts/$s usa \${x,,}/\${x^^} (case conversion, bash 4+)"
done
[[ $errors -eq 0 ]] && echo "  ✓ sin features de bash 4+"

echo ""
echo "── 3. .cjs parsean (node -c) ──"
for c in scripts/*.cjs; do
  [[ -f "$c" ]] || continue
  node -c "$c" 2>/dev/null || err "$c no parsea (node -c)"
done
echo "  ✓ revisados"

echo ""
echo "── 4. Sin CRLF en los .sh (rompe bash en cualquier versión) ──"
for s in $MAC_SCRIPTS; do
  [[ -f "scripts/$s" ]] || continue
  grep -qU $'\r' "scripts/$s" 2>/dev/null && err "scripts/$s tiene CRLF (debe ser LF — ver .gitattributes)"
done
[[ $errors -eq 0 ]] && echo "  ✓ todos LF"

echo ""
echo "── 5. Heredoc node dentro de \$() (riesgo en 3.2 — preferir .cjs) ──"
# Patrón que rompió report.sh. No siempre falla, pero es frágil → aviso.
for s in $MAC_SCRIPTS; do
  [[ -f "scripts/$s" ]] || continue
  grep -qE '=\$\(node .*<<' "scripts/$s" && warn "scripts/$s tiene 'node … <<HEREDOC' dentro de \$() — frágil en bash 3.2, considerar extraer a .cjs"
done

echo ""
echo "════════════════════════════════════════════════════"
if [[ $errors -gt 0 ]]; then echo "✗ $errors problema(s) duro(s) · $warns aviso(s)"; exit 1; fi
echo "✓ Sin problemas duros · $warns aviso(s)"; exit 0
