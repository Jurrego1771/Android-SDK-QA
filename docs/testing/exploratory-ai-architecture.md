# Arquitectura — Testing exploratorio asistido por IA (Appium + MCP)

Diseño del harness donde un agente de IA conduce la app real del SDK de Mediastream,
la observa desde múltiples ángulos y descubre defectos que nadie escribió como caso de
prueba. Complementa la suite determinista (`scripts/run-tests.sh`), no la reemplaza.

> Guía de uso y estado de implementación: [`exploratory-ai.md`](exploratory-ai.md).
> Estado: **Fases 1–2 construidas y verificadas**; F3–F5 en roadmap (al final).

---

## 1. Componentes

```
┌─────────────────────────────────────────────────────────────────────────┐
│  AGENTE EXPLORATORIO (Claude)   loop: observar → actuar → detectar → registrar │
│   oráculos: sdk-api-contract.md · business-rules.md · qa-knowledge/             │
└───────────────▲───────────────────────────────────────────┬──────────────┘
                │ MCP (tools tipadas)                         │ findings
        ┌───────┴────────────────────────────────────────────▼──────────────┐
        │  SERVIDOR MCP CUSTOM (Node/TS, stdio)                              │
        │  Acción:      navigate_deeplink · tap · swipe · press_key · launch │
        │  Observación: screenshot · ui_hierarchy · a11y_tree · session_state│
        │               · logcat · network · analytics · crashes · prefs     │
        │  Findings:    record_finding → qa-knowledge/*.yaml + ai-output/     │
        └───┬──────────────┬───────────────┬───────────────┬────────────────┘
            │ Appium client │ adb           │ mitmdump      │ adb content query
        ┌───▼────┐    ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼──────────────┐
        │ Appium │    │  logcat   │   │ mitmdump  │   │ Observability      │
        │ UiAuto2│    │  tailer   │   │ (flows    │   │ bridge (debug      │
        │ driver │    │  (tags)   │   │  JSON)    │   │ ContentProvider)   │
        └───┬────┘    └─────┬─────┘   └─────┬─────┘   └─────┬──────────────┘
            └───────────────┴───────────────┴───────────────┘
                          DISPOSITIVO (debug build)
            Samsung A53 (móvil) · Sony BRAVIA (TV)  — vía ANDROID_SERIAL
```

| # | Componente | Construido en | Implementación |
|---|---|---|---|
| 1 | **Agente exploratorio** | F1 (oráculos) / F4 (LLM) | Variante determinista `explore.mjs`; variante LLM vía `.mcp.json` |
| 2 | **Servidor MCP custom** | F1 | `tools/exploratory-mcp/src/server.mjs` (Node/ESM, SDK MCP, stdio) |
| 3 | **Appium + UiAutomator2** | F3 | Driver enchufable (tap-por-selector, gestos, a11y); F1 usa adb puro |
| 4 | **Colector logcat** | F1 | `observe_logcat` → `adb logcat -s SDK_QA …` |
| 5 | **Colector red (mitmdump)** | F2 ✓ | `mitm_addon.py` (flows→JSONL); `observe_network`/`observe_analytics` |
| 6 | **Observability bridge** | F2 ✓ | `app/src/debug/.../ObservabilityProvider.kt` (ContentProvider) |
| 7 | **DeepLink router** | F1 | `app/src/debug/.../DeepLinkRouterActivity.kt` (debug-only) |
| 8 | **Session controller** | F1 | `scripts/explore.sh` (device select + build/install + run) |
| 9 | **Findings sink** | F4 | `qa-knowledge/{módulo}/*.yaml` + `ai-output/exploration-<ts>/` |

---

## 2. Responsabilidades

| Componente | Responsabilidad | Reusa |
|---|---|---|
| **Agente exploratorio** | Decide qué explorar; ejecuta el loop; compara observaciones contra oráculos; clasifica anomalías; sintetiza reporte | Agente `test-analyzer` (clasificación bug real / entorno / flaky) |
| **Servidor MCP custom** | Traduce intención del agente en llamadas a Appium/adb/colectores; expone tools tipadas; **normaliza toda observación a JSON** | — |
| **Appium + UiAutomator2** | Driving fino de UI: tap-por-selector, swipe, teclas, page source (jerarquía + a11y) | — |
| **Colector logcat** | Tail por tags (`SDK_QA`, `SDK_QA_FAIL`, `AndroidRuntime`, `StrictMode`, `SDK_QA_DEEPLINK`), ventana por timestamp | Tags que el repo ya emite (`BaseScenarioActivity`, `SdkEvidenceRule`) |
| **Colector red** | Captura HTTPS/ads/analytics como flows JSON filtrables | Skill `android-network-proxy` (mitmproxy + CA en debug) |
| **Observability bridge** | Expone estado interno: `callbackCaptor.eventOrderSnapshot()` + player state, sin tocar `src/main` | `CallbackCaptor`, `BaseScenarioActivity` (lectura vía lifecycle callback) |
| **DeepLink router** | Lanzar cualquier escenario determinísticamente desde el mismo proceso/uid (sortea el `exported=false`) | Activities de escenario existentes |
| **Session controller** | Selecciona device, asegura debug build, arranca/para colectores, recolecta artefactos, siembra al agente | Lógica de selección de device de `run-tests.sh` |
| **Findings sink** | Persiste hallazgos con evidencia y redacción de secretos | Esquema `qa-knowledge/` + IDs `{MOD}-DEF-NNN` |

---

## 3. Flujo de datos

1. **Seed** — El session controller (`scripts/explore.sh`) selecciona `ANDROID_SERIAL`,
   asegura el debug build instalado, arranca colectores (logcat / mitmdump / bridge) y
   siembra al agente con el escenario objetivo + oráculos (`sdk-api-contract.md`,
   `business-rules.md`, `qa-knowledge/core-player/`).
2. **Loop del agente** (cada paso = 1+ tool calls MCP):
   - **Observar baseline** → `observe_screenshot` + `observe_ui_hierarchy` + `observe_session_state`.
   - **Actuar** → `navigate_deeplink("vod")` / `tap` / `swipe` / `press_key`.
   - **Re-observar** → screenshot + `observe_session_state` + `observe_logcat(sinceTime)` + `observe_network(filter)`.
   - **Detectar** contra invariantes (ver Oráculos abajo).
   - **Registrar** → `record_finding(...)` con bundle de evidencia (screenshot + slice de
     logcat + flows + snapshot de sesión).
3. **Cierre** — El controller recolecta artefactos a `ai-output/exploration-<ts>/`; el agente
   escribe `report.md` y vuelca defectos/learnings a `qa-knowledge/`.

**Oráculos** (cómo el agente decide qué es bug; derivados de `business-rules.md`):
no `onError`/`onPlaybackErrors`/`onEmbedErrors` en flujo válido · VOD `onReady` < 15s,
`onPlay` tras `onReady`, posición avanza, `duration` > 0 · Live `onReady` < 30s · callbacks
en main thread (`firedOnMainThread`) · Episode `nextEpisodeIncoming` 1×/episodio · ningún
`FATAL EXCEPTION` ni leak nuevo (excluye `LibraryLeak` ya documentados) · Ads: beacons VAST
esperados presentes en los flows.

---

## 4. Escalabilidad

- **1 sesión por device** (Appium server + sesión MCP) con puertos distintos
  (`--port`, `systemPort`, `mjpegServerPort`) + `ANDROID_SERIAL`. Reusa la selección de
  device de `run-tests.sh`.
- **Paralelismo por device** — el session controller hace fan-out (A53 móvil + BRAVIA TV
  simultáneos); las anotaciones `@MobileOnly/@TvOnly` distinguen capacidades por escenario.
- **Reproducibilidad / sharding** — los deep links son *seeds* deterministas: se reparten
  conjuntos de escenarios por device y se re-ejecuta un hallazgo exacto.
- **Tools MCP stateless** — cada call es un round-trip adb/Appium; el estado vive en el
  device + colectores → escalado horizontal trivial.
- **Presupuesto acotado** por sesión (pasos / tiempo / tokens), patrón loop-until-budget.
- **mitmdump por device** (puerto propio) o compartido con filtro por IP del device.

---

## 5. Seguridad

- **Toda superficie nueva es debug-only.** `DeepLinkRouterActivity`, `ObservabilityProvider`
  y la confianza en CA de usuario (`network_security_config` `<debug-overrides>`) viven en
  `app/src/debug` / debug-overrides → **ausentes en release** (verificado: el manifest merged
  de release no contiene `DeepLinkRouterActivity` ni el scheme `sdkqa`).
- **ContentProvider del bridge es `exported=true` solo en debug** — necesario porque
  `adb content query` corre con uid de shell distinto al de la app (un provider no-exportado
  da `SecurityException`, incluso vía `run-as`). Mitigación: **no expone secretos** (sin
  tokens DRM ni headers de licencia), se consulta solo vía adb local (USB/LAN) y **está
  ausente en release** (verificado en el manifest merged).
- **MCP server local** que habla con el device por adb; no abre puertos de red.
- **mitmproxy CA** solo en dispositivos de test; teardown quita el proxy de la WiFi. El
  SSL-pinning de IMA queda opaco (limitación documentada; sin bypass por defecto).
- **Redacción de secretos** — el findings sink redacta `TestContent.Drm.ACCESS_TOKEN`,
  `ACCOUNT_ID` y headers de licencia antes de escribir a yaml/reports.
- **Acciones no destructivas** — el agente opera solo sobre contenido de test (`TestContent`)
  y acciones de reproducción/UI; sin compras reales ni mutación en PRODUCTION (allowlist
  explícita de escenarios/contenido; PRODUCTION es solo lectura/playback).

---

## 6. Observabilidad — cómo exponer cada fuente al agente

Cada fuente es una **tool MCP** que devuelve JSON normalizado. "Fase" = cuándo se entrega;
"Cambio app" = qué hay que agregar (todo debug-only).

| Fuente | Tool MCP | Mecanismo | Fase | Cambio app |
|---|---|---|---|---|
| **Screenshot actual** | `observe_screenshot` | Appium `getScreenshot` (fallback `adb exec-out screencap -p`); devuelve image content | **F1** ✓ | — |
| **Jerarquía UI Android** | `observe_ui_hierarchy` | `adb shell uiautomator dump` → XML con `resource-id`/`text`/`bounds`/`content-desc` (`tv_status`, `recycler_log`, `fab_debug`…) | **F1** ✓ | — |
| **Accessibility Tree** | `observe_a11y_tree` | Appium page source con atributos a11y (`content-desc`, `accessible`, `focusable`); proyección a11y-only | F3 | — |
| **Logcat** | `observe_logcat(tags?, sinceTime?, maxLines?)` | Tail `adb logcat -s SDK_QA SDK_QA_FAIL StrictMode AndroidRuntime …` | **F1** ✓ | — (tags existentes) |
| **Crash reports** | `observe_crashes(maxLines?)` | Buffer `crash` de logcat (`adb logcat -b crash`): FATAL EXCEPTION / ANR. Complementa LeakCanary (`SdkTestRule`) | **F2** ✓ | — |
| **Network requests** | `observe_network(hostContains?, method?, maxFlows?)` | `mitmdump -s mitm_addon.py` → JSONL; filtro por host/método | **F2** ✓ | Reusa skill (CA ya confiada en debug) |
| **Analytics events** | `observe_analytics(provider?)` | Subconjunto de red clasificado: Mediastream / Comscore / Youbora / ads (DoubleClick/IMA) | **F2** ✓ | Reusa red |
| **Deep Links** | `navigate_deeplink(key)` | Scheme `sdkqa://scenario/<key>` → `DeepLinkRouterActivity` lanza la Activity interna | **F1** ✓ | Router + intent-filter (debug) |
| **Shared Preferences** | `observe_shared_prefs` | `adb shell run-as com.example.sdk_qa cat shared_prefs/*.xml` (app debuggable). Hoy no hay prefs → vacío; hook para futuro | F3 | — (cuando existan) |
| **Estado de sesión** | `observe_session_state` | ContentProvider debug consulta `callbackCaptor.eventOrderSnapshot()` + `player.msPlayer` (pos/dur/isPlaying/playbackState) + config (id/type/env), vía `adb shell content query` | **F2** ✓ | ContentProvider (debug) |

### Detalle de los dos cambios en la app (ambos debug-only, sin tocar `src/main`)

**a) DeepLink router** — `app/src/debug/.../debug/DeepLinkRouterActivity.kt` + `intent-filter`
en `app/src/debug/AndroidManifest.xml`. `exported=true` solo en debug; `when(key)` mapea
`vod|live|ads|…` → `startActivity(...)` de la Activity de escenario (mismo proceso/uid → sin
SecurityException). **Construido y verificado en F1.**

**b) Observability bridge** — `app/src/debug/.../debug/ObservabilityProvider.kt` (ContentProvider
declarado solo en debug). En `onCreate()` registra `ActivityLifecycleCallbacks` para rastrear
la `BaseScenarioActivity` en foreground (cero cambios en `BaseScenarioActivity`, ya público).
`query("content://com.example.sdk_qa.debug.observability/session")` → 1 fila JSON:
`{events:[…], offMainThread:[…], player:{positionMs,durationMs,isPlaying,playbackState}, config:{id,type,env}}`
(lee `msPlayer` en main thread con post+latch). Se consulta vía `adb shell content query`
(sin puerto/forward, sin dependencia extra). **F2 ✓ — construido y verificado.**

---

## Roadmap

- **F1 (hecho)** — DeepLink router + MCP server con `navigate_deeplink`, `tap`,
  `observe_screenshot`, `observe_ui_hierarchy`, `observe_logcat`; oráculos deterministas;
  PoC verificado en el A53.
- **F2 (hecho)** — bridge `observe_session_state` + `observe_crashes` +
  `observe_network`/`observe_analytics` (mitmdump JSONL vía `mitm_addon.py`); el exploration
  runner ahora deriva oráculos del bridge (orden de callbacks, threading, posición, crash).
  Verificado en el A53. Captura de red viva requiere proxy+CA (manual; ver guía).
- **F3** — Appium/UiAutomator2 (tap-por-selector, gestos, `observe_a11y_tree`) + `observe_shared_prefs`.
- **F4** — multi-device + paralelo; findings → `qa-knowledge/*.yaml`; clasificación con `test-analyzer`.
- **F5** — presupuestos QoE (time-to-first-frame, latencia de seek) como invariantes + gating de regresión.
