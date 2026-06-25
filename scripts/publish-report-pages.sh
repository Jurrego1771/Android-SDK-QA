#!/usr/bin/env bash
# ============================================================================
# publish-report-pages.sh — Publica el HTML report a gh-pages en una URL FIJA.
#
# Estrategia force-orphan: cada publish REEMPLAZA el sitio con una rama huérfana fresca (un solo
# commit, sin historia) → crecimiento del repo CERO (siempre pesa lo que UN reporte, no acumula).
# Pages muestra siempre el reporte del ÚLTIMO run en una URL fija. Los reportes históricos quedan
# en los artifacts de Actions (retención propia). Ver docs/testing/qa-trigger-strategy.md §6.
#
# Uso:  publish-report-pages.sh <report_dir> [run_label]
#   report_dir  ej. ai-output/report   (debe tener index.html)
#   run_label   ej. "nightly · SDK 10.0.8"  (texto del commit; default = timestamp)
#
# Requiere git con permiso de push (en CI: permissions contents:write). Usa git worktree → no toca
# el working tree del QA. Imprime la URL pública (última línea de stdout) para que el caller la use.
# Exit: 0 ok · 1 error.
# ============================================================================
set -uo pipefail

REPORT_DIR="${1:?uso: publish-report-pages.sh <report_dir> [label]}"
RUN_LABEL="${2:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

[[ -f "${REPORT_DIR}/index.html" ]] || { echo "✗ no existe ${REPORT_DIR}/index.html — nada que publicar" >&2; exit 1; }

# URL pública de Pages: https://<owner-lower>.github.io/<repo>/  (owner siempre en minúsculas).
ORIGIN_URL="$(git remote get-url origin 2>/dev/null)"
SLUG="$(echo "$ORIGIN_URL" | sed -E 's#.*github.com[:/]([^/]+)/([^/.]+)(\.git)?#\1/\2#')"
OWNER_LC="$(echo "${SLUG%%/*}" | tr '[:upper:]' '[:lower:]')"; REPO="${SLUG##*/}"
PAGES_URL="https://${OWNER_LC}.github.io/${REPO}/"

WORKTREE="$(mktemp -d)"
TMP_BRANCH="_ghp_publish_$$"
cleanup() {
  git worktree remove --force "$WORKTREE" 2>/dev/null || rm -rf "$WORKTREE"
  git branch -D "$TMP_BRANCH" 2>/dev/null || true
}
trap cleanup EXIT

# Worktree huérfano: rama nueva SIN padres → al force-pushear, gh-pages queda con 1 solo commit.
git worktree add --force --detach "$WORKTREE" >/dev/null 2>&1
cd "$WORKTREE"
git checkout --orphan "$TMP_BRANCH" >/dev/null 2>&1
git rm -rf --quiet . >/dev/null 2>&1 || true        # vaciar índice + archivos heredados

cp -r "${PROJECT_ROOT}/${REPORT_DIR}/." .            # reporte a la raíz (index.html = landing)
touch .nojekyll                                      # servir carpetas con guion bajo, sin Jekyll

git add -A
git -c user.name="qa-bot" -c user.email="qa-bot@users.noreply.github.com" \
  commit -q -m "report: ${RUN_LABEL}"
git push -q --force origin "HEAD:gh-pages" || { echo "✗ git push --force a gh-pages falló" >&2; exit 1; }

echo "✓ Reporte publicado (force-orphan, sin acumular historia)" >&2
echo "$PAGES_URL"
