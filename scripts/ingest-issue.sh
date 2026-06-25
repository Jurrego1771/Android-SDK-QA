#!/usr/bin/env bash
# ============================================================================
# ingest-issue.sh — Adaptador de entrada "issue/descripción" del proceso de QA (shift-left).
#
# Toma una descripción libre o un número de issue de GitHub, la normaliza a source.md/source-meta.txt
# (source_type=issue) y delega al núcleo compartido `qa-core.sh`. NO bumpea el SDK: prueba la versión
# ACTUAL del repo (un issue es sobre el comportamiento del binario ya pineado). Si el cambio aún no
# existe en ese binario, el compile-gate del núcleo lo marcará y el strategist tratará esos casos como
# BLOQUEADOS — el resto del proceso (explorar lo explorable, regresión del área) corre igual.
#
# Uso:
#   ingest-issue.sh "fix: VOD onEnd dispara doble al final"      # descripción libre
#   ingest-issue.sh 42                                            # número de issue de GitHub (gh)
# Env: CHANGE_TYPE (opcional, FIX|FEATURE; default se infiere del texto). gh autenticado para #N.
# Exit: el de qa-core.sh.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"
AI_OUTPUT="${PROJECT_ROOT}/ai-output"; mkdir -p "$AI_OUTPUT"

ENV_FILE="${SCRIPT_DIR}/.env"
[[ -f "$ENV_FILE" ]] && while IFS='=' read -r _k _v; do [[ "$_k" =~ ^[[:space:]]*# || -z "$_k" ]] && continue; [[ -z "${!_k:-}" ]] && export "$_k"="$_v"; done < "$ENV_FILE"

INPUT="${1:?uso: ingest-issue.sh \"<descripción>\" | <#issue>}"

# ── 1. Obtener el contenido del issue ────────────────────────────────────────
if [[ "$INPUT" =~ ^[0-9]+$ ]]; then
  command -v gh >/dev/null || { echo "✗ gh no disponible para leer el issue #$INPUT" >&2; exit 1; }
  TITLE="$(gh issue view "$INPUT" --json title --jq '.title' 2>/dev/null)" || { echo "✗ no se pudo leer el issue #$INPUT" >&2; exit 1; }
  BODY="$(gh issue view "$INPUT" --json body --jq '.body' 2>/dev/null)"
  SRC_REF="GitHub issue #$INPUT"
else
  TITLE="$INPUT"; BODY=""; SRC_REF="descripción libre"
fi

# ── 2. Inferir change_type del texto si no se forzó ──────────────────────────
CT="${CHANGE_TYPE:-}"
if [[ -z "$CT" ]]; then
  if echo "$TITLE $BODY" | grep -qiE '\b(fix|bug|crash|regression|no funciona|falla)\b'; then CT="FIX"; else CT="FEATURE"; fi
fi

# ── 3. Normalizar a source.md / source-meta.txt ──────────────────────────────
{
  echo "# Cambio a validar (issue) — $(date -u +%Y-%m-%d)"
  echo ""
  echo "**Fuente:** ${SRC_REF}"
  echo ""
  echo "## ${TITLE}"
  echo ""
  [[ -n "$BODY" ]] && echo "$BODY"
} > "${AI_OUTPUT}/source.md"

CURRENT="$(grep -oE 'mediastreamplatformsdkandroid:[^"]+' "${PROJECT_ROOT}/app/build.gradle.kts" | head -1 | cut -d: -f2)"
cat > "${AI_OUTPUT}/source-meta.txt" <<META
source_type=issue
sdk_version=${CURRENT}
sdk_branch=
version_line=
change_type=${CT}
issue_ref=${SRC_REF}
META

echo "▶ Issue ingerido: ${SRC_REF} · change_type=${CT} · SDK actual=${CURRENT}"
echo "  → ai-output/source.md + source-meta.txt"

# ── 4. Delegar al núcleo (sin bump: prueba la versión actual) ────────────────
export SOURCE_TYPE=issue
export PR_TITLE="QA issue: ${TITLE:0:60}"
export PR_BODY_EXTRA="Origen: ${SRC_REF}."
bash "${SCRIPT_DIR}/qa-core.sh"
