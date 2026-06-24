#!/usr/bin/env bash
# ============================================================================
# run-changelog-pipeline.sh — Orquestador del pipeline de QA dirigido por changelog.
#
# Encadena: fetch → maven-check → [agentes IA headless] → bump → run-tests → analyzer → PR.
# Cada agente IA corre vía `claude -p "<slash-command>"` (headless) y debe producir su archivo
# de contrato en ai-output/ (fail-fast si no aparece).
#
# Requisitos:
#   - Claude Code CLI en PATH + ANTHROPIC_API_KEY (headless autónomo).
#   - gh autenticado (PR). Device A53 conectado (etapa explore + run-tests).
#
# Exit: 0 = pipeline ok (PR abierto) · 3 = sin cambios (no-op) · 4 = SDK no en Maven
#       2 = entorno caído (device) · 1 = fallo de alguna etapa
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

AI_OUTPUT="${PROJECT_ROOT}/ai-output"
CHANGELOG_DIR="${PROJECT_ROOT}/sdk-changelog"
STATE_FILE="${CHANGELOG_DIR}/.last-processed"
BUILD_GRADLE="${PROJECT_ROOT}/app/build.gradle.kts"

# Flags de claude -p para CI headless. Ajustables por env. El permission-mode debe permitir
# que los agentes escriban archivos sin prompt; en runner aislado se acepta bypassPermissions.
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
CLAUDE_FLAGS="${CLAUDE_FLAGS:---permission-mode acceptEdits}"

log()  { echo -e "\n▶ $*"; }
fail() { echo "✗ $*" >&2; exit 1; }

slack() {  # notificación best-effort (no rompe el pipeline si falla)
  [[ -n "${SLACK_WEBHOOK_URL:-}" ]] || return 0
  curl -s -X POST -H 'Content-type: application/json' \
    --data "{\"text\":\"[changelog-pipeline] $1\"}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
}

# --- Helper: corre un agente IA headless y verifica su archivo de salida -----
run_agent() {
  local slash="$1" out="$2"
  log "Agente $slash (headless)…"
  "$CLAUDE_BIN" -p "$slash" $CLAUDE_FLAGS \
    || fail "claude -p $slash devolvió error (¿ANTHROPIC_API_KEY? ¿claude en PATH?)"
  [[ -f "$out" ]] || fail "$slash no produjo $out"
  echo "  ✓ $out"
}

# ── 0. Guard: ¿cambió la versión? ───────────────────────────────────────────
bash "${SCRIPT_DIR}/detect-changelog-change.sh"
case $? in
  0) ;;                       # versión nueva → seguir
  3) echo "Sin cambios — no-op."; exit 3 ;;
  *) fail "detect-changelog-change.sh error" ;;
esac

# ── 1. Ingesta ──────────────────────────────────────────────────────────────
log "Ingesta del changelog"
bash "${SCRIPT_DIR}/fetch-changelog.sh" || fail "fetch-changelog.sh"
SDK_VERSION="$(grep '^sdk_version=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"
[[ -n "$SDK_VERSION" ]] || fail "no se pudo leer sdk_version"
echo "  versión: $SDK_VERSION"

# ── 2. Maven check (antes de buildear / gastar tokens) ──────────────────────
log "Verificando disponibilidad en Maven"
if ! bash "${SCRIPT_DIR}/check-maven-available.sh" "$SDK_VERSION"; then
  slack "Versión $SDK_VERSION en el changelog pero NO disponible en Maven todavía. Abort limpio."
  echo "SDK $SDK_VERSION no disponible en Maven — abort limpio."; exit 4
fi

# ── 3. Cadena de agentes IA (headless) ──────────────────────────────────────
run_agent "/changelog-analyzer" "${AI_OUTPUT}/analysis.md"
run_agent "/test-strategist"    "${AI_OUTPUT}/strategy.md"
run_agent "/changelog-explorer" "${AI_OUTPUT}/exploration.md"   # requiere device + MCP

# ── 4. Bump de la versión del SDK ───────────────────────────────────────────
log "Bump SDK → $SDK_VERSION en build.gradle.kts"
sed -i -E "s|(mediastreamplatformsdkandroid:)[^\"]+|\1${SDK_VERSION}|" "$BUILD_GRADLE"
grep -q "mediastreamplatformsdkandroid:${SDK_VERSION}" "$BUILD_GRADLE" || fail "bump no aplicó"

# ── 5. Generación de tests (headless) ───────────────────────────────────────
run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"

# ── 6. Ejecutar la suite en device ──────────────────────────────────────────
log "Ejecutando tests en device"
bash "${SCRIPT_DIR}/run-tests.sh" --target mobile --size all --capture-sessions
RUN_EXIT=$?
if [[ $RUN_EXIT -eq 2 ]]; then
  slack "Entorno caído (device/backend) — pipeline abortado sin PR para $SDK_VERSION."
  echo "Entorno caído (exit 2) — no se crea PR."; exit 2
fi
# exit 1 (tests fallaron) NO aborta: el analyzer clasifica y el PR documenta los fallos.

# ── 7. Análisis de resultados (headless) ────────────────────────────────────
run_agent "/test-analyzer" "${AI_OUTPUT}/test-analysis-report.md"

# ── 7b. Diff de sesiones vs baseline + interpretación (Capa C) ───────────────
# Compara las capturas de esta versión (ai-output/report/sessions) contra el baseline aceptado
# (qa-knowledge/session-baselines). Solo si hay baseline Y capturas nuevas; best-effort (no aborta
# el PR si falta material — el diff es complementario al análisis de tests).
BASELINE_DIR="${PROJECT_ROOT}/qa-knowledge/session-baselines"
NEW_SESSIONS_DIR="${AI_OUTPUT}/report/sessions"
if compgen -G "${BASELINE_DIR}/*.json" >/dev/null && compgen -G "${NEW_SESSIONS_DIR}/*.json" >/dev/null; then
  log "Diff de sesiones vs baseline (Capa C)"
  node "${SCRIPT_DIR}/diff-sessions.cjs" "$BASELINE_DIR" "$NEW_SESSIONS_DIR" "${AI_OUTPUT}/session-diff.md"
  DIFF_EXIT=$?
  # exit 0 = diff concluyente · exit 5 = no concluyente (capturas cortadas/set desalineado) → en
  # ambos hay un session-diff.md válido y el agente debe interpretarlo (en el caso 5 reportará
  # RECAPTURAR). Solo otro código (2 = mal invocado) se trata como fallo y se omite el agente.
  if [[ $DIFF_EXIT -eq 0 || $DIFF_EXIT -eq 5 ]]; then
    [[ $DIFF_EXIT -eq 5 ]] && { echo "  ⚠ diff NO concluyente (capturas incompletas) — el reporte recomendará recapturar."; slack "Diff de sesiones $SDK_VERSION no concluyente: capturas incompletas. Revisar version-comparison-report."; }
    run_agent "/version-comparator" "${AI_OUTPUT}/version-comparison-report.md"
  else
    echo "  ⚠ diff-sessions.cjs error de invocación (exit $DIFF_EXIT) — se omite la interpretación (no bloquea el PR)."
  fi
else
  echo "  (sin baseline en ${BASELINE_DIR} o sin capturas nuevas — se omite el diff de sesiones)"
fi

# ── 8. Rama + PR (NO merge → revisión humana) ───────────────────────────────
log "Creando rama + PR"
BRANCH="auto/changelog-${SDK_VERSION}"
git checkout -B "$BRANCH"
echo "$SDK_VERSION" > "$STATE_FILE"   # marca como procesada SOLO al llegar aquí
git add -A
git commit -F - <<COMMIT
test(auto): QA dirigido por changelog SDK ${SDK_VERSION}

Pipeline automático (changelog → analyze → strategist → explore(MCP) → generate → run → analyze).
Bump del SDK a ${SDK_VERSION}. Tests generados/ejecutados; ver ai-output/ para reportes.
REQUIERE REVISIÓN HUMANA antes de merge (principio docs/testing/ai-workflow.md).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
COMMIT

git push -u origin "$BRANCH" || fail "git push"
PR_URL=$(gh pr create --fill --base main --head "$BRANCH" 2>/dev/null \
  --title "QA auto: changelog SDK ${SDK_VERSION}" \
  --body "Pipeline dirigido por changelog. Run exit=${RUN_EXIT}. Reportes en ai-output/ (analysis, strategy, exploration, test-analysis-report, session-diff + version-comparison-report si hubo baseline). **Requiere revisión humana** antes de merge. Si el comparador no reporta regresiones, promover \`ai-output/report/sessions/*.json\` a \`qa-knowledge/session-baselines/\`." \
  || echo "")

slack "PR de QA para SDK ${SDK_VERSION} creado: ${PR_URL:-(ver GitHub)} · run exit=${RUN_EXIT}"
log "Pipeline completo. PR: ${PR_URL:-(revisar GitHub)}"
exit 0
