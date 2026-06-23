# Bitácora de sesiones — Observabilidad y exploración del SDK

> Registro vivo de cada sesión de trabajo: **qué** se hizo, **por qué**, y el **siguiente paso**.
> Entradas en orden cronológico inverso (la más reciente arriba). Se actualiza al cierre de cada sesión.

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
