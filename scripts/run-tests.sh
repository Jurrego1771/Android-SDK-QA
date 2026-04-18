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
KNOWN_DEVICES_FILE="${SCRIPT_DIR}/.known-devices"

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
    if [[ -f "$KNOWN_DEVICES_FILE" ]]; then
        log_warn "Sin dispositivos — intentando reconectar desde ${KNOWN_DEVICES_FILE}..."
        while IFS= read -r known_ip; do
            [[ -z "$known_ip" || "$known_ip" == \#* ]] && continue
            log_info "adb connect $known_ip"
            adb connect "$known_ip" 2>&1 | grep -v "^$" || true
        done < "$KNOWN_DEVICES_FILE"
        sleep 2
        DEVICES=$(adb devices 2>/dev/null | tail -n +2 | grep "device$" | awk '{print $1}' || true)
        DEVICE_COUNT=$(echo "$DEVICES" | grep -c . 2>/dev/null || echo 0)
    fi
    if [[ -z "$DEVICES" || "$DEVICE_COUNT" -eq 0 ]]; then
        log_error "No hay dispositivos conectados.\n  Agrega IPs a ${KNOWN_DEVICES_FILE} para reconexión automática."
    fi
    log_ok "Reconexión exitosa"
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

# ─── Paso 5b: Limpiar procesos previos ───────────────────────────────────────
log_step "Limpieza de procesos previos"
log_info "Deteniendo cualquier instrumentación activa..."
adb -s "$DEVICE_SERIAL" shell am force-stop "${APP_PACKAGE}"      2>/dev/null || true
adb -s "$DEVICE_SERIAL" shell am force-stop "${APP_PACKAGE}.test" 2>/dev/null || true
sleep 1
log_ok "Procesos detenidos"

# ─── Paso 6: Grabar pantalla ──────────────────────────────────────────────────
log_step "Grabación de pantalla"

SCREENRECORD_PID=""
if [[ "$NO_REPORT" == false ]]; then
    # Borrar grabación anterior en el device
    adb -s "$DEVICE_SERIAL" shell rm -f /sdcard/sdk_qa_session.mp4 2>/dev/null || true

    log_info "Iniciando screenrecord en el dispositivo..."
    # Verificar que screenrecord esté disponible (algunos TV/devices no lo tienen)
    if adb -s "$DEVICE_SERIAL" shell command -v screenrecord &>/dev/null 2>&1; then
        adb -s "$DEVICE_SERIAL" shell screenrecord --time-limit 1800 /sdcard/sdk_qa_session.mp4 &
        SCREENRECORD_PID=$!
        sleep 1
        log_ok "Grabando (PID host: $SCREENRECORD_PID)"
    else
        log_warn "screenrecord no disponible en este dispositivo — omitiendo grabación"
    fi
fi

# ─── Paso 7: Ejecutar tests ───────────────────────────────────────────────────
log_step "Ejecutando tests"

echo -e ""
START_TS=$(date +%s)

# Mostrar output en tiempo real — parsea el formato brief de ADB:
#   com.example.sdk_qa.SomeTest:...E..   (. = pass, E = fail en la línea de clase)
#   Error in methodName(className):      (bloque de error siguiente)
#   .                                    (pass suelto después de un error)
show_realtime_output() {
    local cur_suite=""
    while IFS= read -r line; do
        # Línea de suite: "com.example.sdk_qa.foo.BarTest:...E."
        if [[ "$line" =~ ^com\.example\.sdk_qa\.(.+):(.*)$ ]]; then
            cur_suite="${BASH_REMATCH[1]##*.}"
            local trail="${BASH_REMATCH[2]}"
            local c
            for (( c=0; c<${#trail}; c++ )); do
                ch="${trail:$c:1}"
                if [[ "$ch" == "." ]]; then
                    echo -e "  ${GREEN}✓${NC}  ${cur_suite}"
                elif [[ "$ch" == "E" ]]; then
                    : # el nombre real llega en el bloque "Error in" siguiente
                fi
            done
        # Inicio de bloque de error
        elif [[ "$line" =~ ^Error\ in\ ([a-zA-Z_][a-zA-Z0-9_]*)\( ]]; then
            echo -e "  ${RED}✗${NC}  ${cur_suite}.${BASH_REMATCH[1]}"
        # Pass suelto después de un bloque de error
        elif [[ "$line" == "." ]]; then
            echo -e "  ${GREEN}✓${NC}  ${cur_suite}"
        fi
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
# ADB brief format: "com.example.pkg.Class:...E." luego bloques "Error in method()"
# Python parsea el raw y escribe un JSON temporal con los resultados estructurados.
PASSED_TESTS=()
FAILED_TESTS=()
FAILED_REASONS=()

TMP_PARSE=$(mktemp).json

python3 - "$INSTRUMENT_RAW" "$TMP_PARSE" <<'PYEOF'
import sys, re, json

raw_path = sys.argv[1]
out_path = sys.argv[2]

try:
    with open(raw_path, encoding='utf-8', errors='replace') as f:
        content = f.read()
except Exception:
    json.dump({"passed": [], "failed": []}, open(out_path, 'w'))
    sys.exit(0)

passed = []
failed = []
current_class = ""
current_suite = ""
in_error = False
error_entry = None
collecting_reason = False

for line in content.splitlines():
    # Suite header: "com.example.sdk_qa.foo.BarTest:...E."
    m = re.match(r'^(com\.example\.sdk_qa\.(\S+?)):(.*)$', line)
    if m:
        current_class = m.group(1)
        current_suite = m.group(2)
        trail = m.group(3)
        # Count dots as passes (we'll add proper names for errors from error blocks)
        for ch in trail:
            if ch == '.':
                passed.append({"class": current_class, "test": "unknown"})
        in_error = False
        error_entry = None
        continue

    # Error block start
    em = re.match(r'^Error in (\w+)\(', line)
    if em:
        # Remove the last "unknown" pass we may have counted for this E
        if passed and passed[-1]["test"] == "unknown":
            passed.pop()
        error_entry = {"class": current_class, "test": em.group(1), "reason": ""}
        failed.append(error_entry)
        in_error = True
        collecting_reason = False
        continue

    # Collect first meaningful reason line
    if in_error and error_entry and not error_entry["reason"]:
        s = line.strip()
        if s and not s.startswith("at ") and not s.startswith("expected") \
             and not s.startswith("Recibidos") and not s.startswith("Faltantes") \
             and not re.match(r'^\s*$', s):
            error_entry["reason"] = s[:200]

    # Lone dot = pass after an error block
    if line.strip() == ".":
        passed.append({"class": current_class, "test": "unknown"})
        in_error = False
        error_entry = None

json.dump({"passed": passed, "failed": failed}, open(out_path, 'w', encoding='utf-8'), ensure_ascii=False)
PYEOF

if [[ -f "$TMP_PARSE" ]]; then
    while IFS= read -r entry; do
        PASSED_TESTS+=("$entry")
    done < <(python3 -c "
import json, sys
d = json.load(open('$TMP_PARSE', encoding='utf-8'))
for t in d['passed']:
    print(t['class'] + '.' + t['test'])
" 2>/dev/null)

    while IFS=$'\t' read -r full reason; do
        FAILED_TESTS+=("$full")
        FAILED_REASONS+=("$reason")
    done < <(python3 -c "
import json, sys
d = json.load(open('$TMP_PARSE', encoding='utf-8'))
for t in d['failed']:
    print(t['class'] + '.' + t['test'] + '\t' + t['reason'].replace('\n',' '))
" 2>/dev/null)
fi
rm -f "$TMP_PARSE"

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

    python3 - "$INSTRUMENT_RAW" "$RESULTS_JSON" "${MINUTES}m${SECONDS_REM}s" <<'PYEOF'
import json, sys, re

raw_path  = sys.argv[1]
out_path  = sys.argv[2]
duration  = sys.argv[3]

try:
    with open(raw_path, encoding='utf-8', errors='replace') as f:
        content = f.read()
except Exception:
    content = ""

passed_tests = []
failed_tests = []
current_class = ""
in_error = False
error_entry = None

for line in content.splitlines():
    # Suite header: "com.example.sdk_qa.foo.BarTest:...E."
    m = re.match(r'^(com\.example\.sdk_qa\.(\S+?)):(.*)$', line)
    if m:
        current_class = m.group(1)
        trail = m.group(3)
        for ch in trail:
            if ch == '.':
                passed_tests.append({"name": "—", "class": current_class, "status": "passed", "duration": ""})
        in_error = False
        error_entry = None
        continue

    # Error block start
    em = re.match(r'^Error in (\w+)\(', line)
    if em:
        error_entry = {"name": em.group(1), "class": current_class,
                       "status": "failed", "duration": "", "error": ""}
        failed_tests.append(error_entry)
        in_error = True
        continue

    # Collect stack/reason — grab first non-boilerplate line
    if in_error and error_entry and not error_entry["error"]:
        s = line.strip()
        if s and not s.startswith("at ") and not s.startswith("expected") \
             and not s.startswith("Recibidos") and not s.startswith("Faltantes") \
             and not re.match(r'^\s*$', s):
            error_entry["error"] = s

    # Lone dot = pass after an error block
    if line.strip() == ".":
        passed_tests.append({"name": "—", "class": current_class, "status": "passed", "duration": ""})
        in_error = False
        error_entry = None

n_pass = len(passed_tests)
n_fail = len(failed_tests)

result = {
    "tests": failed_tests + passed_tests,
    "summary": {
        "total": n_pass + n_fail,
        "passed": n_pass,
        "failed": n_fail,
        "duration": duration
    }
}

with open(out_path, 'w', encoding='utf-8') as f:
    json.dump(result, f, indent=2, ensure_ascii=False)
PYEOF

    log_ok "test-results.json generado: $RESULTS_JSON"
fi

# ─── Paso 12: Generar HTML report ────────────────────────────────────────────
if [[ "$NO_REPORT" == false ]]; then
    log_step "Generando report"
    bash "${SCRIPT_DIR}/report.sh" "$DEVICE_SERIAL" "$RESULTS_JSON" "$REPORT_DIR"
fi

# ─── Paso 13: Notificación Slack ─────────────────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ -f "$ENV_FILE" ]]; then
    # Solo carga variables del .env que no estén ya definidas en el entorno
    # (los secrets de GitHub Actions tienen prioridad sobre el .env local)
    while IFS='=' read -r key value; do
        [[ "$key" =~ ^#.*$ || -z "$key" ]] && continue
        [[ -z "${!key:-}" ]] && export "$key"="$value"
    done < "$ENV_FILE"
fi

if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
    log_step "Notificación Slack"

    # Extraer versión del SDK desde build.gradle.kts
    SDK_VER=$(grep -oP 'mediastreamplatformsdkandroid:\K[^"]+' \
              "${PROJECT_ROOT}/app/build.gradle.kts" 2>/dev/null || echo "desconocida")

    DEVICE_LABEL="${BRAND:-} ${MODEL:-} (Android ${ANDROID:-?} · API ${API:-?})"

    bash "${SCRIPT_DIR}/notify-slack.sh" \
        "$RESULTS_JSON" \
        "$DEVICE_LABEL" \
        "" \
        "$SDK_VER"
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
