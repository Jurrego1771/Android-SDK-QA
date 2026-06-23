# Bitácora de sesiones — Observabilidad y exploración del SDK

> Registro vivo de cada sesión de trabajo: **qué** se hizo, **por qué**, y el **siguiente paso**.
> Entradas en orden cronológico inverso (la más reciente arriba). Se actualiza al cierre de cada sesión.

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
