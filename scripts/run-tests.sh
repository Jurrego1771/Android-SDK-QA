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

# Devuelve seriales de dispositivos actualmente autorizados en adb
get_connected_devices() {
    adb devices 2>/dev/null | tail -n +2 | grep "device$" | awk '{print $1}' || true
}

# Ping a IP (extrae host de "host:port"). 0 = alcanzable, 1 = sin respuesta.
ping_host() {
    local host="${1%%:*}"
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
        ping -n 1 -w 1000 "$host" &>/dev/null
    else
        ping -c 1 -W 1 "$host" &>/dev/null
    fi
}

# Intenta adb connect y verifica que el serial aparezca en "adb devices".
# Imprime el serial conectado en stdout si tiene éxito. Retorna 0/1.
try_adb_connect() {
    local entry="$1"
    adb connect "$entry" 2>&1 | grep -v "^$" >&2 || true
    sleep 1
    local connected
    connected=$(get_connected_devices)
    if echo "$connected" | grep -qF "$entry"; then
        echo "$entry"
        return 0
    fi
    return 1
}

DEVICES=$(get_connected_devices)
DEVICE_COUNT=0
[[ -n "$DEVICES" ]] && DEVICE_COUNT=$(echo "$DEVICES" | wc -l | tr -d ' ')

# ── Si --device especificado y no está conectado, intentar conectar primero ──
if [[ -n "$DEVICE_SERIAL" ]] && ! echo "$DEVICES" | grep -qF "$DEVICE_SERIAL"; then
    log_warn "--device $DEVICE_SERIAL no conectado — intentando adb connect..."
    if try_adb_connect "$DEVICE_SERIAL" >/dev/null; then
        log_ok "Conectado a $DEVICE_SERIAL"
        DEVICES=$(get_connected_devices)
        DEVICE_COUNT=$(echo "$DEVICES" | wc -l | tr -d ' ')
    else
        log_error "No se pudo conectar a '$DEVICE_SERIAL'."
    fi
fi

# ── Si no hay ningún dispositivo, intentar desde .known-devices con ping ──────
if [[ -z "$DEVICES" || "$DEVICE_COUNT" -eq 0 ]]; then
    if [[ ! -f "$KNOWN_DEVICES_FILE" ]]; then
        log_error "Sin dispositivos conectados.\n  Crea ${KNOWN_DEVICES_FILE} con una IP:puerto por línea."
    fi

    # Leer lista (ignorar comentarios y blancos)
    KNOWN_IPS=()
    while IFS= read -r line; do
        [[ -z "$line" || "$line" == \#* ]] && continue
        KNOWN_IPS+=("$line")
    done < "$KNOWN_DEVICES_FILE"

    [[ ${#KNOWN_IPS[@]} -eq 0 ]] && \
        log_error "Sin dispositivos conectados y ${KNOWN_DEVICES_FILE} está vacío."

    log_warn "Sin dispositivos — ping a ${#KNOWN_IPS[@]} conocidos..."

    # Ping en paralelo — resultados en archivos temporales
    _PING_TMP=$(mktemp -d)
    for _entry in "${KNOWN_IPS[@]}"; do
        (
            if ping_host "$_entry"; then
                echo ok > "${_PING_TMP}/${_entry//[.:\/ ]/_}"
            else
                echo fail > "${_PING_TMP}/${_entry//[.:\/ ]/_}"
            fi
        ) &
    done
    wait  # todos los pings terminan aquí

    REACHABLE=()
    for _entry in "${KNOWN_IPS[@]}"; do
        _key="${_entry//[.:\/ ]/_}"
        _res=$(cat "${_PING_TMP}/${_key}" 2>/dev/null || echo fail)
        if [[ "$_res" == "ok" ]]; then
            REACHABLE+=("$_entry")
            log_ok  "  PING OK   $_entry"
        else
            log_warn "  sin resp  $_entry"
        fi
    done
    rm -rf "$_PING_TMP"

    if [[ ${#REACHABLE[@]} -eq 0 ]]; then
        log_error "Ningún dispositivo responde al ping.\n  IPs probadas: ${KNOWN_IPS[*]}"
    fi

    log_info "${#REACHABLE[@]} accesible(s) — intentando adb connect en orden..."

    for _entry in "${REACHABLE[@]}"; do
        log_info "Conectando a $_entry..."
        if try_adb_connect "$_entry" >/dev/null; then
            log_ok "Conectado a $_entry"
            DEVICES=$(get_connected_devices)
            DEVICE_COUNT=$(echo "$DEVICES" | wc -l | tr -d ' ')
            break
        fi
        log_warn "adb connect $_entry — no apareció en adb devices, probando siguiente..."
    done

    if [[ -z "$DEVICES" || "$DEVICE_COUNT" -eq 0 ]]; then
        log_error "No fue posible conectar a ningún dispositivo.\n  Accesibles por ping: ${REACHABLE[*]}"
    fi
    log_ok "Reconexión exitosa"
fi

# ── Selección final de serial ─────────────────────────────────────────────────
if [[ -n "$DEVICE_SERIAL" ]]; then
    echo "$DEVICES" | grep -qF "$DEVICE_SERIAL" || \
        log_error "Dispositivo '$DEVICE_SERIAL' no encontrado.\n  Disponibles:\n$(echo "$DEVICES" | sed 's/^/    /')"
    log_info "Usando: $DEVICE_SERIAL (especificado con --device)"

elif [[ "$DEVICE_COUNT" -eq 1 ]]; then
    DEVICE_SERIAL=$(echo "$DEVICES" | head -1)
    log_info "Auto-seleccionado (único conectado): $DEVICE_SERIAL"

else
    # Múltiples dispositivos — en CI auto-seleccionar el primero, localmente mostrar menú
    declare -a DEVICE_LIST
    while IFS= read -r dev; do
        DEVICE_LIST+=("$dev")
    done <<< "$DEVICES"

    if [[ -n "${CI:-}" ]]; then
        DEVICE_SERIAL="${DEVICE_LIST[0]}"
        log_info "CI: múltiples dispositivos — auto-seleccionado el primero: $DEVICE_SERIAL"
    else
        echo -e "${BOLD}Dispositivos conectados:${NC}"
        i=1
        for dev in "${DEVICE_LIST[@]}"; do
            MODEL=$(adb -s "$dev" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || echo "Desconocido")
            API=$(adb -s "$dev" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n' || echo "?")
            printf "  ${BOLD}[%d]${NC} %-25s %s (API %s)\n" "$i" "$dev" "$MODEL" "$API"
            i=$((i+1))
        done
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
fi

# Mostrar info del dispositivo — una sola conexión adb para todas las props
_PROPS=$(adb -s "$DEVICE_SERIAL" shell "getprop ro.product.model && getprop ro.product.brand && getprop ro.build.version.release && getprop ro.build.version.sdk && getprop ro.build.characteristics" 2>/dev/null | tr -d '\r')
MODEL=$(  sed -n '1p' <<< "$_PROPS")
BRAND=$(  sed -n '2p' <<< "$_PROPS")
ANDROID=$(sed -n '3p' <<< "$_PROPS")
API=$(    sed -n '4p' <<< "$_PROPS")
CHARS=$(  sed -n '5p' <<< "$_PROPS")
MODEL=${MODEL:-?}; BRAND=${BRAND:-?}; ANDROID=${ANDROID:-?}; API=${API:-?}; CHARS=${CHARS:-}
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

# ─── Paso 1b: Keep-awake (Fire TV / Fire Stick) ───────────────────────────────
# Fire Stick entra en reposo y pierde la conexión ADB durante tests largos.
# Se deshabilita el timeout de pantalla y el screensaver antes de cualquier test.
if [[ "$DETECTED_TYPE" == "firetv" ]]; then
    log_step "Keep-awake Fire TV"
    _adb() { adb -s "$DEVICE_SERIAL" shell "$@" 2>/dev/null || true; }

    # Pantalla siempre encendida mientras haya alimentación (AC=1, USB=2, AC+USB=3)
    _adb settings put global stay_on_while_plugged_in 3
    # Timeout de pantalla al máximo (Integer.MAX_VALUE ms ≈ 24 días)
    _adb settings put system screen_off_timeout 2147483647
    # Screensaver/Daydream deshabilitado
    _adb settings put secure screensaver_enabled 0
    # Apagar la función "display sleep" de Fire OS (si existe en esta versión)
    _adb settings put system screen_brightness_mode 0
    # Evitar que el launcher entre en modo bajo consumo
    _adb settings put global low_power 0
    # Despertar pantalla ahora por si ya estaba apagada
    _adb input keyevent KEYCODE_WAKEUP

    log_ok "Keep-awake aplicado — Fire Stick no entrará en reposo durante la sesión"
    unset -f _adb
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
_INSTALL_OUT=$(adb -s "$DEVICE_SERIAL" install -r -t "$APK_APP" 2>&1)
if echo "$_INSTALL_OUT" | grep -qi "FAILED\|error"; then
    # INSTALL_FAILED_UPDATE_INCOMPATIBLE: desinstala y reintenta
    if echo "$_INSTALL_OUT" | grep -qi "UPDATE_INCOMPATIBLE\|SIGNATURES_DO_NOT_MATCH"; then
        log_warn "Firma incompatible — desinstalando versión previa..."
        adb -s "$DEVICE_SERIAL" uninstall "com.example.sdk_qa" 2>/dev/null || true
        adb -s "$DEVICE_SERIAL" install -r -t "$APK_APP" 2>&1 | grep -v "^$" \
            || log_error "Install falló tras desinstalar. Output:\n$_INSTALL_OUT"
    else
        log_error "adb install app falló:\n$_INSTALL_OUT"
    fi
fi
log_ok "App instalada"

log_info "Instalando APK de tests..."
_INSTALL_OUT=$(adb -s "$DEVICE_SERIAL" install -r -t "$APK_TEST" 2>&1)
if echo "$_INSTALL_OUT" | grep -qi "FAILED\|error"; then
    if echo "$_INSTALL_OUT" | grep -qi "UPDATE_INCOMPATIBLE\|SIGNATURES_DO_NOT_MATCH"; then
        log_warn "Firma incompatible — desinstalando APK de tests previo..."
        adb -s "$DEVICE_SERIAL" uninstall "com.example.sdk_qa.test" 2>/dev/null || true
        adb -s "$DEVICE_SERIAL" install -r -t "$APK_TEST" 2>&1 | grep -v "^$" \
            || log_error "Install test APK falló tras desinstalar. Output:\n$_INSTALL_OUT"
    else
        log_error "adb install test APK falló:\n$_INSTALL_OUT"
    fi
fi
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
        # Auto-apply annotation filter based on detected device type
        case "$DETECTED_TYPE" in
            tv)
                INSTRUMENT_ARGS+=" -e notAnnotation ${ANNOTATION_PKG}.MobileOnly,${ANNOTATION_PKG}.FireTvOnly"
                log_info "Target: all (TV detectado) — excluye @MobileOnly y @FireTvOnly"
                ;;
            firetv)
                INSTRUMENT_ARGS+=" -e notAnnotation ${ANNOTATION_PKG}.MobileOnly,${ANNOTATION_PKG}.TvOnly"
                log_info "Target: all (FireTV detectado) — excluye @MobileOnly y @TvOnly"
                ;;
            mobile)
                INSTRUMENT_ARGS+=" -e notAnnotation ${ANNOTATION_PKG}.TvOnly,${ANNOTATION_PKG}.FireTvOnly"
                log_info "Target: all (Mobile detectado) — excluye @TvOnly y @FireTvOnly"
                ;;
            *)
                log_info "Target: Todos los dispositivos — sin filtro de tipo"
                ;;
        esac
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

# ─── Paso 6: Grabación de pantalla ───────────────────────────────────────────
# Deshabilitada temporalmente — scrcpy requiere configuración adicional en CI
SCREENRECORD_PID=""
VIDEO_PATH=""

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

# ─── Paso 8: (grabación deshabilitada) ───────────────────────────────────────

# ─── Paso 9: Parsear resultados del raw file ──────────────────────────────────
# Node.js imprime comandos bash que eval pobla PASSED_TESTS / FAILED_TESTS.
PASSED_TESTS=()
FAILED_TESTS=()
FAILED_REASONS=()

eval "$(node - "$INSTRUMENT_RAW" <<'JSEOF'
const fs = require('fs');
const rawPath = process.argv[2];
let content = '';
try { content = fs.readFileSync(rawPath, 'utf8'); } catch(_) { process.exit(0); }

const passed = [], failed = [];
let currentClass = '', inError = false, errorEntry = null;

for (const line of content.split(/\r?\n/)) {
    const suiteM = line.match(/^(com\.example\.sdk_qa\.\S+?):(.*)/);
    if (suiteM) {
        currentClass = suiteM[1];
        for (const ch of suiteM[2]) {
            if (ch === '.') passed.push({ cls: currentClass, test: 'unknown' });
        }
        inError = false; errorEntry = null;
        continue;
    }
    const errM = line.match(/^Error in (\w+)\(/);
    if (errM) {
        if (passed.length && passed[passed.length-1].test === 'unknown') passed.pop();
        errorEntry = { cls: currentClass, test: errM[1], reason: '' };
        failed.push(errorEntry);
        inError = true;
        continue;
    }
    if (inError && errorEntry && !errorEntry.reason) {
        const s = line.trim();
        if (s && !s.startsWith('at ') && s !== '') errorEntry.reason = s.slice(0, 200);
    }
    if (line.trim() === '.') {
        passed.push({ cls: currentClass, test: 'unknown' });
        inError = false; errorEntry = null;
    }
}

const q = s => "'" + s.replace(/'/g, "'\\''") + "'";
for (const t of passed)  process.stdout.write('PASSED_TESTS+=(' + q(t.cls + '.' + t.test) + ')\n');
for (const t of failed) {
    process.stdout.write('FAILED_TESTS+=(' + q(t.cls + '.' + t.test) + ')\n');
    process.stdout.write('FAILED_REASONS+=(' + q(t.reason) + ')\n');
}
JSEOF
)"

# ─── Paso 9b: Screenshot via adb si hubo fallos (fallback al device-side) ────
# SdkEvidenceRule intenta takeScreenshot() en el test; en TV puede retornar null.
if [[ ${#FAILED_TESTS[@]} -gt 0 && "$NO_REPORT" == false ]]; then
    SCREENSHOT_DIR="${REPORT_DIR}/screenshots"
    mkdir -p "$SCREENSHOT_DIR"
    log_info "Capturando screenshot del device..."
    adb -s "$DEVICE_SERIAL" exec-out screencap -p \
        > "${SCREENSHOT_DIR}/device_on_failure.png" 2>/dev/null \
        && log_ok "Screenshot capturado" \
        || log_warn "No se pudo capturar screenshot vía adb screencap"
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

    node - "$INSTRUMENT_RAW" "$RESULTS_JSON" "${MINUTES}m${SECONDS_REM}s" <<'JSEOF'
const fs = require('fs');
const [rawPath, outPath, duration] = process.argv.slice(2);
let content = '';
try { content = fs.readFileSync(rawPath, 'utf8'); } catch(_) {}

const passedTests = [], failedTests = [];
let currentClass = '', inError = false, errorEntry = null;

for (const line of content.split(/\r?\n/)) {
    const suiteM = line.match(/^(com\.example\.sdk_qa\.\S+?):(.*)/);
    if (suiteM) {
        currentClass = suiteM[1];
        for (const ch of suiteM[2]) {
            if (ch === '.') passedTests.push({ name: '—', class: currentClass, status: 'passed', duration: '' });
        }
        inError = false; errorEntry = null;
        continue;
    }
    const errM = line.match(/^Error in (\w+)\(/);
    if (errM) {
        errorEntry = { name: errM[1], class: currentClass, status: 'failed', duration: '', error: '' };
        failedTests.push(errorEntry);
        inError = true;
        continue;
    }
    if (inError && errorEntry && !errorEntry.error) {
        const s = line.trim();
        if (s && !s.startsWith('at ') && !s.startsWith('expected') &&
            !s.startsWith('Recibidos') && !s.startsWith('Faltantes'))
            errorEntry.error = s;
    }
    if (line.trim() === '.') {
        passedTests.push({ name: '—', class: currentClass, status: 'passed', duration: '' });
        inError = false; errorEntry = null;
    }
}

const result = {
    tests: [...failedTests, ...passedTests],
    summary: { total: passedTests.length + failedTests.length,
               passed: passedTests.length, failed: failedTests.length, duration }
};
fs.writeFileSync(outPath, JSON.stringify(result, null, 2), 'utf8');
JSEOF

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
    SDK_VER=$(node - "${PROJECT_ROOT}/app/build.gradle.kts" <<'JSVER'
const fs = require('fs');
try {
    const t = fs.readFileSync(process.argv[2], 'utf8');
    const m = t.match(/mediastreamplatformsdkandroid:([^"]+)/);
    console.log(m ? m[1] : 'desconocida');
} catch(_) { console.log('desconocida'); }
JSVER
)

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
elif [[ "$EXIT_CODE" -ne 0 && $N_PASS -eq 0 ]]; then
    # adb instrument falló antes de correr cualquier test (crash, desconexión, clase no encontrada)
    echo -e "${RED}${BOLD}╔══════════════════════════════════╗${NC}"
    echo -e "${RED}${BOLD}║   ✗  RUNNER FALLÓ — 0 TESTS      ║${NC}"
    echo -e "${RED}${BOLD}╚══════════════════════════════════╝${NC}"
    echo ""
    echo -e "  adb instrument salió con código $EXIT_CODE y no ejecutó ningún test."
    echo -e "  Raw output: $INSTRUMENT_RAW"
    echo -e "  Logcat: adb -s $DEVICE_SERIAL logcat -d -s AndroidRuntime,TestRunner"
    exit 1
else
    echo -e "${GREEN}${BOLD}╔══════════════════════════════════╗${NC}"
    echo -e "${GREEN}${BOLD}║   ✓  TODOS PASARON  —  ${MINUTES}m${SECONDS_REM}s   ║${NC}"
    echo -e "${GREEN}${BOLD}╚══════════════════════════════════╝${NC}"
fi
