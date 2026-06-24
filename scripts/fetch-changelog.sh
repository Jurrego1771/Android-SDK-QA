#!/usr/bin/env bash
# ============================================================================
# fetch-changelog.sh — Ingesta del changelog del SDK al contrato ai-output/.
#
# Lee el changelog del SDK versionado en el repo QA (sdk-changelog/) y produce:
#   - ai-output/changelog.md       → la sección MÁS RECIENTE del CHANGELOG (## [..])
#   - ai-output/changelog-meta.txt → versión exacta del artefacto + fecha + ref
#
# La versión EXACTA del artefacto sale de sdk-changelog/version.txt (no del header
# del CHANGELOG, que puede ser genérico, ej. "[10.0.8]" vs artefacto "10.0.8-alpha08").
# Esa versión alimenta a check-maven-available.sh y al bump de build.gradle.kts.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CHANGELOG_DIR="${PROJECT_ROOT}/sdk-changelog"
AI_OUTPUT="${PROJECT_ROOT}/ai-output"
mkdir -p "$AI_OUTPUT"

VERSION_FILE="${CHANGELOG_DIR}/version.txt"
CHANGELOG_FILE="${CHANGELOG_DIR}/CHANGELOG.md"

[[ -f "$VERSION_FILE"  ]] || { echo "ERROR: falta $VERSION_FILE" >&2; exit 2; }
[[ -f "$CHANGELOG_FILE" ]] || { echo "ERROR: falta $CHANGELOG_FILE" >&2; exit 2; }

VERSION="$(tr -d ' \t\r\n' < "$VERSION_FILE")"
[[ -n "$VERSION" ]] || { echo "ERROR: version.txt vacío" >&2; exit 2; }

# Extraer la primera sección "## [..]" (la más reciente, arriba) hasta la siguiente "## ".
# awk: empieza a imprimir en el primer "## ", se detiene en el segundo.
awk '
  /^## / { if (seen) exit; seen=1 }
  seen   { print }
' "$CHANGELOG_FILE" > "${AI_OUTPUT}/changelog.md"

[[ -s "${AI_OUTPUT}/changelog.md" ]] || { echo "ERROR: no se pudo extraer sección del changelog" >&2; exit 2; }

# Header de la sección (para meta/legibilidad).
SECTION_HEADER="$(grep -m1 '^## ' "${AI_OUTPUT}/changelog.md" | sed 's/^## //')"

cat > "${AI_OUTPUT}/changelog-meta.txt" <<META
sdk_version=${VERSION}
section_header=${SECTION_HEADER}
source=sdk-changelog/CHANGELOG.md
fetched_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
META

echo "✓ Changelog ingerido: versión ${VERSION}"
echo "  → ${AI_OUTPUT}/changelog.md ($(wc -l < "${AI_OUTPUT}/changelog.md") líneas)"
echo "  → ${AI_OUTPUT}/changelog-meta.txt"
