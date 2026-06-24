#!/usr/bin/env bash
# ============================================================================
# detect-changelog-change.sh — Guard del gatillador (push).
#
# El changelog del SDK se versiona en sdk-changelog/. El workflow se dispara por
# push a sdk-changelog/**, pero un push puede no cambiar la VERSIÓN (ej. typo en
# el texto). Este guard confirma el cambio real comparando la versión declarada
# contra la última procesada.
#
# Exit:  0 = versión nueva → seguir el pipeline
#        3 = sin cambios   → no-op (el workflow termina silencioso)
#        2 = error
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CHANGELOG_DIR="${PROJECT_ROOT}/sdk-changelog"

VERSION_FILE="${CHANGELOG_DIR}/version.txt"
STATE_FILE="${CHANGELOG_DIR}/.last-processed"

[[ -f "$VERSION_FILE" ]] || { echo "ERROR: falta $VERSION_FILE" >&2; exit 2; }
CURRENT="$(tr -d ' \t\r\n' < "$VERSION_FILE")"
[[ -n "$CURRENT" ]] || { echo "ERROR: version.txt vacío" >&2; exit 2; }

LAST=""
[[ -f "$STATE_FILE" ]] && LAST="$(tr -d ' \t\r\n' < "$STATE_FILE")"

if [[ "$CURRENT" == "$LAST" ]]; then
  echo "Sin cambios: versión $CURRENT ya procesada. No-op."
  exit 3
fi

echo "Cambio detectado: $LAST → $CURRENT"
# NO escribimos el estado aquí — lo hace el orquestador SOLO si el pipeline
# completa (para no marcar como procesada una versión que falló a mitad).
exit 0
