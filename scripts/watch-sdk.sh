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
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
CLAUDE_FLAGS="${CLAUDE_FLAGS:---permission-mode acceptEdits}"
export SDK_REPO="${SDK_REPO:-mediastream/MediastreamPlatformSDKAndroid}"
export SDK_BRANCH="${SDK_BRANCH:-10.0.8}"

# Estado por-rama: probar feature/vertical a demanda NO debe pisar el "último probado" de la línea
# principal (10.0.8). La rama default conserva el nombre histórico; otras llevan sufijo saneado.
if [[ "$SDK_BRANCH" == "10.0.8" ]]; then
  STATE_FILE="${PROJECT_ROOT}/.last-tested-sdk"
else
  STATE_FILE="${PROJECT_ROOT}/.last-tested-sdk-$(echo "$SDK_BRANCH" | tr '/:' '--')"
fi

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
  # MSYS_NO_PATHCONV/ARG_CONV_EXCL: en Git Bash (Windows) un arg que empieza con "/" se mangla a una
  # ruta Windows (p.ej. "/changelog-analyzer" → "C:/Program Files/Git/changelog-analyzer") y claude
  # recibe basura en vez del slash command. Desactivar la conversión para este argumento.
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" "$CLAUDE_BIN" -p "$slash" $CLAUDE_FLAGS \
    || fail "claude -p $slash error (¿ANTHROPIC_API_KEY? ¿claude en PATH?)"
  [[ -f "$out" ]] || fail "$slash no produjo $out"
  echo "  ✓ $out"
}

# ── 1. Ingesta + origen del binario, según el tipo de rama ────────────────────
# Línea de versión publicada (semver puro, p.ej. 10.0.8) → binario de Maven (flujo histórico).
# Rama de trabajo (feature/*, bug/*, nombres) → build LOCAL del SDK desde la rama → mavenLocal,
#   porque el artefacto de Maven NO corresponde al código de la rama (homónimos desfasados).
if [[ "$SDK_BRANCH" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  log "Rama de versión '${SDK_BRANCH}' → resolver binario en Maven"
  set +e; bash "${SCRIPT_DIR}/fetch-sdk-changelog.sh"; FETCH_RC=$?; set -e
  case $FETCH_RC in
    0) ;;
    1) echo "La línea del changelog aún no tiene artefacto en Maven — no-op (reintenta el próximo cron)."; exit 0 ;;
    *) fail "fetch-sdk-changelog.sh error ($FETCH_RC)" ;;
  esac
else
  log "Rama de trabajo '${SDK_BRANCH}' → build local del SDK desde la rama (mavenLocal)"
  set +e; LOCAL_VER="$(bash "${SCRIPT_DIR}/build-sdk-local.sh" "$SDK_BRANCH")"; BUILD_RC=$?; set -e
  case $BUILD_RC in
    0) ;;
    2) slack "Clon del SDK no disponible en el runner — QA de ${SDK_BRANCH} abortado (entorno)."; echo "Entorno (clon SDK) — exit 2."; exit 2 ;;
    *) fail "build-sdk-local.sh falló (la rama ${SDK_BRANCH} del SDK no compila/publica)" ;;
  esac
  # Changelog de la rama para contexto de los agentes, con la versión del build local (sin gate Maven).
  set +e; SDK_VERSION_OVERRIDE="$LOCAL_VER" bash "${SCRIPT_DIR}/fetch-sdk-changelog.sh"; FETCH_RC=$?; set -e
  [[ $FETCH_RC -eq 0 ]] || fail "fetch-sdk-changelog.sh (override) error ($FETCH_RC)"
fi

SDK_VERSION="$(grep '^sdk_version=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"
[[ -n "$SDK_VERSION" ]] || fail "no se pudo leer sdk_version"

# ── 2. ¿Es nueva vs lo último probado? ───────────────────────────────────────
LAST="$(cat "$STATE_FILE" 2>/dev/null || echo "")"
if [[ "$SDK_VERSION" == "$LAST" ]]; then
  echo "Versión ${SDK_VERSION} ya fue probada (estado). No-op."
  exit 0
fi
log "Versión NUEVA detectada: ${LAST:-(ninguna)} → ${SDK_VERSION}"
slack "Nueva versión del SDK a probar: ${SDK_VERSION} (rama ${SDK_BRANCH}). Corriendo QA…"

# ── 3. Bump del SDK PRIMERO (antes de los agentes) ───────────────────────────
# Los agentes razonan sobre el código REAL bajo test. Si el bump va después (como antes), el
# strategist lee build.gradle viejo y planea sobre la versión equivocada (incoherencia changelog
# vs código). Bump primero → todos los agentes ven la versión correcta.
log "Bump SDK → ${SDK_VERSION} en build.gradle.kts"
sed -i -E "s|(mediastreamplatformsdkandroid:)[^\"]+|\1${SDK_VERSION}|" "$BUILD_GRADLE"
grep -q "mediastreamplatformsdkandroid:${SDK_VERSION}" "$BUILD_GRADLE" || fail "bump no aplicó"

# ── 4. Compile-gate: ¿el QA compila contra el binario nuevo? ─────────────────
# Da a los agentes un HECHO en vez de una adivinanza ("no compilaría"). Escribe el resultado a
# compile-gate.txt para que /test-strategist y /test-generator lo lean. No aborta: un fallo de
# compilación es una señal a documentar (p.ej. breaking change de la interfaz → adaptación, fase 7).
log "Compile-gate (compileDebugAndroidTestKotlin contra ${SDK_VERSION})"
COMPILE_LOG="${AI_OUTPUT}/compile-gate.txt"
set +e
( cd "$PROJECT_ROOT" && ./gradlew :app:compileDebugAndroidTestKotlin --console=plain ) >"$COMPILE_LOG" 2>&1
COMPILE_RC=$?
set -e
if [[ $COMPILE_RC -eq 0 ]]; then
  echo "result=PASS" >> "$COMPILE_LOG"
  echo "  ✓ el QA compila contra ${SDK_VERSION}"
else
  echo "result=FAIL" >> "$COMPILE_LOG"
  echo "  ⚠ el QA NO compila contra ${SDK_VERSION} — los agentes lo verán en compile-gate.txt (¿breaking change? ¿adaptación necesaria?)"
  slack "Compile-gate FAIL para ${SDK_VERSION} (rama ${SDK_BRANCH}): el QA no compila contra el binario nuevo. Ver compile-gate.txt en el PR."
fi

# helper: recompila androidTest y devuelve rc (para los gates de fase 7 y post-generate)
recompile() { set +e; ( cd "$PROJECT_ROOT" && ./gradlew :app:compileDebugAndroidTestKotlin --console=plain ) >"$1" 2>&1; local rc=$?; set -e; echo "$rc"; }

# ── 5. Análisis + estrategia (device no necesario aún) ───────────────────────
run_agent "/changelog-analyzer" "${AI_OUTPUT}/analysis.md"
run_agent "/test-strategist"    "${AI_OUTPUT}/strategy.md"

# ── 5b. FASE 7: crear escenarios para features nuevas (Vía B) si el strategist los pidió ──
# El strategist escribe scenarios-to-create.txt (líneas `key|descripción`) cuando una feature nueva
# necesita una Activity que no existe. Creamos cada una con /activity-creator (que también cablea el
# deeplink), recompilamos, y con 1 intento de fix. Si no compilan, se omiten (la Vía A sigue válida;
# el gate post-generate es la red final). Esto desbloquea cobertura de features nuevas (p.ej. Vertical).
SCENARIOS="${AI_OUTPUT}/scenarios-to-create.txt"
if [[ -s "$SCENARIOS" ]]; then
  log "Fase 7: creando escenarios para features nuevas (Vía B)"
  # activity-creator no produce un archivo de contrato fijo; lo invocamos y validamos por compile-gate.
  while IFS= read -r line; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    log "activity-creator: ${line}"
    "$CLAUDE_BIN" -p "/activity-creator ${line}" $CLAUDE_FLAGS || echo "  ⚠ activity-creator falló para: ${line}"
  done < "$SCENARIOS"
  log "Compile-gate tras activity-creator"
  SC_RC="$(recompile "$COMPILE_LOG")"
  if [[ "$SC_RC" != "0" ]]; then
    echo "  ⚠ escenarios nuevos no compilan — 1 intento de fix (activity-creator modo fix)"
    echo "result=FAIL" >> "$COMPILE_LOG"
    while IFS= read -r line; do [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue; "$CLAUDE_BIN" -p "/activity-creator ${line}" $CLAUDE_FLAGS || true; done < "$SCENARIOS"
    SC_RC="$(recompile "$COMPILE_LOG")"
  fi
  if [[ "$SC_RC" == "0" ]]; then COMPILE_RC=0; echo "result=PASS" >> "$COMPILE_LOG"; echo "  ✓ escenarios nuevos compilan";
  else echo "  ⚠ escenarios nuevos siguen sin compilar — el gate post-generate decidirá (Vía A sigue válida)"; fi
fi

# ── 5c. Instalar el APK NUEVO (tras fase 7) para que el explorer vea los escenarios nuevos ──
EXPLORE_OK=1
if [[ $COMPILE_RC -eq 0 ]]; then
  log "Instalando APK ${SDK_VERSION} en el device (binario + escenarios nuevos)"
  bash "${SCRIPT_DIR}/prep-device.sh" || echo "  ⚠ prep-device falló"
  set +e; ( cd "$PROJECT_ROOT" && ./gradlew :app:installDebug --console=plain ) >>"$COMPILE_LOG" 2>&1; INSTALL_RC=$?; set -e
  [[ $INSTALL_RC -eq 0 ]] && echo "  ✓ app instalada" || { EXPLORE_OK=0; echo "  ⚠ install falló — se omite exploración"; }
else
  EXPLORE_OK=0
fi

# ── 5d. Exploración en device (app nueva + escenarios nuevos instalados) ──────
if [[ $EXPLORE_OK -eq 1 ]]; then
  run_agent "/changelog-explorer" "${AI_OUTPUT}/exploration.md"   # device + MCP
else
  echo "  (se omite /changelog-explorer: APK no instalado — el generator usa contrato + compile-gate)" >&2
fi

# ── 6. Generación de tests ───────────────────────────────────────────────────
run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"

# ── 6b. Compile-gate POST-generate: validar lo generado ANTES de gastar device ─
# Con auto-reparación acotada (1 intento): si los tests generados no compilan, el generator lee los
# errores (compile-gate-tests.txt) y los corrige; si sigue roto, aborta SIN tocar el device.
TESTGATE="${AI_OUTPUT}/compile-gate-tests.txt"
log "Compile-gate post-generate (¿compilan los tests generados?)"
if [[ "$(recompile "$TESTGATE")" != "0" ]]; then
  echo "  ⚠ los tests generados NO compilan — 1 intento de auto-reparación por el generator"
  echo "result=FAIL" >> "$TESTGATE"
  run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"   # lee compile-gate-tests.txt → fix mode
  if [[ "$(recompile "$TESTGATE")" != "0" ]]; then
    echo "result=FAIL" >> "$TESTGATE"
    slack "Tests generados para ${SDK_VERSION} no compilan tras auto-reparación — abort sin device. Ver compile-gate-tests.txt."
    fail "los tests generados no compilan (tras 1 reintento) — no se gasta device. Errores en ${TESTGATE}"
  fi
fi
echo "result=PASS" >> "$TESTGATE"
echo "  ✓ los tests generados compilan"

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
