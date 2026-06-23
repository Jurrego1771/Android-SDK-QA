# Bitácora de sesiones — Observabilidad y exploración del SDK

> Registro vivo de cada sesión de trabajo: **qué** se hizo, **por qué**, y el **siguiente paso**.
> Entradas en orden cronológico inverso (la más reciente arriba). Se actualiza al cierre de cada sesión.

---

## 2026-06-23 (cont.) — Capa C: Session Export a JSON normalizado para diff entre versiones

**Objetivo**
Roadmap de 3 capas: A (Debug Panel) ✅ hecha y ampliada; B (Maestro) ⏸️ pospuesta por decisión.
Falta C: al cerrar un escenario, volcar un JSON con el timeline para **diff 10.0.7 vs 10.0.8**.

**Decisiones de diseño (preguntadas al usuario)**
- Normalizar para diff (no crudo): el diff debe resaltar comportamiento, no ruido de sesión.
- Persistencia: `getExternalFilesDir("sessions")`, 1 archivo/sesión, debug-only.

**Qué se hizo** (`1fbc827`)
- `SessionExporter` (src/debug) — construye JSON normalizado y lo escribe en onDestroy. Vive 100%
  en debug: reusa los hooks de `ObservabilityProvider` (captureLive en onPause antes del
  releasePlayer → version+format; export en onDestroy). Cero cambios de comportamiento en main.
- `CallbackCaptor` gana `timelineSnapshot()` (TimedEvent: nombre + elapsedRealtime + onMainThread)
  sin romper su API — antes solo guardaba nombres.
- Normalización: offsets relativos al primer evento, thread→main/background, claves ordenadas
  alfabéticamente (serializador propio), redactado session IDs + CDN URL + BW/bitrate instantáneos.

**Verificado** (A53, VOD): `Video-VOD-sdk10.0.7-<ts>.json` — 8 callbacks con offsets, métricas de
comportamiento, format hls, sdk 10.0.7. Capturó un rebuffer mid-playback (onBuffering→onPause→
onReady a ~11s). Schema `sdkqa.session.v1`.

**Nota sobre el diff**
Lo que diffea limpio session-a-session es lo **estructural** (orden callbacks, offMainThread,
format, resolución, loadErrorCount). Los numéricos (ttffMs/rebufferMs/offsetMs) varían por red aun
en la misma versión → para comparar timing, capturar N sesiones/versión y comparar medianas.

**Siguiente paso**
- Cuando haya binario 10.0.8 instalable: capturar sesiones de los mismos escenarios y diffear los
  JSON (jq/diff). Considerar un script `scripts/session-diff.*` que ignore campos numéricos volátiles.
- Roadmap overlay: queda **#5** (rebuffer ratio con ventana móvil).

---

## 2026-06-23 (cont.) — Mejoras al Debug Overlay #3/#4/#6/#7 (harness MCP + BRAVIA)

**Objetivo**
Cerrar brechas del overlay usando el harness MCP recién validado (navigate_deeplink + tap +
observe_*), sin adb manual. Cambios directos a `main`.

**Qué se hizo (todo verificado en device)**
- **#3 Señales del SDK en HUD vivo** (`f3e13ac`) — `getCurrentVideoPlayingFormat` (HLS/DASH),
  `getCurrentUrl` (CDN/manifest, acortado a `host/…/archivo`), `getPBId/getSId/getUId` (session
  IDs). Ya iban en el export; ahora también en el HUD. Cada getter en `runCatching` → "—" si falta.
  Verificado en A53 vía MCP.
- **#4 Deltas inter-callback en el log** (`4e791db`) — cada entrada muestra ms desde el evento
  anterior (`+123ms` / `+1.4s`), clock monotónico (`elapsedRealtime`) capturado en `log()`, no en
  `bind()`. Reset al "Limpiar". Verificado: onBuffering→onReady +1.6s, onReady→onPlay +3ms.
- **#6 Filtro de log por categoría** (`5809b37`) — fila de filter-chips Material (multi-selección),
  uno por `LogCategory`, coloreado con su color. Ninguno = todo. Adapter separa lista completa de
  la visible (`rebuildVisible` + notifyDataSetChanged; lista ≤100, animator off). Verificado.
- **#7 D-pad en TV** (`1edf317`) — el panel era inalcanzable en TV: FAB sin touch y `dispatchKeyEvent`
  reenviaba todo el D-pad al player. Solución: **MENU** abre/cierra (interceptado antes del player),
  **BACK** cierra si está abierto, y **con el panel abierto el D-pad NO va al player** → navega el
  overlay con foco normal. `isTv` vía UiModeManager; foco salta al primer control al abrir.
  **Verificado en BRAVIA real** (`192.168.1.224`, `BRAVIA_VU31`): MENU abre, D-pad recorre/togglea
  chips (log se filtró), BACK cierra sin salir del escenario.

**Notas de infra**
- IP de la BRAVIA ahora `192.168.1.224:5555` (era `.0.24`; DHCP). Requiere aceptar la autorización
  ADB en pantalla al conectar (`adb connect` → "unauthorized" hasta aceptar en la TV).
- Screenshots en TV: las tools MCP apuntan al A53 (ANDROID_SERIAL); para la BRAVIA se usó
  `adb -s <ip> exec-out screencap -p`.

**Siguiente paso**
Roadmap del overlay: queda **#5** (rebuffer ratio con ventana móvil, separar cold/warm). El resto
(#1–#4, #6, #7) hechos. Repetir baseline QoE en TV/Fire TV sigue pendiente.

---

## 2026-06-23 (cont.) — Decisión de navegación: MCP custom validado (ni Maestro ni Appium)

**Objetivo de la sesión**
Resolver la fricción de navegación en device (taps ciegos, `uiautomator dump` se cuelga con video
por "could not get idle state"). Pregunta abierta: ¿Maestro o Appium?

**Hallazgo**
El repo YA tenía la solución, implementada pero sin probar:
- `DeepLinkRouterActivity` → `sdkqa://scenario/<key>` lanza cualquier escenario determinista
  (las Activities son `exported=false`; el router debug-only las lanza desde el mismo uid).
- `tools/exploratory-mcp` → servidor MCP custom (stdio, Node) con 9 tools: `navigate_deeplink`,
  `tap`, `observe_screenshot/ui_hierarchy/logcat/session_state/crashes/network/analytics`.

**Qué se hizo**
- Validado el MCP end-to-end con `test-client.mjs` contra el A53: navega, screenshot, dump,
  logcat (capturó el snapshot del Debug Panel), y `observe_session_state` leyó el estado interno
  del player (position/duration/isPlaying/config/eventos) vía el bridge debug.
- Probado el deep link directo: `am start -d sdkqa://scenario/vod` → ok, WARM, 279ms.

**Decisión (registrada en CLAUDE.md)**
Ni Maestro ni Appium por ahora. El harness adb+bridge cubre ~90%; `observe_session_state` da
introspección del player que Maestro/Appium NO pueden. El único gap (tap por selector vs
coordenadas) se cubre derivando `bounds` de `observe_ui_hierarchy`. Si eso resultara frágil →
Maestro (no Appium) para la Fase 3. El plan original del harness (`exploratory-ai.md`) ya
reservaba Appium solo para F3.

**Entregables**
- `CLAUDE.md` (nuevo) — documenta el harness arriba para que cada sesión lo sepa desde el inicio.
- `.mcp.json` — conecta `sdk-qa-exploratory` a Claude Code (`claude mcp add -s project`).
  Activación: reiniciar Claude Code + aprobar el server + `/mcp` para verificar.

**Commits**
- `307dc72` docs: add CLAUDE.md
- (este) `.mcp.json` + SESSION_LOG

**Siguiente paso**
Tras reconectar, usar las tools MCP en vez de adb manual. Pendientes del overlay siguen vivos
(#3 señales en HUD, #7 D-pad en BRAVIA) + repetir baseline QoE en TV/Fire TV.

---

## 2026-06-23 (cont.) — Evaluación del Debug Overlay + color-coding por umbral

**Objetivo de la sesión**
Evaluar críticamente el debug overlay actual vs apps de referencia (ExoPlayer demo, Mux,
THEOplayer) y empezar a cerrar brechas. Cambios subidos directo a `main` (no branch).

**Evaluación — brechas priorizadas**
- 🔴 Alto impacto: (1) métricas sin color por umbral; (2) no se puede exportar/copiar el
  snapshot desde la app; (3) faltan señales que el SDK SÍ expone (`getCurrentUrl()` = CDN,
  `getPBId/getSId/getUId` = session IDs, `getCurrentVideoPlayingFormat`).
- 🟡 Medio: (4) log sin deltas entre callbacks; (5) rebuffer ratio mezcla cold+warm (falta
  ventana móvil); (6) log no filtrable por categoría.
- 🟢 Bajo / verificar: (7) D-pad en TV — el `BottomSheetBehavior` + FAB podrían NO ser
  navegables con control remoto (riesgo real, testean en BRAVIA; sin confirmar).

**Qué se hizo**
- Implementada la mejora #1 (color-coding por umbral). Cada valor QoE se pinta verde/ámbar/rojo
  según el ideal de industria (distinto de los ceilings de CI). Lógica en
  `PlaybackMetrics.Health` (dominio testeable); render con `ForegroundColorSpan` por valor.
- Verificado en A53: TTFF 3.7s → ámbar; buffer/rebuffer/dropped → verde. El régimen cold/warm
  ahora es visible de un vistazo.

**Commits**
- `b95ba1b` color-code QoE metrics by industry threshold
- `96c06c7` export QA snapshot to clipboard (mejora #2 — ver abajo)

**Mejora #2 completada — Export snapshot**
Botón "Copiar" en el panel → arma un reporte markdown (device, SDK version, contenido + CDN URL,
session IDs pbId/sId/uId, estado, métricas QoE, timeline de callbacks) y lo copia al portapapeles
para pegar en un ticket. Cubre #2 y, de paso, gran parte de #3 (CDN URL, session IDs, formato ya
van en el reporte). Cada getter del SDK en runCatching → si falta en el binario, degrada a "—".
Verificado en A53: el reporte trae la CDN URL real y los session IDs. Durante la prueba el HUD
cazó un `SocketTimeoutException` que el SDK reintentó sin disparar onError (bug de error
silencioso de sdk_known_bugs) — capturado por onLoadError.

**Siguiente paso (roadmap del overlay)**
1. ✅ #1 color-coding por umbral — hecho.
2. ✅ #2 export snapshot al portapapeles — hecho (incluye CDN URL + session IDs de #3).
3. #3 (parcial) — el HUD en vivo aún no muestra CDN URL / session IDs (sí van en el export);
   evaluar si vale mostrarlos en el HUD o basta con el export.
4. #7 Verificar D-pad en la BRAVIA antes de invertir más en el overlay para TV.

---

## 2026-06-23 (cont.) — Baseline limpio + calibración de gates

**Objetivo de la sesión**
Medir el comportamiento QoE real del SDK 10.0.7 en device físico, sin instrumentación de
captura encima, para calibrar los umbrales de `PlaybackQoeTest` con números reales (los
anteriores eran techos adivinados).

**Qué se hizo**
1. `QoeBaselineTest.kt` (`@LargeTest`, on-demand) — mide N reproducciones y loguea TTFF,
   rebuffer, bitrate, etc. en formato parseable (`adb logcat -s QOE_BASELINE`). No es gate, es
   medición. Reporta cada iteración + resumen min/mediana/max.
2. Corrida limpia en A53 (3 iters, soak 30s, vía `am instrument` directo sin Orchestrator).

**Hallazgo principal — dos regímenes (cold vs warm)**
| Iter | Régimen | TTFF | Rebuffer ratio | Calidad | BW medido | Buffer |
|---|---|---|---|---|---|---|
| 1 | **frío** | 7976ms | 52.2% | 854x480 | 350 kbps | 890ms |
| 2 | caliente | 2642ms | 0% | 1280x720 | 41 Mbps | 54s |
| 3 | caliente | 1523ms | 0% | 1280x720 | 27 Mbps | 48.6s |

- El cuello de botella del cold-start es la **red** (DNS/TCP/TLS/CDN cache miss + estimador ABR
  frío), no el SDK. Registrado como `CORE-LEARN-015` en `qa-knowledge/core-player/learnings.yaml`.
- La sospecha previa "360p con banda disponible" **NO se reprodujo** limpio → era artefacto de
  contaminación. Descartada.

**Calibración resultante (`PlaybackQoeTest`)**
- Añadido `@Before warmUp()`: reproducción descartada que calienta la red → la medición cae en
  steady-state (no flaky bajo el Orchestrator, donde cada test = proceso frío).
- TTFF gateado por **mediana de 3** muestras (la muestra única varía 1.5–5.1s; un techo ajustado
  con muestra única falla — se verificó empíricamente: falló a 5127ms).
- Umbrales: `TTFF < 4000ms` (caliente med 2.6s), `rebufferRatio < 0.02` (caliente 0%).
- Verificado: 4/4 pasan tras calibración.

**Siguiente paso**
1. (Opcional) Repetir baseline en TV (BRAVIA) y Fire TV para ver si el régimen cold/warm difiere
   por dispositivo.
2. (Capa C) Session export a JSON al cerrar la Activity, para diff de QoE entre versiones del SDK.
3. Integrar `--size large` en una corrida nightly de CI para que los gates QoE corran sin frenar
   cada commit.

---

## 2026-06-23 — Debug Panel QoE + gates de CI

**Objetivo de la sesión**
Arrancar la capa de observabilidad del SDK: poder *ver* y *medir* el comportamiento interno del
player (lo que ni los callbacks del SDK ni un runner de UI tipo Appium/Maestro exponen), y
compararlo contra el estándar de la industria (Mux, Brightcove, THEOplayer).

**Decisiones tomadas (y por qué)**
- **Una sola app, un APK** para TV y móvil — el SDK ya es adaptativo en runtime
  (`isAndroidTV()`, `handleTVKeyEvent()`, etc.). Separar apps escondería justo los bugs
  device-specific que QA debe cazar. Diferenciación por anotaciones `@MobileOnly`/`@TvOnly` y
  filtro `--target`, que el repo ya implementa.
- **Espresso/instrumentado como herramienta principal**, NO Appium/Maestro — solo el test
  in-process ve `msPlayer`, los getters y los callbacks. Maestro queda como complemento futuro
  para flows visuales (overlay episodio, fullscreen).
- **Métricas vía `AnalyticsListener` de ExoPlayer** — `msPlayer` es un `ExoPlayer` real
  casteable (el SDK lo construye con `ExoPlayer.Builder`), salvo con Chromecast activo donde
  pasa a `CastPlayer`. El colector es defensivo ante ese swap.

**Qué se hizo**
1. `PlaybackMetrics.kt` (core) — colector `AnalyticsListener` que acumula métricas QoE:
   TTFF, rebuffer (count/ms/ratio), bitrate + switches ABR, dropped frames, ancho de banda
   medido, buffer health, errores de carga silenciosos.
2. `BaseScenarioActivity` — engancha el colector (origen de TTFF = creación del player; attach
   temprano en `playerViewReady` para cerrar la carrera con el primer frame; re-attach en el
   poll de 500ms para el swap de Cast). HUD multilínea en el debug sheet. Expuesto como
   `playbackMetrics` para tests.
3. Layout `activity_base_scenario.xml` — bloque `tv_metrics` monospace.
4. `PlaybackQoeTest.kt` (4 tests, `@LargeTest`) — gates de CI: TTFF, rebuffer ratio, sin
   errores de carga silenciosos, ABR reporta bitrate/resolución. Helpers `metricsSnapshot()` y
   `awaitFirstFrame()` en `SdkTestExtensions`.

**Verificación en device (Samsung A53, SM-A536E, SDK 10.0.7)**
- HUD vivo en VOD Básico mostrando datos reales.
- 4/4 tests QoE pasan (`connectedDebugAndroidTest`).
- Observación de una corrida manual (⚠️ contaminada por uiautomator/screencap + un seek, NO
  baseline confiable): `TTFF 2.125s · BR 780kbps @ 640x360 con 2.3 Mbps disponibles · REBUF
  ratio 5.34%`. Señales a confirmar en corrida limpia: ABR sirviendo 360p con banda de sobra;
  rebuffer ratio muy por encima del 0.5% de industria.

**Commits**
- `a1d0d44` feat(observability): add QoE PlaybackMetrics debug panel
- (pendiente en esta sesión) tests QoE + refuerzos de attach del colector

**Siguiente paso**
1. **Baseline limpio**: correr `PlaybackQoeTest` en device físico sin instrumentación de captura
   encima, capturando los valores reales de TTFF/rebuffer/bitrate del SDK 10.0.7 → fijar
   umbrales calibrados (hoy son techos generosos anti-flaky).
2. Confirmar o descartar la observación del ABR (360p con banda disponible) con una corrida
   controlada.
3. (Capa C) Session export a JSON al cerrar la Activity, para diff entre versiones del SDK.

---

<!-- Plantilla para nuevas entradas:

## YYYY-MM-DD — <título corto>

**Objetivo de la sesión**

**Qué se hizo**

**Por qué**

**Verificación**

**Commits**

**Siguiente paso**

-->
