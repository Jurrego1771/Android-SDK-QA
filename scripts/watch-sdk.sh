#!/usr/bin/env bash
# ============================================================================
# watch-sdk.sh — Adaptador de entrada "versión del SDK" del proceso de QA.
#
# Resuelve la versión a probar (changelog del SDK + Maven, o build local de una rama de trabajo),
# decide si es nueva, normaliza la entrada a source.md/source-meta.txt, y delega el resto al núcleo
# compartido `qa-core.sh` (change-analyzer → … → PR). Marca la versión como probada solo si se abre PR.
#
# Pensado para el runner self-hosted (usa `gh`, `claude -p`, y el device).
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
  set +e; SDK_VERSION_OVERRIDE="$LOCAL_VER" bash "${SCRIPT_DIR}/fetch-sdk-changelog.sh"; FETCH_RC=$?; set -e
  [[ $FETCH_RC -eq 0 ]] || fail "fetch-sdk-changelog.sh (override) error ($FETCH_RC)"
fi

SDK_VERSION="$(grep '^sdk_version=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"
[[ -n "$SDK_VERSION" ]] || fail "no se pudo leer sdk_version"
VERSION_LINE="$(grep '^version_line=' "${AI_OUTPUT}/changelog-meta.txt" | cut -d= -f2)"

# ── 2. ¿Es nueva vs lo último probado? ───────────────────────────────────────
LAST="$(cat "$STATE_FILE" 2>/dev/null || echo "")"
if [[ "$SDK_VERSION" == "$LAST" ]]; then
  echo "Versión ${SDK_VERSION} ya fue probada (estado). No-op."
  exit 0
fi
log "Versión NUEVA detectada: ${LAST:-(ninguna)} → ${SDK_VERSION}"
slack "Nueva versión del SDK a probar: ${SDK_VERSION} (rama ${SDK_BRANCH}). Corriendo QA…"

# ── 3. Clasificar change_type ────────────────────────────────────────────────
# semver puro estable → RELEASE (batería completa). alpha de línea de versión → VERSION (regresión
# seleccionada). rama bug/* o *fix* → FIX. otra rama → FEATURE.
if   [[ "$SDK_BRANCH" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && ! "$SDK_VERSION" =~ -alpha ]]; then CHANGE_TYPE="RELEASE"
elif [[ "$SDK_BRANCH" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]];                                then CHANGE_TYPE="VERSION"
elif [[ "$SDK_BRANCH" =~ (bug|fix) ]];                                              then CHANGE_TYPE="FIX"
else                                                                                     CHANGE_TYPE="FEATURE"; fi

# ── 4. Normalizar a source.md / source-meta.txt (contrato del núcleo) ─────────
cp -f "${AI_OUTPUT}/changelog.md" "${AI_OUTPUT}/source.md" 2>/dev/null || echo "(sin changelog.md)" > "${AI_OUTPUT}/source.md"
cat > "${AI_OUTPUT}/source-meta.txt" <<META
source_type=changelog
sdk_version=${SDK_VERSION}
sdk_branch=${SDK_BRANCH}
version_line=${VERSION_LINE}
change_type=${CHANGE_TYPE}
META

# ── 5. Delegar al núcleo compartido ──────────────────────────────────────────
export SOURCE_TYPE=changelog SDK_VERSION SDK_BRANCH STATE_FILE
export PR_TITLE="QA auto: SDK ${SDK_VERSION}"
bash "${SCRIPT_DIR}/qa-core.sh"
