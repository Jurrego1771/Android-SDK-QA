#!/usr/bin/env bash
# ============================================================================
# watch-sdk.sh — Cerebro del cron de QA dirigido por el SDK (autónomo, cero dependencia del SDK).
#
# Cada corrida (cron):
#   1. Lee el CHANGELOG.md del repo del SDK (gh) y resuelve la versión EXACTA en Maven.
#        - si la línea aún no tiene artefacto en Maven → no-op (la próxima pasada reintenta).
#   2. ¿Es nueva vs la última probada (.last-tested-sdk)?  no → no-op (barato, no toca el device).
#   3. Si es nueva → corre el pipeline completo (changelog-analyzer → strategist → explore(MCP) →
#        bump → generate → run en device → analyze → diff-sesiones → version-comparator → PR).
#   4. Marca la versión como probada SOLO si el pipeline llega a abrir el PR.
#
# Pensado para correr en el runner self-hosted (usa `gh`, `claude -p`, y el device).
#
# Exit: 0 (procesado / no-op) · 2 entorno caído (device) · 1 fallo de etapa.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

# Cargar scripts/.env (ANTHROPIC_API_KEY, SLACK_WEBHOOK_URL) sin pisar el entorno.
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ -f "$ENV_FILE" ]]; then
  while IFS='=' read -r _k _v; do
    [[ "$_k" =~ ^[[:space:]]*# || -z "$_k" ]] && continue
    [[ -z "${!_k:-}" ]] && export "$_k"="$_v"
  done < "$ENV_FILE"
fi

AI_OUTPUT="${PROJECT_ROOT}/ai-output"
BUILD_GRADLE="${PROJECT_ROOT}/app/build.gradle.kts"
STATE_FILE="${PROJECT_ROOT}/.last-tested-sdk"
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
CLAUDE_FLAGS="${CLAUDE_FLAGS:---permission-mode acceptEdits}"
export SDK_REPO="${SDK_REPO:-mediastream/MediastreamPlatformSDKAndroid}"
export SDK_BRANCH="${SDK_BRANCH:-10.0.8}"

log()  { echo -e "\n▶ $*"; }
fail() { echo "✗ $*" >&2; exit 1; }
slack() {
  [[ -n "${SLACK_WEBHOOK_URL:-}" ]] || return 0
  curl -s -X POST -H 'Content-type: application/json' \
    --data "{\"text\":\"[watch-sdk] $1\"}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
}
run_agent() {
  local slash="$1" out="$2"
  log "Agente $slash (headless)…"
  "$CLAUDE_BIN" -p "$slash" $CLAUDE_FLAGS || fail "claude -p $slash error (¿ANTHROPIC_API_KEY? ¿claude en PATH?)"
  [[ -f "$out" ]] || fail "$slash no produjo $out"
  echo "  ✓ $out"
}

# ── 1. Ingesta del changelog del SDK + resolución de versión en Maven ─────────
log "Leyendo changelog del SDK (${SDK_REPO}@${SDK_BRANCH}) y resolviendo versión en Maven"
set +e
bash "${SCRIPT_DIR}/fetch-sdk-changelog.sh"; FETCH_RC=$?
set -e
case $FETCH_RC in
  0) ;;
  1) echo "La línea del changelog aún no tiene artefacto en Maven — no-op (se reintenta en el próximo cron)."; exit 0 ;;
  *) fail "fetch-sdk-changelog.sh error ($FETCH_RC)" ;;
esac

SDK_VERSION="$(grep '^sdk_version=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"
[[ -n "$SDK_VERSION" ]] || fail "no se pudo leer sdk_version"

# ── 2. ¿Es nueva vs lo último probado? ───────────────────────────────────────
LAST="$(cat "$STATE_FILE" 2>/dev/null || echo "")"
if [[ "$SDK_VERSION" == "$LAST" ]]; then
  echo "Versión ${SDK_VERSION} ya fue probada (estado). No-op."
  exit 0
fi
log "Versión NUEVA detectada: ${LAST:-(ninguna)} → ${SDK_VERSION}"
slack "Nueva versión del SDK detectada en Maven: ${SDK_VERSION}. Corriendo QA…"

# ── 3. Cadena de agentes (headless) ──────────────────────────────────────────
run_agent "/changelog-analyzer" "${AI_OUTPUT}/analysis.md"
run_agent "/test-strategist"    "${AI_OUTPUT}/strategy.md"
run_agent "/changelog-explorer" "${AI_OUTPUT}/exploration.md"   # device + MCP

# ── 4. Bump del SDK a la versión exacta resuelta ─────────────────────────────
log "Bump SDK → ${SDK_VERSION} en build.gradle.kts"
sed -i -E "s|(mediastreamplatformsdkandroid:)[^\"]+|\1${SDK_VERSION}|" "$BUILD_GRADLE"
grep -q "mediastreamplatformsdkandroid:${SDK_VERSION}" "$BUILD_GRADLE" || fail "bump no aplicó"

# ── 5. Generación + 6. Ejecución en device ───────────────────────────────────
run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"
log "Preparando device (anti-suspensión, idempotente)"
bash "${SCRIPT_DIR}/prep-device.sh" || echo "  ⚠ prep-device falló (¿device conectado?) — run-tests lo detectará"
log "Ejecutando tests en device"
bash "${SCRIPT_DIR}/run-tests.sh" --target mobile --size all --capture-sessions
RUN_EXIT=$?
if [[ $RUN_EXIT -eq 2 ]]; then
  slack "Entorno caído (device/backend) — QA de ${SDK_VERSION} abortado sin PR. No se marca probado; reintenta."
  echo "Entorno caído (exit 2) — no se crea PR, no se marca probado."; exit 2
fi

# ── 7. Análisis + 7b. Diff de sesiones vs baseline ───────────────────────────
run_agent "/test-analyzer" "${AI_OUTPUT}/test-analysis-report.md"
BASELINE_DIR="${PROJECT_ROOT}/qa-knowledge/session-baselines"
NEW_SESSIONS_DIR="${AI_OUTPUT}/report/sessions"
if compgen -G "${BASELINE_DIR}/*.json" >/dev/null && compgen -G "${NEW_SESSIONS_DIR}/*.json" >/dev/null; then
  log "Diff de sesiones vs baseline (Capa C)"
  node "${SCRIPT_DIR}/diff-sessions.cjs" "$BASELINE_DIR" "$NEW_SESSIONS_DIR" "${AI_OUTPUT}/session-diff.md"
  DIFF_RC=$?
  if [[ $DIFF_RC -eq 0 || $DIFF_RC -eq 5 ]]; then
    [[ $DIFF_RC -eq 5 ]] && slack "Diff de sesiones de ${SDK_VERSION} no concluyente: capturas incompletas."
    run_agent "/version-comparator" "${AI_OUTPUT}/version-comparison-report.md"
  fi
fi

# ── 8. Rama + PR (NO merge → revisión humana) + marcar probado ───────────────
log "Creando rama + PR"
BRANCH="auto/sdk-${SDK_VERSION}"
git checkout -B "$BRANCH"
echo "$SDK_VERSION" > "$STATE_FILE"   # marca probado SOLO al llegar aquí
git add -A
git commit -F - <<COMMIT
test(auto): QA autónomo dirigido por el SDK ${SDK_VERSION}

Detectado por watch-sdk (changelog ${SDK_REPO}@${SDK_BRANCH} línea $(grep '^version_line=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2) → artefacto ${SDK_VERSION} en Maven).
Cadena: changelog-analyzer → strategist → explore(MCP) → generate → run(device) → analyze → version-comparator.
Bump del SDK a ${SDK_VERSION}. Run exit=${RUN_EXIT}. REQUIERE REVISIÓN HUMANA antes de merge.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
COMMIT

git push -u origin "$BRANCH" || fail "git push"
PR_URL=$(gh pr create --fill --base main --head "$BRANCH" 2>/dev/null \
  --title "QA auto: SDK ${SDK_VERSION}" \
  --body "QA autónomo dirigido por el changelog del SDK + gate de Maven. Versión: ${SDK_VERSION}. Run exit=${RUN_EXIT}. Reportes en ai-output/. **Requiere revisión humana** antes de merge." \
  || echo "")
slack "PR de QA para SDK ${SDK_VERSION}: ${PR_URL:-(ver GitHub)} · run exit=${RUN_EXIT}"
log "watch-sdk completo. PR: ${PR_URL:-(revisar GitHub)}"
exit 0
