#!/usr/bin/env bash
# ============================================================================
# run-changelog-pipeline.sh — Adaptador de entrada "changelog versionado en el repo QA".
#
# Trigger: push que toca sdk-changelog/ (version.txt + CHANGELOG.md). Un guard corta si la versión
# no cambió. Ingiere el changelog local, verifica Maven, normaliza a source.md/source-meta.txt y
# delega al núcleo compartido `qa-core.sh` (change-analyzer → … → PR). Mismo núcleo que watch-sdk.sh
# e ingest-issue.sh → sin divergencia de lógica.
#
# Exit: 0 ok (PR) · 3 sin cambios (no-op) · 4 SDK no en Maven · 2 entorno caído · 1 fallo de etapa.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"
AI_OUTPUT="${PROJECT_ROOT}/ai-output"
CHANGELOG_DIR="${PROJECT_ROOT}/sdk-changelog"
STATE_FILE="${CHANGELOG_DIR}/.last-processed"

log()  { echo -e "\n▶ $*"; }
fail() { echo "✗ $*" >&2; exit 1; }
slack() { [[ -n "${SLACK_WEBHOOK_URL:-}" ]] || return 0; curl -s -X POST -H 'Content-type: application/json' --data "{\"text\":\"[changelog-pipeline] $1\"}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true; }

# ── 0. Guard: ¿cambió la versión? ───────────────────────────────────────────
bash "${SCRIPT_DIR}/detect-changelog-change.sh"
case $? in
  0) ;;                       # versión nueva → seguir
  3) echo "Sin cambios — no-op."; exit 3 ;;
  *) fail "detect-changelog-change.sh error" ;;
esac

# ── 1. Ingesta del changelog local (sdk-changelog/) ──────────────────────────
log "Ingesta del changelog"
bash "${SCRIPT_DIR}/fetch-changelog.sh" || fail "fetch-changelog.sh"
SDK_VERSION="$(grep '^sdk_version=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"
[[ -n "$SDK_VERSION" ]] || fail "no se pudo leer sdk_version"
VERSION_LINE="$(grep '^version_line=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"
echo "  versión: $SDK_VERSION"

# ── 2. Maven check (antes de gastar device/tokens) ──────────────────────────
log "Verificando disponibilidad en Maven"
if ! bash "${SCRIPT_DIR}/check-maven-available.sh" "$SDK_VERSION"; then
  slack "Versión $SDK_VERSION en el changelog pero NO disponible en Maven todavía. Abort limpio."
  echo "SDK $SDK_VERSION no disponible en Maven — abort limpio."; exit 4
fi

# ── 3. change_type + normalizar a source.md/source-meta.txt ──────────────────
if [[ "$SDK_VERSION" =~ -alpha ]]; then CHANGE_TYPE="VERSION"; else CHANGE_TYPE="RELEASE"; fi
cp -f "${AI_OUTPUT}/changelog.md" "${AI_OUTPUT}/source.md" 2>/dev/null || echo "(sin changelog.md)" > "${AI_OUTPUT}/source.md"
cat > "${AI_OUTPUT}/source-meta.txt" <<META
source_type=changelog
sdk_version=${SDK_VERSION}
sdk_branch=${VERSION_LINE}
version_line=${VERSION_LINE}
change_type=${CHANGE_TYPE}
META

# ── 4. Delegar al núcleo compartido ──────────────────────────────────────────
export SOURCE_TYPE=changelog SDK_VERSION STATE_FILE
export SDK_BRANCH="${VERSION_LINE}"
export PR_TITLE="QA auto: changelog SDK ${SDK_VERSION}"
bash "${SCRIPT_DIR}/qa-core.sh"
