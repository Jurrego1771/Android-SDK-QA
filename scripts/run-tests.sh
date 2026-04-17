#!/usr/bin/env bash
# =============================================================================
# run-tests.sh — Build, deploy y ejecutar tests del SDK QA en un dispositivo
#
# Uso:
#   ./scripts/run-tests.sh [OPCIONES]
#
# Opciones:
#   --target   mobile|tv|firetv|all   Filtro de dispositivo    (default: all)
#   --device   <serial>               Serial ADB específico    (default: auto)
#   --size     medium|large|all       Filtro de tamaño de test (default: medium)
#   --class    <NombreClase>          Correr solo esa clase    (ej: VodIntegrationTest)
#   --package  <paquete>              Correr solo ese paquete  (ej: smoke)
#   --smoke                           Alias para --package smoke
#   --skip-build                      Saltear el paso de Gradle build
#   --no-report                       No generar HTML report al final
#   -h, --help                        Mostrar esta ayuda
#
# Ejemplos:
#   ./scripts/run-tests.sh
#   ./scripts/run-tests.sh --target tv
#   ./scripts/run-tests.sh --target mobile --size large
#   ./scripts/run-tests.sh --device 192.168.1.100:5555 --target tv
#   ./scripts/run-tests.sh --smoke
#   ./scripts/run-tests.sh --class AudioFocusTest
#   ./scripts/run-tests.sh --skip-build --target all --size all
# =============================================================================

set -euo pipefail

# ─── Colores ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()      { echo -e "${GREEN}[ OK ]${NC}  $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_step()    { echo -e "\n${BOLD}${CYAN}══ $* ══${NC}"; }
log_error()   { echo -e "${RED}[ERR ]${NC}  $*" >&2; exit 1; }

# ─── Defaults ─────────────────────────────────────────────────────────────────
TARGET="all"
DEVICE_SERIAL=""
SIZE_FILTER="medium"
SKIP_BUILD=false
TEST_CLASS=""
TEST_PACKAGE=""
SMOKE_ONLY=false
NO_REPORT=false

APP_PACKAGE="com.example.sdk_qa"
ANNOTATION_PKG="${APP_PACKAGE}.annotation"
TEST_RUNNER="${APP_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

APK_APP="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
APK_TEST="${PROJECT_ROOT}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

AI_OUTPUT="${PROJECT_ROOT}/ai-output"
INSTRUMENT_RAW="${AI_OUTPUT}/instrument-raw.txt"
RESULTS_JSON="${AI_OUTPUT}/test-results.json"
REPORT_DIR="${AI_OUTPUT}/report"

# Detectar si estamos en Windows (Git Bash / MSYS)
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    GRADLEW="${PROJECT_ROOT}/gradlew.bat"
else
    GRADLEW="${PROJECT_ROOT}/gradlew"
fi

# ─── Ayuda ────────────────────────────────────────────────────────────────────
show_help() {
    sed -n '/^# Uso:/,/^# ====/p' "$0" | grep "^#" | sed 's/^# \{0,2\}//'
}

# ─── Parsear argumentos ───────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --target)      TARGET="$2";        shift 2 ;;
        --device)      DEVICE_SERIAL="$2"; shift 2 ;;
        --size)        SIZE_FILTER="$2";   shift 2 ;;
        --class)       TEST_CLASS="$2";    shift 2 ;;
        --package)     TEST_PACKAGE="$2";  shift 2 ;;
        --smoke)       SMOKE_ONLY=true;    shift   ;;
        --skip-build)  SKIP_BUILD=true;    shift   ;;
        --no-report)   NO_REPORT=true;     shift   ;;
        -h|--help)     show_help; exit 0           ;;
        *) log_error "Opción desconocida: '$1'. Usa --help para ver las opciones." ;;
    esac
done

# ─── Validar argumentos ───────────────────────────────────────────────────────
[[ "$TARGET" =~ ^(mobile|tv|firetv|all)$ ]] || \
    log_error "--target debe ser: mobile | tv | firetv | all (recibido: '$TARGET')"

[[ "$SIZE_FILTER" =~ ^(medium|large|all)$ ]] || \
    log_error "--size debe ser: medium | large | all (recibido: '$SIZE_FILTER')"

command -v adb &>/dev/null || \
    log_error "adb no encontrado. Agrega Android SDK Platform Tools al PATH."

mkdir -p "$AI_OUTPUT" "$REPORT_DIR/screenshots" "$REPORT_DIR/videos"

# ─── Banner ───────────────────────────────────────────────────────────────────
echo -e ""
echo -e "${BOLD}${CYAN}╔═══════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║   Mediastream SDK QA — Test Runner    ║${NC}"
echo -e "${BOLD}${CYAN}╚═══════════════════════════════════════╝${NC}"
echo -e ""

# ─── Paso 1: Selección de dispositivo ─────────────────────────────────────────
log_step "Dispositivo"

# Obtener lista de dispositivos conectados
DEVICES=$(adb devices 2>/dev/null | tail -n +2 | grep "device$" | awk '{print $1}' || true)
DEVICE_COUNT=$(echo "$DEVICES" | grep -c . 2>/dev/null || echo 0)

if [[ -z "$DEVICES" || "$DEVICE_COUNT" -eq 0 ]]; then
    log_error "No hay dispositivos conectados.\n  Conecta un dispositivo físico o inicia un emulador."
fi

if [[ -n "$DEVICE_SERIAL" ]]; then
    echo "$DEVICES" | grep -q "^${DEVICE_SERIAL}$" || \
        log_error "Dispositivo '$DEVICE_SERIAL' no encontrado.\n  Disponibles:\n$(echo "$DEVICES" | sed 's/^/    /')"
    log_info "Usando: $DEVICE_SERIAL (especificado con --device)"

elif [[ "$DEVICE_COUNT" -eq 1 ]]; then
    DEVICE_SERIAL=$(echo "$DEVICES" | head -1)
    log_info "Auto-seleccionado (único conectado): $DEVICE_SERIAL"

else
    # Múltiples dispositivos — mostrar menú
    echo -e "${BOLD}Dispositivos conectados:${NC}"
    i=1
    declare -a DEVICE_LIST
    while IFS= read -r dev; do
        MODEL=$(adb -s "$dev" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || echo "Desconocido")
        API=$(adb -s "$dev" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n' || echo "?")
        printf "  ${BOLD}[%d]${NC} %-25s %s (API %s)\n" "$i" "$dev" "$MODEL" "$API"
        DEVICE_LIST+=("$dev")
        i=$((i+1))
    done <<< "$DEVICES"

    echo ""
    printf "${BOLD}Selecciona [1-%d]: ${NC}" "$DEVICE_COUNT"
    read -r selection

    [[ "$selection" =~ ^[0-9]+$ ]] && \
    [[ "$selection" -ge 1 ]] && \
    [[ "$selection" -le "$DEVICE_COUNT" ]] || \
        log_error "Selección inválida: '$selection'"

    DEVICE_SERIAL="${DEVICE_LIST[$((selection-1))]}"
    log_info "Seleccionado: $DEVICE_SERIAL"
fi

# Mostrar info del dispositivo
MODEL=$(adb -s "$DEVICE_SERIAL" shell getprop ro.product.model    2>/dev/null | tr -d '\r\n' || echo "?")
BRAND=$(adb -s "$DEVICE_SERIAL" shell getprop ro.product.brand    2>/dev/null | tr -d '\r\n' || echo "?")
ANDROID=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r\n' || echo "?")
API=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk  2>/dev/null | tr -d '\r\n' || echo "?")
CHARS=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r\n' || echo "")
IS_TV=$(echo "$CHARS" | grep -c "tv" || true)
IS_FIRE=$(echo "$BRAND" | grep -ic "amazon" || true)

echo ""
printf "  %-12s %s %s\n" "Modelo:"   "$BRAND" "$MODEL"
printf "  %-12s Android %s (API %s)\n" "Sistema:"  "$ANDROID" "$API"

DETECTED_TYPE="mobile"
if   [[ "$IS_FIRE" -gt 0 ]]; then DETECTED_TYPE="firetv"
elif [[ "$IS_TV"   -gt 0 ]]; then DETECTED_TYPE="tv"
fi
printf "  %-12s %s\n" "Tipo:"     "$DETECTED_TYPE"

# Avisar si el --target no coincide con el dispositivo detectado
if [[ "$TARGET" != "all" && "$TARGET" != "$DETECTED_TYPE" ]]; then
    log_warn "--target=$TARGET pero el dispositivo parece ser '$DETECTED_TYPE'"
fi

# ─── Paso 2: Build ────────────────────────────────────────────────────────────
log_step "Build"

if [[ "$SKIP_BUILD" == true ]]; then
    log_warn "Saltando build (--skip-build)"
    [[ -f "$APK_APP"  ]] || log_error "APK no encontrado: $APK_APP — corre sin --skip-build primero"
    [[ -f "$APK_TEST" ]] || log_error "APK de test no encontrado: $APK_TEST"
else
    log_info "Ejecutando Gradle..."
    cd "$PROJECT_ROOT"
    "$GRADLEW" assembleDebug assembleAndroidTest --quiet 2>&1 | \
        grep -E "(BUILD|FAILURE|error:)" || true

    [[ -f "$APK_APP"  ]] || log_error "Build falló — APK no generado: $APK_APP"
    [[ -f "$APK_TEST" ]] || log_error "Build falló — APK de test no generado: $APK_TEST"
    log_ok "Build exitoso"
fi

# ─── Paso 3: Instalar ─────────────────────────────────────────────────────────
log_step "Instalación en $DEVICE_SERIAL"

log_info "Instalando app..."
adb -s "$DEVICE_SERIAL" install -r -t "$APK_APP" 2>&1 | grep -v "^$" | head -5
log_ok "App instalada"

log_info "Instalando APK de tests..."
adb -s "$DEVICE_SERIAL" install -r -t "$APK_TEST" 2>&1 | grep -v "^$" | head -5
log_ok "APK de tests instalado"

# ─── Paso 4: Lanzar app ───────────────────────────────────────────────────────
log_step "Lanzando app"
adb -s "$DEVICE_SERIAL" shell am start \
    -n "${APP_PACKAGE}/.MainActivity" \
    --activity-clear-top \
    > /dev/null 2>&1
log_ok "App abierta en el dispositivo — lista para inspección manual"
sleep 1

# ─── Paso 5: Construir argumentos del runner ──────────────────────────────────
log_step "Configuración de tests"

INSTRUMENT_ARGS=""

# Filtro por tipo de dispositivo
case "$TARGET" in
    tv)
        INSTRUMENT_ARGS+=" -e notAnnotation ${ANNOTATION_PKG}.MobileOnly,${ANNOTATION_PKG}.FireTvOnly"
        log_info "Target: Android TV  — excluye @MobileOnly y @FireTvOnly"
        ;;
    firetv)
        INSTRUMENT_ARGS+=" -e notAnnotation ${ANNOTATION_PKG}.MobileOnly,${ANNOTATION_PKG}.TvOnly"
        log_info "Target: Fire TV     — excluye @MobileOnly y @TvOnly"
        ;;
    mobile)
        INSTRUMENT_ARGS+=" -e notAnnotation ${ANNOTATION_PKG}.TvOnly,${ANNOTATION_PKG}.FireTvOnly"
        log_info "Target: Mobile      — excluye @TvOnly y @FireTvOnly"
        ;;
    all)
        log_info "Target: Todos los dispositivos — sin filtro de tipo"
        ;;
esac

# Filtro por tamaño (@MediumTest / @LargeTest)
case "$SIZE_FILTER" in
    medium)
        INSTRUMENT_ARGS+=" -e size medium"
        log_info "Tamaño: @MediumTest (CI — tests rápidos)"
        ;;
    large)
        INSTRUMENT_ARGS+=" -e size large"
        log_info "Tamaño: @LargeTest (release gate — tests lentos con seek)"
        ;;
    all)
        log_info "Tamaño: todos (@MediumTest + @LargeTest)"
        ;;
esac

# Smoke tests
if [[ "$SMOKE_ONLY" == true ]]; then
    INSTRUMENT_ARGS+=" -e package ${APP_PACKAGE}.smoke"
    log_info "Modo smoke: solo ${APP_PACKAGE}.smoke"
fi

# Paquete específico
if [[ -n "$TEST_PACKAGE" ]]; then
    INSTRUMENT_ARGS+=" -e package ${APP_PACKAGE}.${TEST_PACKAGE}"
    log_info "Paquete: ${APP_PACKAGE}.${TEST_PACKAGE}"
fi

# Clase específica
if [[ -n "$TEST_CLASS" ]]; then
    INSTRUMENT_ARGS+=" -e class ${APP_PACKAGE}.integration.${TEST_CLASS}"
    log_info "Clase: ${TEST_CLASS}"
fi

# ─── Paso 6: Grabar pantalla ──────────────────────────────────────────────────
log_step "Grabación de pantalla"

SCREENRECORD_PID=""
if [[ "$NO_REPORT" == false ]]; then
    # Borrar grabación anterior en el device
    adb -s "$DEVICE_SERIAL" shell rm -f /sdcard/sdk_qa_session.mp4 2>/dev/null || true

    log_info "Iniciando screenrecord en el dispositivo..."
    # screenrecord corre en el device; adb shell bloquea el host hasta que el proceso termine o muera
    adb -s "$DEVICE_SERIAL" shell screenrecord --time-limit 1800 /sdcard/sdk_qa_session.mp4 &
    SCREENRECORD_PID=$!
    sleep 1  # Dale tiempo al proceso de arrancar
    log_ok "Grabando (PID host: $SCREENRECORD_PID)"
fi

# ─── Paso 7: Ejecutar tests ───────────────────────────────────────────────────
log_step "Ejecutando tests"

echo -e ""
START_TS=$(date +%s)

# Mostrar output en tiempo real (corre en subshell — solo para display)
show_realtime_output() {
    local cur_test="" cur_class="" cur_num="" total=""
    while IFS= read -r line; do
        case "$line" in
            INSTRUMENTATION_STATUS:\ test=*)    cur_test="${line#*test=}" ;;
            INSTRUMENTATION_STATUS:\ class=*)   cur_class="${line#*class=}"; cur_class="${cur_class##*.}" ;;
            INSTRUMENTATION_STATUS:\ current=*) cur_num="${line#*current=}" ;;
            INSTRUMENTATION_STATUS:\ numtests=*) total="${line#*numtests=}" ;;
            INSTRUMENTATION_STATUS_CODE:\ 1)
                printf "  ${BLUE}[%s/%s]${NC} %s.%s " "$cur_num" "$total" "$cur_class" "$cur_test"
                ;;
            INSTRUMENTATION_STATUS_CODE:\ 0)   echo -e "${GREEN}✓${NC}" ;;
            INSTRUMENTATION_STATUS_CODE:\ -2)  echo -e "${RED}✗${NC}" ;;
            INSTRUMENTATION_STATUS_CODE:\ -3)  echo -e "${YELLOW}⊘ skip${NC}" ;;
        esac
    done
}

# Guardar output crudo + mostrar en tiempo real
set +e
adb -s "$DEVICE_SERIAL" shell am instrument -w \
    $INSTRUMENT_ARGS \
    "$TEST_RUNNER" 2>&1 | tee "$INSTRUMENT_RAW" | show_realtime_output
EXIT_CODE=${PIPESTATUS[0]}
set -e

END_TS=$(date +%s)
ELAPSED=$((END_TS - START_TS))
MINUTES=$((ELAPSED / 60))
SECONDS_REM=$((ELAPSED % 60))

# ─── Paso 8: Detener grabación ────────────────────────────────────────────────
if [[ -n "$SCREENRECORD_PID" ]]; then
    log_info "Deteniendo grabación..."
    # SIGINT hace que screenrecord finalice limpiamente y cierre el mp4
    adb -s "$DEVICE_SERIAL" shell pkill -2 screenrecord 2>/dev/null || true
    wait "$SCREENRECORD_PID" 2>/dev/null || true
    sleep 2  # Esperar a que el proceso del device vacíe el archivo
    log_ok "Grabación finalizada"
fi

# ─── Paso 9: Parsear resultados del raw file ──────────────────────────────────
# Esta función corre en el shell principal (no subshell) para poder llenar arrays
PASSED_TESTS=()
FAILED_TESTS=()
FAILED_REASONS=()

if [[ -f "$INSTRUMENT_RAW" ]]; then
    cur_test="" cur_class="" in_stack=false stack=""
    while IFS= read -r line; do
        case "$line" in
            INSTRUMENTATION_STATUS:\ test=*)
                cur_test="${line#*test=}"
                in_stack=false; stack=""
                ;;
            INSTRUMENTATION_STATUS:\ class=*)
                cur_class="${line#*class=}"
                ;;
            INSTRUMENTATION_STATUS_CODE:\ 0)
                PASSED_TESTS+=("${cur_class}.${cur_test}")
                ;;
            INSTRUMENTATION_STATUS_CODE:\ -2)
                FAILED_TESTS+=("${cur_class}.${cur_test}")
                FAILED_REASONS+=("${stack:-sin detalle}")
                ;;
            INSTRUMENTATION_STATUS:\ stack=*)
                in_stack=true
                stack="${line#*stack=}"
                ;;
            *)
                if $in_stack; then
                    if [[ "$line" == INSTRUMENTATION_STATUS* ]]; then
                        in_stack=false
                    elif [[ -n "$line" ]]; then
                        stack+=$'\n'"$line"
                    fi
                fi
                ;;
        esac
    done < "$INSTRUMENT_RAW"
fi

# ─── Paso 10: Resumen final ───────────────────────────────────────────────────
log_step "Resumen"

N_PASS=${#PASSED_TESTS[@]}
N_FAIL=${#FAILED_TESTS[@]}

echo ""
echo -e "  ${GREEN}✓ Pasaron:${NC} $N_PASS   ${RED}✗ Fallaron:${NC} $N_FAIL   ⏱ ${MINUTES}m${SECONDS_REM}s"
echo ""

# Detalle de fallos
if [[ $N_FAIL -gt 0 ]]; then
    echo -e "${RED}${BOLD}── Fallos ─────────────────────────────────────────${NC}"
    for i in "${!FAILED_TESTS[@]}"; do
        echo -e "  ${RED}✗${NC} ${BOLD}${FAILED_TESTS[$i]}${NC}"
        reason="${FAILED_REASONS[$i]}"
        if [[ -n "$reason" ]]; then
            echo -e "    ${YELLOW}↳${NC} $(echo "$reason" | head -1)"
        fi
        echo ""
    done

    # Pull de logcat filtrado a fallos del SDK
    echo -e "${BOLD}── Logcat SDK_QA_FAIL ─────────────────────────────${NC}"
    adb -s "$DEVICE_SERIAL" logcat -d -s SDK_QA_FAIL 2>/dev/null | \
        grep -v "^-" | tail -40 || echo "  (sin entradas en SDK_QA_FAIL)"
    echo ""
fi

# ─── Paso 11: Generar test-results.json ───────────────────────────────────────
if [[ "$NO_REPORT" == false ]]; then
    log_info "Generando test-results.json..."

    # Escribir listas a archivos temporales para evitar problemas con
    # caracteres especiales y saltos de línea en stack traces al pasar por args
    TMP_PASSED=$(mktemp)
    TMP_FAILED=$(mktemp)
    TMP_ERRORS=$(mktemp)

    printf '%s\n' "${PASSED_TESTS[@]+"${PASSED_TESTS[@]}"}" > "$TMP_PASSED"
    printf '%s\n' "${FAILED_TESTS[@]+"${FAILED_TESTS[@]}"}" > "$TMP_FAILED"

    # Errores: cada entrada separada por un marcador único (los stack traces tienen \n)
    for reason in "${FAILED_REASONS[@]+"${FAILED_REASONS[@]}"}"; do
        printf '%s\n---SDK_QA_ERR_SEP---\n' "$reason"
    done > "$TMP_ERRORS"

    python3 - "$RESULTS_JSON" "$N_PASS" "$N_FAIL" "${MINUTES}m${SECONDS_REM}s" \
              "$TMP_PASSED" "$TMP_FAILED" "$TMP_ERRORS" <<'PYEOF'
import json, sys

out_file  = sys.argv[1]
n_pass    = int(sys.argv[2])
n_fail    = int(sys.argv[3])
duration  = sys.argv[4]
f_passed  = sys.argv[5]
f_failed  = sys.argv[6]
f_errors  = sys.argv[7]

def read_lines(path):
    try:
        with open(path) as f:
            return [l.rstrip('\n') for l in f if l.strip()]
    except Exception:
        return []

def read_errors(path):
    try:
        with open(path) as f:
            content = f.read()
        parts = content.split('---SDK_QA_ERR_SEP---\n')
        return [p.strip() for p in parts if p.strip()]
    except Exception:
        return []

passed_list = read_lines(f_passed)
failed_list = read_lines(f_failed)
errors_list = read_errors(f_errors)

tests = []
for full in passed_list:
    parts = full.rsplit('.', 1)
    tests.append({"name": parts[-1], "class": parts[0] if len(parts) > 1 else "",
                  "status": "passed", "duration": ""})
for i, full in enumerate(failed_list):
    parts = full.rsplit('.', 1)
    err   = errors_list[i] if i < len(errors_list) else ""
    tests.append({"name": parts[-1], "class": parts[0] if len(parts) > 1 else "",
                  "status": "failed", "duration": "", "error": err})

result = {
    "tests": tests,
    "summary": {"total": n_pass + n_fail, "passed": n_pass,
                "failed": n_fail, "duration": duration}
}
with open(out_file, 'w') as f:
    json.dump(result, f, indent=2, ensure_ascii=False)
PYEOF

    rm -f "$TMP_PASSED" "$TMP_FAILED" "$TMP_ERRORS"
    log_ok "test-results.json generado: $RESULTS_JSON"
fi

# ─── Paso 12: Generar HTML report ────────────────────────────────────────────
if [[ "$NO_REPORT" == false ]]; then
    log_step "Generando report"
    bash "${SCRIPT_DIR}/report.sh" "$DEVICE_SERIAL" "$RESULTS_JSON" "$REPORT_DIR"
fi

# ─── Resultado final ──────────────────────────────────────────────────────────
echo ""
if [[ $N_FAIL -gt 0 ]]; then
    echo -e "${RED}${BOLD}╔══════════════════════════════════╗${NC}"
    echo -e "${RED}${BOLD}║   ✗  TESTS FALLARON  —  ${MINUTES}m${SECONDS_REM}s   ║${NC}"
    echo -e "${RED}${BOLD}╚══════════════════════════════════╝${NC}"
    echo ""
    echo -e "  Logcat completo:  adb -s $DEVICE_SERIAL logcat -d -s SDK_QA,SDK_QA_FAIL"
    echo -e "  Solo este test:   ./scripts/run-tests.sh --class ${FAILED_TESTS[0]##*.} --skip-build"
    exit 1
else
    echo -e "${GREEN}${BOLD}╔══════════════════════════════════╗${NC}"
    echo -e "${GREEN}${BOLD}║   ✓  TODOS PASARON  —  ${MINUTES}m${SECONDS_REM}s   ║${NC}"
    echo -e "${GREEN}${BOLD}╚══════════════════════════════════╝${NC}"
fi
