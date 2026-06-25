#!/usr/bin/env bash
# ============================================================================
# resolve-sdk-version.sh — Resuelve la versión EXACTA del SDK a probar desde Maven Central.
#
# El changelog/PR del SDK declara una LÍNEA (ej. "10.0.8"), pero Maven solo tiene los artefactos
# concretos (10.0.8-alpha08, 10.0.8-alpha-eu-01, ...). Este script consulta maven-metadata.xml y
# devuelve el ÚLTIMO artefacto publicado de esa línea — la versión real que se puede probar.
#
# Uso:
#   resolve-sdk-version.sh <linea>          # ej. 10.0.8 → último 10.0.8* publicado
#   resolve-sdk-version.sh latest           # último artefacto publicado (cualquier línea)
#   resolve-sdk-version.sh <linea> --plain  # solo la versión (para capturar en una var)
#
# Salida (stdout): la versión exacta (ej. 10.0.8-alpha08). Exit: 0 encontrada · 1 ninguna · 2 error red.
# ============================================================================
set -uo pipefail

GROUP_PATH="io/github/mediastream/mediastreamplatformsdkandroid"
META_URL="https://repo1.maven.org/maven2/${GROUP_PATH}/maven-metadata.xml"

LINE="${1:-latest}"
PLAIN=false; [[ "${2:-}" == "--plain" ]] && PLAIN=true

XML="$(curl -s --max-time 15 "$META_URL" 2>/dev/null)"
[[ -n "$XML" ]] || { echo "✗ No se pudo leer maven-metadata.xml" >&2; exit 2; }

# Todas las versiones, en orden de publicación (el XML las lista cronológicamente).
mapfile -t ALL < <(echo "$XML" | grep -oE '<version>[^<]+</version>' | sed -E 's#</?version>##g')
[[ ${#ALL[@]} -gt 0 ]] || { echo "✗ metadata sin versiones" >&2; exit 2; }

# Por defecto se resuelve la progresión MAINLINE: la línea exacta o `-alphaNN` (incl. `-alpha.NN`).
# Se EXCLUYEN variantes especiales (`-alpha-eu-*`, `-eu-hotfix-*`, etc.) que requieren config aparte
# (env EU, accessToken) y romperían el harness estándar. Con `ANY_VARIANT=1` se toma el último de
# cualquier variante de la línea.
ANY_VARIANT="${ANY_VARIANT:-0}"
is_mainline() { [[ "$1" =~ ^${LINE//./\\.}(-alpha\.?[0-9]+)?$ ]]; }

pick=""
if [[ "$LINE" == "latest" ]]; then
  pick="$(echo "$XML" | grep -oE '<release>[^<]+</release>' | sed -E 's#</?release>##g' | tail -1)"
  [[ -z "$pick" ]] && pick="${ALL[-1]}"
else
  for v in "${ALL[@]}"; do                       # el XML está en orden de publicación → el último gana
    if [[ "$ANY_VARIANT" == "1" ]]; then
      [[ "$v" == "$LINE" || "$v" == "$LINE-"* ]] && pick="$v"
    else
      is_mainline "$v" && pick="$v"
    fi
  done
fi

[[ -n "$pick" ]] || { $PLAIN || echo "✗ Sin artefactos publicados para la línea '$LINE'" >&2; exit 1; }

if $PLAIN; then echo "$pick"; else echo "Línea '$LINE' → último publicado en Maven: $pick"; fi
exit 0
