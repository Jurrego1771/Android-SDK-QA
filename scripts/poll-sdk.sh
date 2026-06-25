#!/usr/bin/env bash
# ============================================================================
# poll-sdk.sh — Disparador por POLLING del flujo QA dirigido por el diff del SDK.
#
# Vigila la rama de release del SDK con `gh` (sin tokens cross-repo, sin tocar el repo del SDK).
# Si el HEAD avanzó desde la última vez (merge o push), corre el pipeline QA por diff sobre el
# rango (prev..nuevo). Pensado para correr cada N horas vía el Programador de tareas de Windows.
#
# Config (env, con defaults):
#   SDK_REPO    owner/repo del SDK   (default: mediastream/MediastreamPlatformSDKAndroid)
#   SDK_BRANCH  rama release a vigilar (default: 10.0.8)
#
# Estado local: .sdk-poll-state (gitignored) — el último sha procesado. Idempotente.
#
# Exit: 0 sin cambios / procesado ok · 2 no se pudo consultar el SDK (red/gh) · otro = exit del pipeline.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

SDK_REPO="${SDK_REPO:-mediastream/MediastreamPlatformSDKAndroid}"
SDK_BRANCH="${SDK_BRANCH:-10.0.8}"
STATE_FILE="${PROJECT_ROOT}/.sdk-poll-state"

# HEAD actual de la rama release (vía gh — usa el login del runner, sin tokens).
HEAD_SHA="$(gh api "repos/${SDK_REPO}/commits/${SDK_BRANCH}" --jq '.sha' 2>/dev/null)"
if [[ ! "$HEAD_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "✗ No se pudo leer ${SDK_REPO}@${SDK_BRANCH} (¿rama existe? ¿gh autenticado? ¿red?)." >&2
  exit 2
fi

PREV_SHA="$(cat "$STATE_FILE" 2>/dev/null || echo "")"

# Primera corrida: registrar el estado sin disparar (no reprocesar todo el historial).
if [[ -z "$PREV_SHA" ]]; then
  echo "$HEAD_SHA" > "$STATE_FILE"
  echo "Bootstrap: ${SDK_REPO}@${SDK_BRANCH} en ${HEAD_SHA:0:12}. Estado guardado, sin correr."
  exit 0
fi

if [[ "$PREV_SHA" == "$HEAD_SHA" ]]; then
  echo "Sin cambios en ${SDK_REPO}@${SDK_BRANCH} (${HEAD_SHA:0:12}). No-op."
  exit 0
fi

echo "▶ Cambio en ${SDK_REPO}@${SDK_BRANCH}: ${PREV_SHA:0:12} → ${HEAD_SHA:0:12}. Corriendo pipeline QA…"
export SDK_EVENT=push
export SDK_REPO SDK_REF="$SDK_BRANCH"
export SDK_BASE_SHA="$PREV_SHA" SDK_HEAD_SHA="$HEAD_SHA"

bash "${SCRIPT_DIR}/run-sdk-pipeline.sh"
RC=$?

# Marcar procesado solo en resultados DEFINITIVOS (0 ok · 4 no-en-Maven). En 2 (entorno caído) o
# fallo de etapa NO se marca → el próximo polling reintenta el mismo rango cuando el entorno vuelva.
if [[ $RC -eq 0 || $RC -eq 4 ]]; then
  echo "$HEAD_SHA" > "$STATE_FILE"
  echo "✓ Procesado ${HEAD_SHA:0:12} (exit $RC)."
else
  echo "⚠ Pipeline exit $RC — NO se marca procesado; se reintentará en el próximo polling." >&2
fi
exit $RC
