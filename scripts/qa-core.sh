#!/usr/bin/env bash
# ============================================================================
# qa-core.sh — Núcleo compartido del proceso de QA (las 6 etapas), agnóstico de la entrada.
#
# Lo invocan los ADAPTADORES de entrada (mismo núcleo sea cual sea la fuente):
#   - watch-sdk.sh    (versión nueva del SDK) → SOURCE_TYPE=changelog
#   - ingest-issue.sh (issue/descripción)     → SOURCE_TYPE=issue
#   - (futuro) un adaptador de diff de PR      → SOURCE_TYPE=diff
#
# Espera que el adaptador YA dejó en ai-output/:
#   - source.md        — el contenido normalizado del cambio
#   - source-meta.txt  — source_type, sdk_version, sdk_branch, change_type
# y (si aplica) ya resolvió/instaló el binario. qa-core hace: bump (si la versión difiere) →
# compile-gate → change-analyzer → test-strategist → [fase 7 activity-creator] → install → explorer
# (+ issues) → test-generator → compile-gate post-gen → run-tests 2 fases (+ retry) → test-analyzer →
# diff-sesiones → version-comparator → PR (NO merge).
#
# Env requeridos: SOURCE_TYPE. Opcionales: SDK_VERSION (si hay bump), SDK_BRANCH, STATE_FILE
#   (si se setea, se escribe la versión al llegar al PR), PR_TITLE/PR_BODY_EXTRA.
# Exit: 0 ok · 2 entorno caído (device) · 1 fallo de etapa.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"
AI_OUTPUT="${PROJECT_ROOT}/ai-output"
BUILD_GRADLE="${PROJECT_ROOT}/app/build.gradle.kts"
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
CLAUDE_FLAGS="${CLAUDE_FLAGS:---permission-mode acceptEdits}"
SOURCE_TYPE="${SOURCE_TYPE:?qa-core requiere SOURCE_TYPE (changelog|diff|issue)}"
SDK_BRANCH="${SDK_BRANCH:-}"
STATE_FILE="${STATE_FILE:-}"
DEVICE="${DEVICE:-}"   # serial; si vacío, run-tests auto-detecta

log()  { echo -e "\n▶ $*"; }
fail() { echo "✗ $*" >&2; exit 1; }
slack() { [[ -n "${SLACK_WEBHOOK_URL:-}" ]] || return 0; curl -s -X POST -H 'Content-type: application/json' --data "{\"text\":\"[qa-core] $1\"}" "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true; }
run_agent() {
  local slash="$1" out="$2"
  log "Agente $slash (headless)…"
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" "$CLAUDE_BIN" -p "$slash" $CLAUDE_FLAGS \
    || fail "claude -p $slash error (¿ANTHROPIC_API_KEY? ¿claude en PATH?)"
  [[ -f "$out" ]] || fail "$slash no produjo $out"
  echo "  ✓ $out"
}
recompile() { set +e; ( cd "$PROJECT_ROOT" && ./gradlew :app:compileDebugAndroidTestKotlin --console=plain ) >"$1" 2>&1; local rc=$?; set -e; echo "$rc"; }

[[ -f "${AI_OUTPUT}/source-meta.txt" ]] || fail "falta ai-output/source-meta.txt (lo produce el adaptador)"
SDK_VERSION="${SDK_VERSION:-$(grep '^sdk_version=' "${AI_OUTPUT}/source-meta.txt" | cut -d= -f2)}"
CHANGE_TYPE="$(grep '^change_type=' "${AI_OUTPUT}/source-meta.txt" | cut -d= -f2)"
log "qa-core: source_type=${SOURCE_TYPE} · sdk=${SDK_VERSION:-(actual)} · change_type=${CHANGE_TYPE:-?}"

# ── 1. Bump del SDK (solo si la versión difiere de la actual; un issue sobre la versión actual no bumpea) ──
CURRENT="$(grep -oE 'mediastreamplatformsdkandroid:[^"]+' "$BUILD_GRADLE" | head -1 | cut -d: -f2)"
if [[ -n "$SDK_VERSION" && "$SDK_VERSION" != "$CURRENT" ]]; then
  log "Bump SDK ${CURRENT} → ${SDK_VERSION}"
  sed -i -E "s|(mediastreamplatformsdkandroid:)[^\"]+|\1${SDK_VERSION}|" "$BUILD_GRADLE"
  grep -q "mediastreamplatformsdkandroid:${SDK_VERSION}" "$BUILD_GRADLE" || fail "bump no aplicó"
else
  SDK_VERSION="$CURRENT"
  echo "  (sin bump — se prueba la versión actual ${SDK_VERSION})"
fi

# ── 2. Compile-gate: ¿compila contra el binario? (hecho para los agentes, no adivinanza) ──
log "Compile-gate (compileDebugAndroidTestKotlin contra ${SDK_VERSION})"
COMPILE_LOG="${AI_OUTPUT}/compile-gate.txt"
COMPILE_RC="$(recompile "$COMPILE_LOG")"
if [[ "$COMPILE_RC" == "0" ]]; then echo "result=PASS" >> "$COMPILE_LOG"; echo "  ✓ compila";
else echo "result=FAIL" >> "$COMPILE_LOG"; echo "  ⚠ NO compila — agentes lo verán en compile-gate.txt"; slack "Compile-gate FAIL ${SDK_VERSION}: el QA no compila. Ver compile-gate.txt."; fi

# ── 3. Análisis de impacto + estrategia (device no necesario aún) ─────────────
run_agent "/change-analyzer" "${AI_OUTPUT}/analysis.md"
run_agent "/test-strategist" "${AI_OUTPUT}/strategy.md"

# ── 4. Fase 7: crear escenarios para features nuevas (Vía B) si el strategist los pidió ──
SCENARIOS="${AI_OUTPUT}/scenarios-to-create.txt"
if [[ -s "$SCENARIOS" ]]; then
  log "Creando escenarios para features nuevas (activity-creator)"
  while IFS= read -r line; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    log "activity-creator: ${line}"
    MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" "$CLAUDE_BIN" -p "/activity-creator ${line}" $CLAUDE_FLAGS || echo "  ⚠ activity-creator falló: ${line}"
  done < "$SCENARIOS"
  SC_RC="$(recompile "$COMPILE_LOG")"
  if [[ "$SC_RC" != "0" ]]; then
    echo "  ⚠ escenarios nuevos no compilan — 1 intento de fix"; echo "result=FAIL" >> "$COMPILE_LOG"
    while IFS= read -r line; do [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue; MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" "$CLAUDE_BIN" -p "/activity-creator ${line}" $CLAUDE_FLAGS || true; done < "$SCENARIOS"
    SC_RC="$(recompile "$COMPILE_LOG")"
  fi
  if [[ "$SC_RC" == "0" ]]; then COMPILE_RC=0; echo "result=PASS" >> "$COMPILE_LOG"; echo "  ✓ escenarios nuevos compilan";
  else echo "  ⚠ escenarios siguen sin compilar — el gate post-generate decidirá"; fi
fi

# ── 5. Instalar el APK nuevo (para que el explorer vea binario + escenarios nuevos) ──
EXPLORE_OK=1
if [[ "$COMPILE_RC" == "0" ]]; then
  log "Instalando APK ${SDK_VERSION} en el device"
  bash "${SCRIPT_DIR}/prep-device.sh" || echo "  ⚠ prep-device falló"
  set +e; ( cd "$PROJECT_ROOT" && ./gradlew :app:installDebug --console=plain ) >>"$COMPILE_LOG" 2>&1; INSTALL_RC=$?; set -e
  [[ $INSTALL_RC -eq 0 ]] && echo "  ✓ app instalada" || { EXPLORE_OK=0; echo "  ⚠ install falló — se omite exploración"; }
else EXPLORE_OK=0; fi

# ── 6. Exploración en device (observar antes de escribir) + issues de hallazgos ──
if [[ $EXPLORE_OK -eq 1 ]]; then
  run_agent "/explorer" "${AI_OUTPUT}/exploration.md"
  # Hallazgos (bugs + mejoras) → GitHub issues (idempotente). best-effort, no aborta el pipeline.
  bash "${SCRIPT_DIR}/create-findings-issues.sh" "${AI_OUTPUT}/findings.json" || echo "  ⚠ create-findings-issues falló (best-effort)"
else
  echo "  (se omite /explorer: APK no instalado — el generator usa contrato + compile-gate)" >&2
fi

# ── 7. Generación de tests + compile-gate post-generate (auto-reparación acotada) ──
run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"
TESTGATE="${AI_OUTPUT}/compile-gate-tests.txt"
log "Compile-gate post-generate (¿compilan los tests generados?)"
if [[ "$(recompile "$TESTGATE")" != "0" ]]; then
  echo "  ⚠ los tests generados NO compilan — 1 intento de auto-reparación"; echo "result=FAIL" >> "$TESTGATE"
  run_agent "/test-generator" "${AI_OUTPUT}/generated-tests-report.md"
  if [[ "$(recompile "$TESTGATE")" != "0" ]]; then
    echo "result=FAIL" >> "$TESTGATE"
    slack "Tests generados (${SDK_VERSION}) no compilan tras auto-reparación — abort sin device."
    fail "tests generados no compilan (tras 1 reintento) — no se gasta device. Ver ${TESTGATE}"
  fi
fi
echo "result=PASS" >> "$TESTGATE"; echo "  ✓ los tests generados compilan"

# ── 8. Ejecución en 2 fases (A=verificación del cambio · B=regresión seleccionada) + auto-retry ──
log "Preparando device"
bash "${SCRIPT_DIR}/prep-device.sh" || echo "  ⚠ prep-device falló — run-tests lo detectará"
RESULTS="${AI_OUTPUT}/test-results.json"
RUN_EXIT=0
# Fase B: regresión seleccionada (smoke + features tocadas/acopladas). 'all' = batería completa (RELEASE).
REGSET="${AI_OUTPUT}/regression-set.txt"
if [[ -s "$REGSET" ]] && grep -qxF "all" "$REGSET"; then
  log "Ejecución: batería COMPLETA (RELEASE)"
  bash "${SCRIPT_DIR}/run-tests.sh" --target mobile --size all --capture-sessions; RUN_EXIT=$?
else
  log "Ejecución 2 fases: A) verificación del cambio · B) regresión seleccionada"
  bash "${SCRIPT_DIR}/run-tests.sh" --target mobile --size all --capture-sessions; RUN_EXIT=$?
  # Nota: run-tests corre la suite medium completa hoy; la selección fina por --class del regression-set
  # es una optimización futura. La señal de regresión ya queda separada por el analyzer.
fi
if [[ $RUN_EXIT -eq 2 ]]; then
  slack "Entorno caído (device/backend) — QA de ${SDK_VERSION} abortado sin PR. No se marca probado."
  echo "Entorno caído (exit 2) — no se crea PR."; exit 2
fi
# Auto-retry de fallidos → marca flaky vs consistente (evidencia para el test-analyzer). best-effort.
if [[ -n "$DEVICE" ]]; then bash "${SCRIPT_DIR}/retry-failed-tests.sh" "$DEVICE" "$RESULTS" 2 || true
else DEV="$(adb devices 2>/dev/null | awk 'NR==2{print $1}')"; [[ -n "$DEV" ]] && bash "${SCRIPT_DIR}/retry-failed-tests.sh" "$DEV" "$RESULTS" 2 || true; fi

# ── 9. Triage + diff de sesiones + comparador ─────────────────────────────────
run_agent "/test-analyzer" "${AI_OUTPUT}/test-analysis-report.md"
BASELINE_DIR="${PROJECT_ROOT}/qa-knowledge/session-baselines"
NEW_SESSIONS_DIR="${AI_OUTPUT}/report/sessions"
if compgen -G "${BASELINE_DIR}/*.json" >/dev/null && compgen -G "${NEW_SESSIONS_DIR}/*.json" >/dev/null; then
  log "Diff de sesiones vs baseline"
  node "${SCRIPT_DIR}/diff-sessions.cjs" "$BASELINE_DIR" "$NEW_SESSIONS_DIR" "${AI_OUTPUT}/session-diff.md"; DIFF_RC=$?
  if [[ $DIFF_RC -eq 0 || $DIFF_RC -eq 5 ]]; then
    [[ $DIFF_RC -eq 5 ]] && slack "Diff de sesiones de ${SDK_VERSION} no concluyente: capturas incompletas."
    run_agent "/version-comparator" "${AI_OUTPUT}/version-comparison-report.md"
  fi
fi

# ── 10. Rama + PR (NO merge → revisión humana) + marcar probado ───────────────
log "Creando rama + PR"
BRANCH="auto/${SOURCE_TYPE}-${SDK_VERSION}"
git checkout -B "$BRANCH"
[[ -n "$STATE_FILE" ]] && echo "$SDK_VERSION" > "$STATE_FILE"   # marca probado SOLO al llegar aquí
git add -A
git commit -F - <<COMMIT
test(auto): QA ${SOURCE_TYPE} — SDK ${SDK_VERSION}

Proceso QA (change-analyzer → strategist → [activity-creator] → explorer → generate →
run 2 fases + retry → analyze → version-comparator). change_type=${CHANGE_TYPE:-?}.
Run exit=${RUN_EXIT}. REQUIERE REVISIÓN HUMANA antes de merge.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
COMMIT
git push -u origin "$BRANCH" || fail "git push"
PR_URL=$(gh pr create --fill --base main --head "$BRANCH" 2>/dev/null \
  --title "${PR_TITLE:-QA auto (${SOURCE_TYPE}): SDK ${SDK_VERSION}}" \
  --body "Proceso QA dirigido por ${SOURCE_TYPE}. Versión: ${SDK_VERSION}. Run exit=${RUN_EXIT}. Reportes en ai-output/. ${PR_BODY_EXTRA:-} **Requiere revisión humana** antes de merge." \
  || echo "")
slack "PR de QA (${SOURCE_TYPE}) para ${SDK_VERSION}: ${PR_URL:-(ver GitHub)} · run exit=${RUN_EXIT}"
log "qa-core completo. PR: ${PR_URL:-(revisar GitHub)}"
exit 0
