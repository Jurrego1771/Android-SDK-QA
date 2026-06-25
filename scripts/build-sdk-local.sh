#!/usr/bin/env bash
# ============================================================================
# build-sdk-local.sh — Buildea el SDK desde una RAMA DE TRABAJO y lo publica a mavenLocal.
#
# Por qué existe: para ramas de feature/fix el artefacto de Maven NO corresponde al código de la
# rama (homónimos desfasados — p.ej. Maven "11.0.0-alpha.01" es de abril, la rama feature/vertical
# es de hoy). Probar Maven da un binario equivocado. En su lugar: checkout de la rama en el clon del
# SDK, publishToMavenLocal, y el QA consume ESE .aar (settings.gradle.kts ya tiene mavenLocal()).
#
# Uso:  build-sdk-local.sh <rama_sdk>
# Env:  SDK_LOCAL_PATH  (default /d/repos/mediastream/MediastreamPlatformSDKAndroid)
#
# Imprime en stdout (última línea) la VERSIÓN publicada (la que declara la rama), para que el caller
# bumpee el QA a ella. Logs de progreso van a stderr.
# Exit: 0 ok · 2 entorno (clon/SDK no disponible) · 1 fallo de build/checkout.
# ============================================================================
set -uo pipefail

BRANCH="${1:?uso: build-sdk-local.sh <rama_sdk>}"
SDK_LOCAL_PATH="${SDK_LOCAL_PATH:-/d/repos/mediastream/MediastreamPlatformSDKAndroid}"
MODULE="mediastreamplatformsdkandroid"

log(){ echo -e "\n▶ $*" >&2; }
err(){ echo "✗ $*" >&2; }

[[ -d "$SDK_LOCAL_PATH/.git" ]] || { err "clon del SDK no encontrado en $SDK_LOCAL_PATH (set SDK_LOCAL_PATH)"; exit 2; }
command -v git >/dev/null || { err "git no disponible"; exit 2; }

cd "$SDK_LOCAL_PATH"

# Guardar la rama actual del clon para restaurarla al final (no dejar al usuario en otra rama).
ORIG_REF="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
restore(){ [[ -n "$ORIG_REF" && "$ORIG_REF" != "$BRANCH" ]] && git checkout -q "$ORIG_REF" 2>/dev/null || true; }
trap restore EXIT

log "Checkout de la rama del SDK: ${BRANCH}"
git fetch -q origin "$BRANCH" 2>/dev/null || { err "git fetch origin $BRANCH falló (¿rama existe? ¿red?)"; exit 1; }
git checkout -q -B "$BRANCH" "origin/${BRANCH}" 2>/dev/null || { err "checkout de origin/$BRANCH falló"; exit 1; }
git reset -q --hard "origin/${BRANCH}" 2>/dev/null || true   # HEAD exacto de la rama

# Versión que declara la rama (lo que publicará a mavenLocal).
VERSION="$(grep -E '^version\s*=' "${MODULE}/build.gradle.kts" 2>/dev/null | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[[ -n "$VERSION" ]] || { err "no se pudo leer 'version' de ${MODULE}/build.gradle.kts"; exit 1; }
echo "  versión de la rama: ${VERSION}" >&2

# local.properties (sdk.dir) — necesario para que Gradle del SDK encuentre el Android SDK.
if [[ ! -f local.properties ]]; then
  echo "sdk.dir=C\\:\\\\Users\\\\Neo\\\\AppData\\\\Local\\\\Android\\\\Sdk" > local.properties
fi

# assembleRelease + generatePom (NO publishToMavenLocal: el build del SDK declara el sources jar dos
# veces — withSourcesJar() + artifact(androidSourcesJar) — y la tarea de publish falla con "multiple
# artifacts identical jar:sources"). Generamos AAR+POM y los instalamos a ~/.m2 a mano.
log "Compilando AAR + POM (${MODULE}:${VERSION})…"
GRADLE="./gradlew"; [[ -x "$GRADLE" ]] || GRADLE="gradle"
if ! $GRADLE ":${MODULE}:assembleRelease" ":${MODULE}:generatePomFileForReleasePublication" --console=plain >&2; then
  err "build del SDK de la rama ${BRANCH} falló (no compila)"
  exit 1
fi

AAR="${MODULE}/build/outputs/aar/${MODULE}-release.aar"
POM="${MODULE}/build/publications/release/pom-default.xml"
[[ -f "$AAR" && -f "$POM" ]] || { err "no se generaron AAR/POM (¿cambió la estructura del build del SDK?)"; exit 1; }

# Instalar a mavenLocal con el layout estándar (groupId io.github.mediastream).
DEST="${HOME}/.m2/repository/io/github/mediastream/${MODULE}/${VERSION}"
mkdir -p "$DEST"
cp -f "$AAR" "${DEST}/${MODULE}-${VERSION}.aar"
cp -f "$POM" "${DEST}/${MODULE}-${VERSION}.pom"

echo "  ✓ ${MODULE}:${VERSION} instalado en mavenLocal: ${DEST}" >&2
echo "$VERSION"   # ← última línea de stdout: la versión, para el caller
