#!/usr/bin/env bash
# ============================================================================
# check-maven-available.sh — ¿está publicado el artefacto del SDK en Maven?
#
# El changelog del SDK puede publicarse ANTES que el binario. Este script
# verifica que la versión exista en Maven Central antes de intentar buildear,
# para abortar barato (sin build, sin gastar tokens de los agentes IA).
#
# Uso:   ./scripts/check-maven-available.sh <version>
#        ./scripts/check-maven-available.sh 10.0.8-alpha08
# Exit:  0 = disponible · 4 = no publicada aún · 2 = error de uso/red
# ============================================================================
set -uo pipefail

GROUP_PATH="io/github/mediastream/mediastreamplatformsdkandroid"
ARTIFACT="mediastreamplatformsdkandroid"
BASE="https://repo1.maven.org/maven2/${GROUP_PATH}"

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "ERROR: falta la versión. Uso: $0 <version>" >&2
  exit 2
fi

POM_URL="${BASE}/${VERSION}/${ARTIFACT}-${VERSION}.pom"

# HEAD al .pom: 200 = publicado, 404 = aún no. -f hace fallar en >=400.
code=$(curl -s -o /dev/null -w '%{http_code}' -I "$POM_URL" 2>/dev/null || echo "000")

case "$code" in
  200)
    echo "✓ SDK $VERSION disponible en Maven Central"
    exit 0 ;;
  404)
    echo "⏳ SDK $VERSION NO está publicado en Maven Central todavía ($POM_URL)" >&2
    exit 4 ;;
  *)
    echo "ERROR: respuesta inesperada de Maven ($code) para $POM_URL" >&2
    exit 2 ;;
esac
