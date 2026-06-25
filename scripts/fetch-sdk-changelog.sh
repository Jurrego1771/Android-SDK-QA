#!/usr/bin/env bash
# ============================================================================
# fetch-sdk-changelog.sh — Ingesta del CHANGELOG.md del repo del SDK (cross-repo, vía gh).
#
# A diferencia de fetch-changelog.sh (lee la copia local en sdk-changelog/), este trae el
# CHANGELOG.md directo del repo del SDK con `gh` (el login del runner, sin tokens) y resuelve la
# versión EXACTA a probar consultando Maven (resolve-sdk-version.sh) — porque el header del changelog
# es una LÍNEA ("## [10.0.8]") y Maven tiene el artefacto concreto ("10.0.8-alpha08").
#
# Produce el MISMO contrato que consume /changelog-analyzer:
#   ai-output/changelog.md       → la sección más reciente del CHANGELOG
#   ai-output/changelog-meta.txt → sdk_version=<exacto de Maven> + línea + ref
#
# Config (env): SDK_REPO (default mediastream/...), SDK_BRANCH (default 10.0.8).
# Exit: 0 ok · 1 versión de la línea aún no publicada en Maven · 2 error (changelog/red).
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AI_OUTPUT="${PROJECT_ROOT}/ai-output"
mkdir -p "$AI_OUTPUT"

SDK_REPO="${SDK_REPO:-mediastream/MediastreamPlatformSDKAndroid}"
SDK_BRANCH="${SDK_BRANCH:-10.0.8}"

# 1. Bajar el CHANGELOG.md del repo del SDK (gh = login del runner; sin tokens).
RAW="$(gh api "repos/${SDK_REPO}/contents/CHANGELOG.md?ref=${SDK_BRANCH}" --jq '.content' 2>/dev/null | base64 -d 2>/dev/null)"
[[ -n "$RAW" ]] || { echo "✗ No se pudo leer CHANGELOG.md de ${SDK_REPO}@${SDK_BRANCH}" >&2; exit 2; }

# 2. Extraer la sección más reciente (primer "## [..]" hasta el siguiente "## ").
echo "$RAW" | awk '/^## / { if (seen) exit; seen=1 } seen { print }' > "${AI_OUTPUT}/changelog.md"
[[ -s "${AI_OUTPUT}/changelog.md" ]] || { echo "✗ No se pudo extraer sección del changelog" >&2; exit 2; }

SECTION_HEADER="$(grep -m1 '^## ' "${AI_OUTPUT}/changelog.md" | sed 's/^## //')"

# 3. Línea de versión del header: "## [10.0.8] — ..." → 10.0.8
LINE="$(echo "$SECTION_HEADER" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
[[ -n "$LINE" ]] || { echo "✗ No se halló versión X.Y.Z en el header: $SECTION_HEADER" >&2; exit 2; }

# 4. Resolver la versión EXACTA publicada en Maven para esa línea (gate de disponibilidad incluido).
EXACT="$(bash "${SCRIPT_DIR}/resolve-sdk-version.sh" "$LINE" --plain 2>/dev/null)"
if [[ -z "$EXACT" ]]; then
  echo "Línea $LINE aún sin artefacto en Maven — no hay binario que probar todavía." >&2
  exit 1
fi

cat > "${AI_OUTPUT}/changelog-meta.txt" <<META
sdk_version=${EXACT}
version_line=${LINE}
section_header=${SECTION_HEADER}
source=${SDK_REPO}@${SDK_BRANCH}:CHANGELOG.md
fetched_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
META

echo "✓ Changelog del SDK ingerido: línea ${LINE} → artefacto ${EXACT}"
echo "  → ${AI_OUTPUT}/changelog.md ($(wc -l < "${AI_OUTPUT}/changelog.md") líneas)"
echo "  → ${AI_OUTPUT}/changelog-meta.txt"
