# Business Rules — Mediastream Platform SDK Android

> Reglas de negocio por feature. Definen qué es ÉXITO, qué es FALLO, y los límites temporales esperados.
> Fuente: documentación de features en `docs/features/`, SDK source, y comportamiento observado.
> Usar estas reglas para validar asserts en tests generados.

---

## Reglas Globales (aplican a todas las features)

| Regla | Descripción |
|-------|-------------|
| **No onError en flujo normal** | Ningún flujo de reproducción válido debe disparar `onError`. Si dispara, es un fallo. |
| **Callbacks en Main Thread** | Todos los callbacks del SDK se disparan en el Main Thread. Si no, es un bug del SDK. |
| **Timeout VOD inicial** | `onReady` debe disparar dentro de **15 segundos** con conexión normal (stream HLS público). |
| **Timeout Live inicial** | `onReady` para Live puede tardar más (hasta **30 segundos**) por dependencia del servidor. |
| **autoplay=true por defecto** | Si no se configura autoplay, el player INICIA reproducción automáticamente tras onReady. |
| **src vs id** | `src` directo no llama a la API de Mediastream. `id` siempre hace una llamada HTTP a la API. |

---

## Feature: VOD (Video Bajo Demanda)

### Qué es ÉXITO:
- `onReady` se dispara dentro de 15s
- `onPlay` se dispara después de `onReady` (con autoplay=true)
- El player es observable (contenedor visible, `msPlayer != null`)
- `msPlayer.currentPosition` avanza con el tiempo
- `msPlayer.duration` > 0 después de `onReady`
- `seekTo(targetMs)` mueve la posición a ±5000ms del objetivo

### Qué es FALLO:
- `onError` dispara en cualquier punto del flujo
- `onReady` no dispara dentro de 15s
- `msPlayer` es null después de `onReady`
- `msPlayer.duration` es 0 o negativo para VOD con duración conocida
- `seekTo()` no mueve la posición (posición queda en 0 o en el punto anterior)

### Reglas de assert:
- Para seek: verificar que `currentPosition` está dentro de `targetMs ± 5000ms` (margen para buffering)
- Para duración: verificar `duration > 0` (no el valor exacto, puede variar)
- Para posición: verificar que avanza después de 2s de reproducción (`position > 0`)

---

## Feature: Live Streaming

### Qué es ÉXITO:
- `onReady` se dispara dentro de 30s
- `onPlay` se dispara después de `onReady`
- `msPlayer.duration` retorna `Long.MIN_VALUE` o valor inmenso (live no tiene duración fija)
- El player se reconecta automáticamente si `automaticallyReconect=true` (default)

### Qué es FALLO:
- `onError` dispara inmediatamente al cargar (indica error del stream o de la API)
- `onReady` no dispara dentro de 30s
- Para Live: `isVodCase()` retorna true (no debería)

### Reglas específicas de DVR:
- `dvr=true` habilita la seekbar en el player
- Con DVR activo: el usuario puede hacer seek hacia atrás hasta `windowDvr` segundos
- `getFixedCurrentTime()` retorna la posición ajustada al offset DVR (no el tiempo absoluto de ExoPlayer)
- Volver a live edge: `onPlay` se dispara con posición cercana al final del stream

---

## Feature: Episode (Episodios)

### Qué es ÉXITO (modo API — `loadNextAutomatically` y config en plataforma):
- `nextEpisodeIncoming(id)` se dispara `nextEpisodeTime` segundos antes del fin
- El overlay de siguiente episodio aparece automáticamente
- Si el usuario no hace nada: a los 5 segundos transiciona automáticamente al siguiente
- `onNewSourceAdded(config)` se dispara al cargar el siguiente episodio
- `onPlay` se dispara con el nuevo contenido

### Qué es ÉXITO (modo manual — `nextEpisodeId` o `updateNextEpisode()`):
- `nextEpisodeIncoming(id)` se dispara 3+ segundos ANTES de que aparezca el overlay
- El integrador RECIBE `nextEpisodeIncoming` y tiene tiempo de llamar `updateNextEpisode(config)`
- El overlay SOLO aparece si `updateNextEpisode()` fue llamado
- Sin `updateNextEpisode()`: el overlay NO aparece (incluso si hay tiempo)
- `isNextOverlayVisible()` retorna false antes de la confirmación

### Qué es FALLO:
- `nextEpisodeIncoming` dispara más de una vez para el mismo episodio
- El overlay aparece en modo manual sin haber llamado `updateNextEpisode()`
- El overlay no aparece en modo API cuando debería
- La auto-transición no ocurre después de 5s en modo API
- El botón "Ver créditos" no cancela la auto-transición

### Reglas de timing:
- `nextEpisodeIncoming` se dispara cuando quedan ≤ `nextEpisodeTime` segundos
- Ventana de respuesta para `updateNextEpisode()`: desde `nextEpisodeIncoming` hasta que aparece el overlay
- Auto-transición a los 5 segundos de mostrar el overlay (si el usuario no interactúa)

---

## Feature: Audio

### Qué es ÉXITO:
- `onReady` dispara para audio live (radio) dentro de 30s
- `onReady` dispara para audio VOD (podcast) dentro de 15s
- La notificación de medios aparece cuando `playerType=AUDIO`
- `onLiveAudioCurrentSongChanged(data)` dispara cuando cambia la canción en radio live
- Background playback: la reproducción continúa al poner la app en background

### Qué es FALLO:
- `onError` dispara al cargar contenido de audio válido
- La notificación no aparece para audio en background
- `onLiveAudioCurrentSongChanged` dispara con `data=null` cuando hay metadata disponible
- La reproducción se detiene al poner la app en background (debe continuar)

### Para PlayerType AUDIO:
- No hay UI de video visible (solo barra de controles o pantalla de notificación)
- El servicio `MediastreamPlayerService` maneja el background audio
- La notificación tiene controles de transporte (play/pause/next/prev según config)

---

## Feature: Ads (IMA / VAST)

### Qué es ÉXITO:
- El ad preroll se carga y reproduce antes del contenido principal
- `onAdEvents(STARTED)` dispara al inicio del ad
- `onAdEvents(COMPLETED)` dispara al terminar el ad
- `onAdEvents(CONTENT_RESUME_REQUESTED)` dispara antes de iniciar el contenido principal
- `onPlay` del contenido principal dispara después de `ALL_ADS_COMPLETED`

### Qué es FALLO:
- Un error en el ad (`onAdErrorEvent`) provoca que el contenido principal NO se cargue
- El ad se reproduce en loop o más de una vez
- El contenido principal no inicia después de que el ad termina

### Regla crítica — Error de ad debe recuperarse:
- Si el ad falla (`onAdErrorEvent`): el SDK DEBE continuar con el contenido principal
- El flujo esperado con error de ad: `onAdErrorEvent` → `onPlay` (contenido principal)
- Si después de `onAdErrorEvent` NO dispara `onPlay`, es un bug crítico de revenue

### Reglas de callback order para ads:
```
CONTENT_PAUSE_REQUESTED → STARTED → [FIRST_QUARTILE] → [MIDPOINT] → [THIRD_QUARTILE]
→ COMPLETED → CONTENT_RESUME_REQUESTED → ALL_ADS_COMPLETED → [onPlay contenido]
```

---

## Feature: Callbacks — Orden y Garantías

### Regla de orden (VOD autoplay):
1. `playerViewReady` — SIEMPRE primero
2. `onBuffering` — puede aparecer antes o después de `onReady`
3. `onReady` — ANTES de `onPlay` siempre
4. `onPlay` — después de `onReady`

### Regla de unicidad:
- `onReady` puede disparar múltiples veces si el player se recarga (`reloadPlayer()`)
- `nextEpisodeIncoming` dispara exactamente UNA VEZ por episodio
- `onEnd` dispara exactamente UNA VEZ al terminar (no se repite)

### Regla de threading:
- Todos los callbacks se disparan en el Main Thread (`Looper.getMainLooper().thread`)
- `CallbackCaptor.firedOnMainThread("onReady")` debe retornar `true`

### Regla de multiplicación de callbacks:
- Si se registran N callbacks via `addPlayerCallback()`, cada evento se entrega a los N listeners
- No debe haber duplicación dentro de un mismo listener

### onError vs onPlaybackErrors vs onEmbedErrors:
| Callback | Cuándo | Recuperable | Qué hacer |
|----------|--------|-------------|-----------|
| `onError` | Error crítico — player no puede continuar | NO | Mostrar mensaje al usuario, reiniciar o salir |
| `onPlaybackErrors` | Error de ExoPlayer durante reproducción | A veces | Log + reintentar si `tryToReconnectOnPlaybackError=true` |
| `onEmbedErrors` | Error de la API de Mediastream (HTTP 4xx/5xx, ID no encontrado) | NO | Mostrar error, el contenido no está disponible |

---

## Feature: PiP (Picture-in-Picture)

### Jerarquía de configuración (de mayor a menor prioridad):
1. `config.pip = FlagStatus.DISABLE` → PiP siempre deshabilitado (local override)
2. `config.pip = FlagStatus.ENABLE` → PiP siempre habilitado
3. `config.pip = FlagStatus.NONE` → el SDK consulta la configuración de la plataforma API

### Qué es ÉXITO:
- `pip=ENABLE`: al llamar `startPiP()`, la ventana PiP aparece
- `pip=DISABLE`: al llamar `startPiP()`, NO aparece ninguna ventana PiP
- `onPictureInPictureModeChanged(true)` se llama desde la Activity y el SDK ajusta la UI

### Qué es FALLO:
- `startPiP()` lanza NPE cuando `pipHandler` es null (bug crítico)
- `pip=DISABLE` pero la ventana PiP aparece igualmente (la jerarquía de config falla)
- El aspect ratio del PiP no es 16:9 (limitación conocida, no un bug)

---

## Feature: DRM

### Qué es ÉXITO:
- Contenido DRM en entorno DEV carga y reproduce correctamente
- Si la licencia es inválida: `onError` dispara con mensaje descriptivo (no NPE)

### Qué es FALLO:
- La app crashea al intentar reproducir contenido DRM (NPE u excepción no manejada)
- `onError` no dispara cuando la licencia falla (el usuario se queda en pantalla negra)

### Nota v11:
- DRM se resuelve automáticamente cuando el contenido tiene DRM configurado en la plataforma
- No requiere configurar `DrmData` manualmente (no es API pública en v11)
- Solo disponible en entorno DEV (`MediastreamPlayerConfig.Environment.DEV`)

---

## Feature: Chromecast

### Qué es ÉXITO:
- `onCastAvailable(true)` cuando hay dispositivos en la red
- `onCastSessionStarted()` cuando la sesión se establece
- El contenido se reproduce en el receptor Cast
- `onCastSessionEnded()` cuando se desconecta, y el móvil retoma la reproducción

### Qué es FALLO:
- `onCastSessionStartFailed()` sin fallback al player local
- La sesión Cast se pierde sin `onCastSessionEnded` o `onCastSessionSuspended`

---

## Contenido de Prueba — IDs y Constraints

| Constante | Valor | Tipo | Ambiente | Notas |
|-----------|-------|------|----------|-------|
| `ACCOUNT_ID` | `5fbfd5b96660885379e1a129` | — | PROD | Cuenta de Mediastream |
| `Video.VOD_SHORT` | `6980e43ac0ac0673d0944d63` | VOD | PROD | Contenido corto |
| `Video.LIVE` | `5fd39e065d68477eaa1ccf5a` | Live | PROD | Stream estable |
| `Video.EPISODE_WITH_NEXT_API` | `69e14468941e7a8050a8584f` | Episode | PROD | Con siguiente en API |
| `Video.VOD_WITH_ADS` | `696bc8a832ce0ef08c6fa0ef` | VOD | PROD | Con anuncios IMA |
| `Audio.LIVE` | `632c9b23d1dcd7027f32f7fe` | Audio Live | PROD | Radio |
| `Audio.VOD` | `659c1a5cb66e51001357f22c` | Audio VOD | PROD | Podcast |
| `Reels.PLAYER_ID` | `6980ccd0654c284dc952b544` | — | PROD | Player ID para Reels |
| `Drm.LIVE_ID` | `699afcb05a41925324fa4605` | Live+DRM | DEV | Solo env DEV |
| `SRC_DIRECT_HLS` | `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` | URL directa | — | No requiere API |

### Constraints de contenido:
- `SRC_DIRECT_HLS` es el stream más confiable para CI (no depende de la API de Mediastream)
- `Video.LIVE` puede estar offline si el stream está inactivo — los tests live pueden ser flaky
- `Drm.LIVE_ID` solo funciona en `Environment.DEV` — los tests DRM no funcionan en PROD
- Los IDs con `TODO_` prefix no están configurados aún — no usarlos en tests

---

## Timeouts Recomendados por Tipo de Test

| Escenario | Timeout | Razón |
|-----------|---------|-------|
| VOD con src directo (SRC_DIRECT_HLS) | 15s | Stream público sin DRM, resuelve rápido |
| VOD con ID de plataforma | 20s | Incluye llamada HTTP a la API |
| Live con ID de plataforma | 30s | El servidor de live puede tardar en responder |
| Ad preroll (IMA) | 20s | El ad puede tardar en cargar de la CDN |
| Episodio siguiente | 30s | Incluye carga del siguiente contenido |
| DRM content | 25s | Incluye llamada a la licencia DRM |
