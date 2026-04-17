# Risk Map — Mediastream Platform SDK Android

**Version:** 1.1
**Fecha baseline:** 2026-04-16
**SDK Version bajo test:** 11.0.0-alpha.01
**SDK consumido como:** dependencia Maven (`io.github.mediastream:mediastreamplatformsdkandroid:11.0.0-alpha.01`)
**Cobertura global actual:** 0% (0 tests reales implementados)

> **Scope:** Este repo testea el SDK como **caja negra** (dependencia externa).
> Los unit tests de internals del SDK (Config, models, ApiService, etc.) van en el repo `MediastreamPlatformSDKAndroid`.

---

## Como usar este documento

Este mapa de riesgos es un documento vivo. Cada vez que se implementa un test o se descubre un bug nuevo:
1. Actualizar el campo `Cobertura` de la feature correspondiente
2. Actualizar `Cobertura global actual` en el encabezado
3. Agregar el bug al registro de bugs encontrados si aplica
4. Registrar el snapshot en `history/` con la fecha

**Escala de riesgo:**

| Nivel | Criterio |
|-------|---------|
| 🔴 CRITICO | Fallo produce indisponibilidad total del player o datos incorrectos reportados |
| 🟠 ALTO | Fallo afecta feature principal visible para el usuario final |
| 🟡 MEDIO | Fallo afecta feature secundaria o tiene workaround |
| 🟢 BAJO | Fallo cosmético o de baja frecuencia de uso |

---

## Matriz de Riesgos por Feature

### Core — Inicializacion y Configuracion

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Inicializacion con `id` + API | 🔴 CRITICO | Sin esto, nada funciona. API call sincrona en init | 0% | Ninguno |
| Inicializacion con `src` directo | 🟠 ALTO | Path alternativo critico, diferente flujo interno | 0% | Ninguno |
| `MediastreamPlayerConfig.copy()` | 🟡 MEDIO | Bug aqui afecta episodios secuenciales | 0% | Ninguno |
| `mergePersistentFrom()` | 🟡 MEDIO | Bug afecta volumen/brillo entre episodios | 0% | Ninguno |
| Manejo de errores HTTP (4xx/5xx) | 🔴 CRITICO | Sin manejo correcto, el player puede crashear | 0% | Ninguno |
| Config con campos null inesperados | 🟠 ALTO | La API puede devolver campos opcionales como null | 0% | Ninguno |

### Reproduccion — Video

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| VOD — reproduccion completa | 🔴 CRITICO | Feature base del SDK | 0% | Ninguno |
| VOD — seek | 🟠 ALTO | Usado frecuentemente por usuarios | 0% | Ninguno |
| VOD — autoplay=false | 🟡 MEDIO | Configuracion menos comun pero debe funcionar | 0% | Ninguno |
| Live — reproduccion | 🔴 CRITICO | Feature base del SDK | 0% | Ninguno |
| Live — reconexion automatica | 🟠 ALTO | Streams caen temporalmente con frecuencia | 0% | Ninguno |
| Episode — carga siguiente | 🟠 ALTO | Feature diferenciadora del SDK | 0% | Ninguno |
| Episode — `nextEpisodeIncoming` timing | 🟡 MEDIO | Timing incorrecto = mala UX | 0% | Ninguno |

### Reproduccion — Audio

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Audio live (Icecast/HLS) | 🟠 ALTO | Caso de uso de radio digital | 0% | Ninguno |
| Audio podcast VOD | 🟠 ALTO | Caso de uso frecuente | 0% | Ninguno |
| Background playback | 🔴 CRITICO | Requisito fundamental de audio | 0% | Ninguno |
| Metadata SSE (`onLiveAudioCurrentSongChanged`) | 🟡 MEDIO | Feature de metadata, no bloquea reproduccion | 0% | Ninguno |

### DVR

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| DVR — seekbar visible | 🟠 ALTO | UX critica del DVR | 0% | Ninguno |
| DVR — seek hacia atras | 🔴 CRITICO | Feature principal del DVR | 0% | Ninguno |
| DVR — offset calculation (fictitious timeline) | 🔴 CRITICO | Logica custom compleja, alta probabilidad de bugs de offset | 0% | Ninguno |
| DVR — volver a live edge | 🟠 ALTO | Flujo comun del usuario | 0% | Ninguno |
| DVR + Ads combinados | 🔴 CRITICO | Complejidad maxima: dos features complejas interactuando | 0% | Ninguno |

### Publicidad (Ads)

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Preroll IMA | 🔴 CRITICO | Impacto en revenue directo | 0% | Ninguno |
| Error de ad → contenido continua | 🔴 CRITICO | Si falla, el usuario queda en pantalla negra | 0% | Ninguno |
| VMAP (multiples ads) | 🟠 ALTO | Mas complejo que un ad simple | 0% | Ninguno |
| Ad skip | 🟡 MEDIO | Funcionalidad estandar de IMA | 0% | Ninguno |
| DAI / SSAI | 🟠 ALTO | Integracion server-side mas compleja | 0% | Ninguno |
| `onAdEvents` callbacks correctos | 🟠 ALTO | Clientes dependen de estos para UI | 0% | Ninguno |

### DRM

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| DRM Widevine L1 | 🟠 ALTO | Contenido premium requiere L1 | 0% | Ninguno |
| DRM Widevine L3 | 🟡 MEDIO | Fallback para dispositivos sin L1 | 0% | Ninguno |
| Error de licencia → feedback correcto | 🟠 ALTO | Sin feedback, el usuario no sabe por que falla | 0% | Ninguno |

### Analytics

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Comscore — inicializacion condicional | 🟠 ALTO | Si se inicializa sin datos, puede crashear Comscore | 0% | Ninguno |
| Comscore — eventos play/pause/end | 🟡 MEDIO | Datos de audiencia pueden ser incorrectos | 0% | Ninguno |
| Youbora — inicializacion | 🟡 MEDIO | Similar a Comscore | 0% | Ninguno |
| Analytics sin datos en API | 🟠 ALTO | API puede no devolver datos de analytics → no crashear | 0% | Ninguno |

### Features Avanzadas

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Picture-in-Picture | 🟡 MEDIO | Feature avanzada, uso moderado | 0% | Ninguno |
| Chromecast — sesion completa | 🟡 MEDIO | Requiere hardware especifico | 0% | Ninguno |
| Android TV — UI correcta | 🟡 MEDIO | Requiere emulador/dispositivo TV | 0% | Ninguno |
| Android Auto | 🟢 BAJO | Feature de nicho, no bloquea otros features | 0% | Ninguno |
| Subtitulos WebVTT | 🟡 MEDIO | Feature comun pero con formato estandar | 0% | Ninguno |
| Subtitulos ASS/SSA | 🟠 ALTO | Parser custom = mayor riesgo de bugs | 0% | Ninguno |
| Descarga offline | 🟢 BAJO | Feature experimental/demo | 0% | Ninguno |
| Notificaciones media | 🟠 ALTO | Requerido para audio en background | 0% | Ninguno |

### Callbacks

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Orden correcto de callbacks | 🔴 CRITICO | Clientes construyen logica sobre el orden de callbacks | 0% | Ninguno |
| Callbacks no se duplican | 🟠 ALTO | Multiples callbacks registrados pueden recibir eventos dobles | 0% | Ninguno |
| `onError` vs `onEmbedErrors` vs `onPlaybackErrors` distincion | 🟠 ALTO | Confusion frecuente, clientes no manejan correctamente | 0% | Ninguno |
| Callback null safety | 🟡 MEDIO | NPE si callback tiene metodo no implementado | 0% | Ninguno |

### Next Episode UI

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Timing: callback 3s antes del overlay | 🔴 CRITICO | Si el timing es incorrecto, modo manual falla (el cliente no tiene tiempo de llamar updateNextEpisode) | 0% | Ninguno |
| Modo API: overlay aparece automaticamente | 🟠 ALTO | Path mas usado, debe funcionar sin intervencion del cliente | 0% | Ninguno |
| Modo manual: overlay solo con updateNextEpisode() | 🔴 CRITICO | Si aparece sin confirmacion, el cliente pierde control | 0% | Ninguno |
| Auto-transicion a los 5 segundos | 🟠 ALTO | Timer debe cancelarse si usuario hace click en "Ver creditos" | 0% | Ninguno |
| Boton "Ver creditos" cancela auto-transicion | 🟠 ALTO | Si no cancela el timer, hay transicion inesperada | 0% | Ninguno |
| `nextEpisodeIncoming` se emite solo una vez | 🟡 MEDIO | Si se emite dos veces, puede crear estado inconsistente en el cliente | 0% | Ninguno |
| Ultimo episodio: no muestra overlay | 🟡 MEDIO | Bug comun en feature de episodios | 0% | Ninguno |

### Services

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Service forwardea nextEpisodeIncoming correctamente | 🔴 CRITICO | Si falla, el modo manual de siguiente episodio no funciona desde background | 0% | Ninguno |
| Botones notificacion (Next/Prev) disparan callbacks | 🟠 ALTO | Feature visible para el usuario | 0% | Ninguno |
| EventBus no interfiere con EventBus del cliente | 🟠 ALTO | Collision de eventos puede causar bugs muy dificiles de diagnosticar | 0% | Ninguno |
| Service se destruye correctamente al cerrar player | 🟡 MEDIO | Leak de service puede agotar recursos | 0% | Ninguno |
| MediastreamPlayerServiceWithSync — navegacion Auto | 🟡 MEDIO | Requiere head unit de Android Auto para testear | 0% | Ninguno |
| isFromSyncService=true funciona sin Activity | 🟠 ALTO | El player no debe crashear sin Activity en modo service | 0% | Ninguno |

### PiP

| Feature | Riesgo | Razon del riesgo | Cobertura | Tests implementados |
|---------|--------|-----------------|-----------|-------------------|
| Jerarquia de config: local > API | 🟠 ALTO | Si la jerarquia falla, el cliente no puede forzar disable | 0% | Ninguno |
| pip=NONE + API sin campo pip → false (no crashea) | 🟠 ALTO | NPE si mediaInfo es null al evaluar | 0% | Ninguno |
| startPiP() sin Activity (pipHandler null) | 🔴 CRITICO | NPE si pipHandler no fue inicializado | 0% | Ninguno |
| Aspect ratio 16:9 fijo | 🟢 BAJO | Limitacion conocida, no es un bug | 0% | Ninguno |

---

## Resumen ejecutivo de riesgos

### Features CRITICAS sin cobertura (prioridad 1)

1. **Inicializacion + manejo de errores HTTP** — todo depende de esto
2. **DVR fictitious timeline offset** — logica mas compleja y propensa a bugs
3. **Ads: error de ad → contenido continua** — impacto directo en revenue
4. **Background audio playback** — requisito fundamental
5. **Orden correcto de callbacks** — contrato con todos los clientes del SDK
6. **Next episode modo manual: timing del callback** — si falla, el cliente no puede llamar updateNextEpisode() a tiempo
7. **Next episode modo manual: overlay solo con confirmacion** — si aparece sin updateNextEpisode(), el cliente pierde el control
8. **startPiP() sin Activity (pipHandler null)** — NPE critica
9. **Service forwardea nextEpisodeIncoming** — requerido para modo manual desde background

### Features ALTAS sin cobertura (prioridad 2)

10. Live streaming reconexion
11. Episode modo API: overlay automatico y auto-transicion
12. Boton "Ver creditos" cancela timer de auto-transicion
13. Custom ASS/SSA subtitle parser
14. Notificaciones de media
15. EventBus: no interferencia con EventBus del cliente
16. Inicializacion condicional de analytics (sin crashear)
17. Jerarquia pip config: local DISABLE sobrepasa valor de API

---

---

## Cambios pendientes — v10.0.0 (breaking changes a preparar)

> Documentados en `context/pip-implementation.md` del SDK. NO estan en el codigo actual (v9.9.0).

| Cambio | Riesgo de migracion | Impacto | Accion requerida |
|--------|--------------------|---------|--------------------|
| `onFullscreen()` → `onFullscreen(enteredForPip: Boolean)` | 🔴 CRITICO | Breaking change — todas las implementaciones del callback rompen si no se actualiza | Actualizar firma en todas las Activities del sample app (12 archivos) |
| `pipReplaceActivityContentWithPlayer` nuevo campo | 🟠 ALTO | Sin el campo, PiP en small container se ve mal | Agregar config en casos de uso con contenedor pequeno |
| `pipExpandToFullscreenFirst` formalizado | 🟡 MEDIO | Cambio de comportamiento para integradores que usan fullscreen antes de PiP | Documentar y testear el nuevo flujo |

**Referencia:** `docs/features/upcoming-v10-pip-changes.md`

---

## Registro de bugs encontrados

| Fecha | Feature | Descripcion | Severidad | Estado | Test que lo cubre |
|-------|---------|-------------|-----------|--------|------------------|
| — | — | (sin bugs registrados aun) | — | — | — |

---

## Historial de cobertura

| Fecha | Cobertura global | Tests unitarios | Tests integracion | Tests E2E | Notas |
|-------|-----------------|----------------|-------------------|-----------|-------|
| 2026-04-16 | 0% | 0 | 0 | 0 | Baseline inicial |

---

*Proxima revision: cuando se implementen los primeros tests*
*Mantener actualizado con cada PR que agrega o modifica tests*
