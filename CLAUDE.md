# CLAUDE.md — SDK-Android-Qa

Proyecto de **QA caja-negra** del SDK de Mediastream para Android
(`io.github.mediastream:mediastreamplatformsdkandroid`). Una app de escenarios (manual +
instrumentado) que ejercita el SDK como dependencia externa. No es el repo del SDK: los unit
tests de internals viven en `MediastreamPlatformSDKAndroid`.

- SDK bajo test: **10.0.7** (lo que reporta `getVersion()` en device; el binario Maven puede
  decir `10.0.8-alpha01` — discrepancia conocida, ver `qa-knowledge/core-player` CORE-LEARN-004).
- Una sola app / un APK para móvil y TV; el SDK es adaptativo en runtime. Diferenciación por
  anotaciones `@MobileOnly`/`@TvOnly` + filtro `--target` en `scripts/run-tests.sh`.
- Devices: **el harness auto-detecta el device USB conectado** (`adb devices`), no depende de un
  serial fijo — cualquier teléfono enchufado sirve, sin registrar nada. Móvil actual: **Motorola
  Edge 60 Fusion** `ZY22MLTTD3` (Android 15 / API 35). Anterior: Samsung A53 `R5CTB1W92KY` (API 36;
  varios `learnings` del SESSION_LOG son de API 36 — p.ej. el PiP automático al salir). TV: Sony
  BRAVIA `192.168.1.224:5555` (WiFi — IP por DHCP, puede cambiar; `BRAVIA_VU31`, requiere aceptar
  la autorización ADB en pantalla al conectar).
  > Nota: los `qa-knowledge/session-baselines` se capturaron en el A53 (`sdk10.0.7`). Al comparar
  > sesiones de otro device, lo **estructural** (orden de callbacks, formato) diffea limpio; los
  > **numéricos** (TTFF, rebuffer) varían por device además de por red — recapturar baseline si se
  > fija un nuevo device de referencia.

---

## ⚡ Navegar/observar el device — usa el harness, NO taps ciegos

Antes de manejar el device con `adb input tap` a ciegas (frágil: `uiautomator dump` se cuelga con
"could not get idle state" mientras hay video), usa lo que **ya existe y está validado**:

### 1. Deep links — lanzar cualquier escenario de forma determinista
Las Activities de escenario son `exported=false` (no se lanzan directo por adb). El router
`DeepLinkRouterActivity` (debug-only) sí:

```bash
adb shell am start -W -a android.intent.action.VIEW -d "sdkqa://scenario/vod"
```

Keys: `vod live livedvr lived episode episode-custom ads drm pip reels switcher subtitles
fullscreen service audio-live audio-vod audio-episode audio-service direct-hls`
(fuente de verdad: `app/src/debug/.../debug/DeepLinkRouterActivity.kt`).

### 2. MCP exploratorio — navegación + observación (validado, Fases 1–2)
`tools/exploratory-mcp` es un servidor MCP custom (stdio, Node) que envuelve adb + un bridge
debug. **Validado end-to-end** (test-client contra A53). Tools:

| Tool | Qué hace |
|---|---|
| `navigate_deeplink(key)` | Lanza escenario vía `sdkqa://scenario/<key>` |
| `tap(x, y)` | Tap por coordenadas |
| `observe_screenshot()` | PNG de la pantalla |
| `observe_ui_hierarchy()` | Dump de vistas con `resource-id` + `bounds` (deriva coords de un selector) |
| `observe_logcat(tags?, maxLines?)` | Slice de logcat por tags del SDK QA |
| `observe_session_state()` | **Estado interno del player** (callbacks en orden, offMainThread, position/duration, config) vía bridge — algo que Maestro/Appium NO pueden |
| `observe_crashes()` · `observe_network()` · `observe_analytics()` | Crashes, tráfico (mitm), beacons |

**Probar standalone:** `cd tools/exploratory-mcp && ANDROID_SERIAL=<serial> node test-client.mjs`

**Conectar a Claude Code** (para usar las tools desde la conversación): crear `.mcp.json` en la
raíz:
```json
{ "mcpServers": { "sdk-qa-exploratory": {
    "command": "node", "args": ["tools/exploratory-mcp/src/server.mjs"], "env": {} } } }
```
Reiniciar/reconectar y aprobar el server. Con varios devices, fijar `"env": {"ANDROID_SERIAL": "..."}`.

> Decisión de herramientas: **ni Maestro ni Appium** por ahora — el harness adb+bridge cubre el
> 90%. Si el tap-por-coordenada-derivada resultara frágil, meter **Maestro** (no Appium) para la
> Fase 3. Docs: `docs/testing/exploratory-ai.md` y `-architecture.md`.

---

## Comandos

```bash
# Build + instalar en device
./gradlew :app:installDebug

# Suite por device/size (preflight gate incluido)
./scripts/run-tests.sh --target mobile --size medium

# Una clase, directo (sin Orchestrator) — útil para iterar
adb shell am instrument -w -e class com.example.sdk_qa.integration.<Clase> \
  com.example.sdk_qa.test/androidx.test.runner.AndroidJUnitRunner

# Gates QoE (@LargeTest, no entran en CI size medium) y baseline on-demand
#   PlaybackQoeTest      — gates con warm-up + TTFF por mediana
#   QoeBaselineTest      — medición; valores a logcat -s QOE_BASELINE
```

`@LargeTest` no corre en la suite normal (`--size medium`); se invoca por clase.

---

## Observabilidad in-app (Debug Panel)

`BaseScenarioActivity` tiene un panel (FAB 🐛 → bottom sheet) común a las 18 Activities:
- **HUD QoE** color-coded por umbral de industria (`PlaybackMetrics` + `PlaybackMetrics.Health`):
  TTFF, buffer, bitrate/switches, rebuffer ratio, dropped frames, errores de carga silenciosos.
- **Señales del SDK en el HUD vivo**: `getCurrentVideoPlayingFormat` (HLS/DASH), `getCurrentUrl`
  (CDN/manifest, acortado a `host/…/archivo`), `getPBId/getSId/getUId` (session IDs).
- **Log de callbacks** con **delta inter-evento** (`+123ms` / `+1.4s`, clock monotónico) y
  **filtro por categoría** (chips multi-selección; ninguno = todo).
- Botón **"Copiar"** → exporta un snapshot markdown (device, SDK, CDN URL, session IDs
  pbId/sId/uId, métricas, timeline de callbacks) al portapapeles y a logcat (`SDK_QA`).
- **En TV (D-pad)**: el FAB no se puede tocar → **tecla MENU** (botón Opciones del remoto)
  abre/cierra el panel; con el panel abierto el D-pad navega el overlay (no se reenvía al
  player) y **BACK** lo cierra sin salir del escenario. Verificado en BRAVIA.
- `playbackMetrics` y `callbackCaptor` están expuestos para tests instrumentados.
- **Session export (Capa C, debug-only)**: al cerrar un escenario, `SessionExporter`
  (`app/src/debug/.../debug/`) escribe un JSON normalizado a
  `getExternalFilesDir("sessions")/<scenario>-sdk<version>-<ts>.json`, para **diff entre
  versiones del SDK**. **Timeline unificado** (schema v2): fusiona callbacks del SDK
  (`CallbackCaptor`) con eventos discretos de ExoPlayer (`SessionRecorder`: `video_format`/bitrate,
  `buffering_start/end`, `dropped_frames`, `load_error`+httpStatus, `player_error`, `first_frame`),
  ordenados por reloj monotónico compartido. `SessionRecorder` es un `AnalyticsListener` separado,
  read-only, todo en runCatching (no afecta reproducción ni tests; verificado con
  `CallbackOrderTest`). Normalizado (offsets relativos, thread main/background, claves ordenadas,
  sin session IDs ni CDN URL). Recuperar:
  `adb pull /sdcard/Android/data/com.example.sdk_qa/files/sessions`.
  > Nota: los campos numéricos (ttffMs, rebufferMs, offsetMs) varían por red incluso en la misma
  > versión. Para comparar **timing** captura N sesiones/versión y compara medianas; lo que
  > diffea limpio session-a-session es lo **estructural**: orden de callbacks, offMainThread,
  > format, resolución, loadErrorCount.

---

## QA agéntico — proceso y entradas

El QA lo ejecuta una cadena de **8 agentes** (`.claude/commands/*`, `.claude/agents/*`) que siguen el
proceso profesional risk-based + RTS + exploratory-first. **Fuente de verdad: `docs/agents.md`** (schema
uniforme, set de agentes, contrato de archivos `ai-output/*`, las 6 etapas). NO dupliques esa info acá.

- **Núcleo compartido:** `scripts/qa-core.sh` (bump → compile-gate → change-analyzer → test-strategist →
  [activity-creator] → explorer → test-generator → run 2 fases + retry → test-analyzer → version-comparator → PR).
- **3 adaptadores de entrada** que normalizan a `ai-output/source.md`+`source-meta.txt` y delegan al núcleo:
  - `scripts/watch-sdk.sh` — versión nueva del SDK (cron / `workflow_dispatch sdk_branch=…`).
  - `scripts/run-changelog-pipeline.sh` — changelog versionado en el repo (push a `sdk-changelog/`).
  - `scripts/ingest-issue.sh "<descripción>" | <#issue>` — **shift-left por issue** (prueba la versión actual).
- **Blast radius:** `qa-knowledge/affected-files.json` (archivo SDK → features), regenerado por
  `build-knowledge-index.cjs`. **Grafo de conocimiento:** `qa-knowledge/` (ver `CONVENTIONS.md`, schema de 4).
- El agente headless tiene permiso de gradle (`.claude/settings.json`) para auto-verificar sus fixes.
- Nada se mergea solo: el proceso abre **PR** (resultados) e **issues** (bugs/mejoras de la exploración).

## Convenciones

- Bitácora de trabajo: `docs/testing/SESSION_LOG.md` (qué/por qué/siguiente paso, se actualiza
  al cierre de cada sesión).
- Conocimiento por-feature: `qa-knowledge/<slug>/` — schema de 4 (`rules.md` + `risks.yaml` +
  `defects.yaml` + `tests.yaml`). Ver `qa-knowledge/CONVENTIONS.md`.
- Mapa de riesgos/cobertura: `risk-map/RISK_MAP.md`.
- Commits: convencionales (`feat(...)`, `test(...)`, `fix(...)`, `docs(...)`).
