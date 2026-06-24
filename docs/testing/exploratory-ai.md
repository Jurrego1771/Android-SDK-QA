# Testing exploratorio asistido por IA — Appium + MCP

Harness donde un agente conduce la app real y descubre defectos que nadie escribió
como caso de prueba (regresiones de UX, crashes, errores de red/ads, fugas,
secuencias de callbacks anómalas), complementando la suite determinista
(`scripts/run-tests.sh`).

Diseño completo (componentes, responsabilidades, flujo de datos, escalabilidad,
seguridad, observabilidad): [`exploratory-ai-architecture.md`](exploratory-ai-architecture.md).

## Estado actual: Fases 1–2 (adb + bridge debug)

Pipeline validado end-to-end: **agente ↔ servidor MCP (stdio) ↔ adb/bridge ↔ device ↔ observación**.
Appium/UiAutomator2 se enchufa en F3 (tap-por-selector / gestos / a11y tree); las tools
actuales se resuelven con adb puro + el ContentProvider debug + mitmdump.

### Componentes (Fase 1)

| Pieza | Archivo |
|---|---|
| Servidor MCP custom | `tools/exploratory-mcp/src/server.mjs` |
| Helpers adb | `tools/exploratory-mcp/src/adb.mjs` |
| Exploration runner (oráculos deterministas) | `tools/exploratory-mcp/explore.mjs` |
| Cliente de verificación | `tools/exploratory-mcp/test-client.mjs` |
| Session controller | `scripts/explore.sh` |
| DeepLink router (debug-only) | `app/src/debug/.../debug/DeepLinkRouterActivity.kt` |
| Observability bridge (debug-only) | `app/src/debug/.../debug/ObservabilityProvider.kt` |
| Addon mitmproxy (red→JSONL) | `tools/exploratory-mcp/mitm_addon.py` |

### Tools expuestas

| Tool | Qué hace | Fase |
|---|---|---|
| `navigate_deeplink(key)` | Lanza un escenario vía `sdkqa://scenario/<key>` | F1 |
| `tap(x, y)` | Tap en coordenadas absolutas (adb input tap) | F1 |
| `observe_screenshot()` | PNG de la pantalla actual (image content) | F1 |
| `observe_ui_hierarchy()` | Jerarquía de vistas (uiautomator dump, con resource-ids/bounds) | F1 |
| `observe_logcat(tags?, sinceTime?, maxLines?)` | Slice de logcat por tags del SDK QA | F1 |
| `observe_session_state()` | Estado interno: callbacks en orden, offMainThread, player state, config (vía bridge) | F2 |
| `observe_crashes(maxLines?)` | Buffer 'crash' de logcat (FATAL EXCEPTION / ANR) | F2 |
| `observe_network(hostContains?, method?, maxFlows?)` | Flows HTTPS de mitmdump (incluye ads IMA) | F2 |
| `observe_analytics(provider?)` | Beacons clasificados: Mediastream / Comscore / Youbora / ads | F2 |

Claves de `navigate_deeplink` (= `DeepLinkRouterActivity.SCENARIOS`):
`vod live livedvr lived episode episode-custom ads drm pip reels switcher subtitles
fullscreen service audio-live audio-vod audio-episode audio-service direct-hls`.

## Cómo correr

### A. Exploración determinista (oráculos codificados, headless/CI)

```bash
./scripts/explore.sh --device R5CTB1W92KY --scenario vod [--skip-build]
```

Lanza el escenario, observa, aplica los oráculos de `docs/ai-context/business-rules.md`
(onReady < 15s, onPlay tras onReady, sin onError, sin FATAL EXCEPTION, posición avanza)
y escribe `ai-output/exploration-<ts>/` con `report.md` + evidencia (screenshot.png,
ui-hierarchy.xml, logcat.txt). Exit code 1 si hay hallazgos de severidad alta/critical.

### B. Verificar el servidor MCP

```bash
cd tools/exploratory-mcp && ANDROID_SERIAL=<serial> node test-client.mjs
```

### C. Exploración abierta con agente LLM (Claude Code)

Registrar el servidor MCP en `.mcp.json` del proyecto (NO incluido — lo agregás vos):

```json
{
  "mcpServers": {
    "sdk-qa-exploratory": {
      "command": "node",
      "args": ["tools/exploratory-mcp/src/server.mjs"],
      "env": { "ANDROID_SERIAL": "R5CTB1W92KY" }
    }
  }
}
```

Luego un agente Claude usa las tools `navigate_deeplink/tap/observe_*` para explorar
libremente, razonando contra los oráculos (`docs/ai-context/sdk-api-contract.md`,
`business-rules.md`, `qa-knowledge/core-player/`) y registrando hallazgos.

## Captura de red (F2 — `observe_network` / `observe_analytics`)

Las tools de red leen un JSONL que escribe `mitmdump` con `mitm_addon.py`. La captura viva
requiere proxy + CA en el device (paso manual; reusa la skill `android-network-proxy`,
cambiando `mitmweb` por `mitmdump`):

```powershell
# 1) mitmdump con el addon, escribiendo flows a un JSONL
$env:MITM_FLOWS = "D:\repos\jurrego1771\SDK-Android-Qa\ai-output\mitm-flows.jsonl"
$mitm = Get-ChildItem "$env:APPDATA\Python\Python*\Scripts\mitmdump.exe" | Select -First 1 -Expand FullName
& $mitm --listen-port 8080 -s tools/exploratory-mcp/mitm_addon.py
# 2) device: WiFi proxy → IP_DEL_PC:8080  +  instalar CA de mitm.it (Ajustes > Seguridad)
#    (network_security_config ya confía en CA de usuario en debug)
```

Luego, con `MITM_FLOWS` apuntando al mismo archivo, el servidor MCP sirve `observe_network`
y `observe_analytics`. `scripts/explore.sh` propaga `MITM_FLOWS` si está en el entorno.

## Seguridad

- **Todo lo nuevo es debug-only.** `DeepLinkRouterActivity` y `ObservabilityProvider` viven
  en `app/src/debug` → ausentes en release (verificado en el manifest merged de release).
  La confianza en CA de usuario (`network_security_config` `<debug-overrides>`) también.
- **El bridge es `exported=true` solo en debug** (necesario para `adb content query`, que
  corre con uid distinto al de la app). No expone secretos (sin tokens DRM ni headers de
  licencia) y está ausente en release.
- El servidor MCP corre local y habla con el device por adb (USB/LAN), no se expone a red.
- El agente opera solo sobre contenido de test (`TestContent`) y acciones no destructivas.

## Roadmap

- **F2**: `ObservabilityProvider` (ContentProvider debug) → `observe_session_state`
  (callbacks en orden + player state vía adb content query); `observe_crashes`;
  `observe_network`/`observe_analytics` (mitmdump JSON, reusa skill `android-network-proxy`).
- **F3**: Appium/UiAutomator2 (tap-por-selector, gestos, `observe_a11y_tree`); `observe_shared_prefs`.
- **F4**: multi-device + paralelo; findings → `qa-knowledge/{módulo}/*.yaml`; clasificación
  con el agente `test-analyzer`.
- **F5**: presupuestos QoE (time-to-first-frame, latencia de seek) como invariantes.
