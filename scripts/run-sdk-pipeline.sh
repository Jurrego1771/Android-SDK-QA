#!/usr/bin/env bash
# ============================================================================
# run-sdk-pipeline.sh — Orquestador del flujo QA disparado por un cambio en el REPO DEL SDK.
#
# Cabeza = diff REAL del código (PR o push), no un changelog escrito → fuente de verdad.
# Cola   = idéntica al pipeline por changelog (strategist → explore(MCP) → generate → run → analyze
#          → diff de sesiones → version-comparator → PR). Reusa scripts y agentes existentes.
#
# Inputs por entorno (los inyecta el workflow sdk-dispatch.yml desde el payload):
#   SDK_EVENT     pr | push                         (requerido)
#   SDK_REPO      owner/repo del SDK                (requerido)
#   SDK_PR_NUMBER nº de PR                          (si SDK_EVENT=pr)
#   SDK_BASE_SHA  / SDK_HEAD_SHA                     (si SDK_EVENT=push)
#   SDK_REF       rama del SDK (ej. 10.0.x)          (informativo)
#   SDK_VERSION   versión del artefacto a probar     (opcional: si viene y está en Maven, se bumpea)
#
# Exit: 0 ok (PR abierto) · 4 versión no en Maven · 2 entorno caído (device) · 1 fallo de etapa.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

AI_OUTPUT="${PROJECT_ROOT}/ai-output"
BUILD_GRADLE="${PROJECT_ROOT}/app/build.gradle.kts"
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
CLAUDE_FLAGS="${CLAUDE_FLAGS:---permission-mode acceptEdits}"

log()  { echo -e "\n▶ $*"; }
fail() { echo "✗ $*" >&2; exit 1; }
slack() {
  [[ -n "${SLACK_WEBHOOK_URL:-}" ]] || return 0
  curl -s -X POST -H 'Content-type: application/json' \
    --data "{\"text\":\"[sdk-pipeline] $1\"}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
}
run_agent() {
  local slash="$1" out="$2"
  log "Agente $slash (headless)…"
  "$CLAUDE_BIN" -p "$slash" $CLAUDE_FLAGS \
    || fail "claude -p $slash error (¿ANTHROPIC_API_KEY? ¿claude en PATH?)"
  [[ -f "$out" ]] || fail "$slash no produjo $out"
  echo "  ✓ $out"
}

# ── 0. Validar inputs ────────────────────────────────────────────────────────
: "${SDK_EVENT:?Falta SDK_EVENT (pr|push)}"
: "${SDK_REPO:?Falta SDK_REPO (owner/repo del SDK)}"
export SDK_REPO

# ── 1. Obtener el diff real del SDK (cross-repo) ─────────────────────────────
log "Obteniendo diff del SDK ($SDK_EVENT) de $SDK_REPO"
case "$SDK_EVENT" in
  pr)
    : "${SDK_PR_NUMBER:?Falta SDK_PR_NUMBER}"
    node "${SCRIPT_DIR}/fetch-sdk-diff.js" pr "$SDK_PR_NUMBER" || fail "fetch-sdk-diff pr"
    ;;
  push)
    : "${SDK_BASE_SHA:?Falta SDK_BASE_SHA}"; : "${SDK_HEAD_SHA:?Falta SDK_HEAD_SHA}"
    node "${SCRIPT_DIR}/fetch-sdk-diff.js" compare "$SDK_BASE_SHA" "$SDK_HEAD_SHA" || fail "fetch-sdk-diff compare"
    ;;
  *) fail "SDK_EVENT inválido: $SDK_EVENT (pr|push)" ;;
esac
[[ -s "${AI_OUTPUT}/diff.txt" ]] || { echo "Diff vacío (sin archivos relevantes) — no-op."; exit 0; }

# ── 2. Versión a probar + Maven check (opcional) ─────────────────────────────
# Si el payload trae SDK_VERSION y está en Maven → se bumpea. Si no, se prueba contra la versión
# actual de build.gradle (el diff aún puede no tener binario publicado: igual se analiza y se generan
# tests). El explorer/analyzer necesitan la versión: se expone vía changelog-meta.txt.
DO_BUMP=false
if [[ -n "${SDK_VERSION:-}" ]]; then
  log "Verificando $SDK_VERSION en Maven"
  if bash "${SCRIPT_DIR}/check-maven-available.sh" "$SDK_VERSION"; then
    DO_BUMP=true
  else
    slack "SDK $SDK_VERSION en el cambio pero NO en Maven todavía — se prueba contra la versión actual."
    echo "  $SDK_VERSION no en Maven — sin bump (se prueba la versión actual)."
  fi
fi
CUR_VERSION="$(grep -oE 'mediastreamplatformsdkandroid:[^"]+' "$BUILD_GRADLE" | cut -d: -f2 | head -1)"
EFFECTIVE_VERSION="${SDK_VERSION:-$CUR_VERSION}"
# changelog-meta.txt mínimo → el explorer (reusado) lee la versión de aquí.
printf 'sdk_version=%s\nsource=sdk-%s\n' "$EFFECTIVE_VERSION" "$SDK_EVENT" > "${AI_OUTPUT}/changelog-meta.txt"

# ── 3. Cadena de agentes IA (headless) ───────────────────────────────────────
run_agent "/diff-analyzer"   "${AI_OUTPUT}/analysis.md"     # diff.txt → analysis.md (mismo schema)
run_agent "/test-strategist" "${AI_OUTPUT}/strategy.md"
run_agent "/changelog-explorer" "${AI_OUTPUT}/exploration.md"   # device + MCP (lee strategy.md)

# ── 4. Bump opcional ─────────────────────────────────────────────────────────
if [[ "$DO_BUMP" == true ]]; then
  log "Bump SDK → $SDK_VERSION en build.gradle.kts"
  sed -i -E "s|(mediastreamplatformsdkandroid:)[^\"]+|\1${SDK_VERSION}|" "$BUILD_GRADLE"
  grep -q "mediastreamplatformsdkandroid:${SDK_VERSION}" "$BUILD_GRADLE" || fail "bump no aplicó"
fi

# ── 5. Generación de tests + 6. Ejecución en device ──────────────────────────
run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"
log "Ejecutando tests en device"
bash "${SCRIPT_DIR}/run-tests.sh" --target mobile --size all --capture-sessions
RUN_EXIT=$?
if [[ $RUN_EXIT -eq 2 ]]; then
  slack "Entorno caído (device/backend) — pipeline SDK abortado sin PR."
  echo "Entorno caído (exit 2) — no se crea PR."; exit 2
fi

# ── 7. Análisis + 7b. Diff de sesiones vs baseline ───────────────────────────
run_agent "/test-analyzer" "${AI_OUTPUT}/test-analysis-report.md"
BASELINE_DIR="${PROJECT_ROOT}/qa-knowledge/session-baselines"
NEW_SESSIONS_DIR="${AI_OUTPUT}/report/sessions"
if compgen -G "${BASELINE_DIR}/*.json" >/dev/null && compgen -G "${NEW_SESSIONS_DIR}/*.json" >/dev/null; then
  log "Diff de sesiones vs baseline (Capa C)"
  node "${SCRIPT_DIR}/diff-sessions.cjs" "$BASELINE_DIR" "$NEW_SESSIONS_DIR" "${AI_OUTPUT}/session-diff.md"
  DIFF_EXIT=$?
  if [[ $DIFF_EXIT -eq 0 || $DIFF_EXIT -eq 5 ]]; then
    [[ $DIFF_EXIT -eq 5 ]] && slack "Diff de sesiones SDK no concluyente: capturas incompletas."
    run_agent "/version-comparator" "${AI_OUTPUT}/version-comparison-report.md"
  fi
fi

# ── 8. Rama + PR (NO merge → revisión humana) ────────────────────────────────
log "Creando rama + PR"
case "$SDK_EVENT" in
  pr)   TAG="pr-${SDK_PR_NUMBER}" ;;
  push) TAG="push-${SDK_HEAD_SHA:0:7}" ;;
esac
BRANCH="auto/sdk-${TAG}"
git checkout -B "$BRANCH"
git add -A
git commit -F - <<COMMIT
test(auto): QA dirigido por cambio en el SDK (${SDK_EVENT} ${TAG})

Pipeline automático desde el repo del SDK (${SDK_REPO}, ref ${SDK_REF:-?}).
Fuente: diff REAL del código (no changelog). Versión probada: ${EFFECTIVE_VERSION}$([[ "$DO_BUMP" == true ]] && echo " (bump aplicado)").
Cadena: diff-analyzer → strategist → explore(MCP) → generate → run → analyze → version-comparator.
REQUIERE REVISIÓN HUMANA antes de merge.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
COMMIT
git push -u origin "$BRANCH" || fail "git push"
PR_URL=$(gh pr create --fill --base main --head "$BRANCH" 2>/dev/null \
  --title "QA auto: SDK ${SDK_EVENT} ${TAG} (v${EFFECTIVE_VERSION})" \
  --body "Pipeline dirigido por el diff del SDK (${SDK_REPO}). Evento: ${SDK_EVENT} ${TAG}. Run exit=${RUN_EXIT}. Reportes en ai-output/. **Requiere revisión humana** antes de merge." \
  || echo "")
slack "PR de QA para SDK ${SDK_EVENT} ${TAG} (v${EFFECTIVE_VERSION}): ${PR_URL:-(ver GitHub)} · run exit=${RUN_EXIT}"
log "Pipeline SDK completo. PR: ${PR_URL:-(revisar GitHub)}"
exit 0
