# 03 — MediastreamPlayerCallback

## Descripcion

Interfaz que el cliente implementa para recibir eventos del ciclo de vida y estado del player. Se registra via `player.addPlayerCallback(callback)`.

**Multiples callbacks:** El SDK soporta multiples callbacks registrados simultaneamente (`getMediastreamCallbacks()` retorna la lista completa).

---

## Registro

```kotlin
player.addPlayerCallback(object : MediastreamPlayerCallback {
    override fun onPlay() { /* ... */ }
    override fun onPause() { /* ... */ }
    // todos los metodos con implementacion por defecto (open interface)
})
```

---

## Referencia completa de callbacks

### Ciclo de vida del player

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `playerViewReady` | `(msplayerView: PlayerView?)` | Vista del player lista e insertada en el layout. **Primero que se dispara.** |
| `onReady` | `()` | Player inicializado y listo para reproducir (buffer suficiente) |
| `onPlay` | `()` | Reproduccion iniciada o reanudada |
| `onPause` | `()` | Reproduccion pausada por el usuario |
| `onBuffering` | `()` | Player esperando datos (rebuffering) |
| `onEnd` | `()` | Contenido llego al final |
| `onError` | `(error: String?)` | Error critico irrecuperable |
| `onPlayerClosed` | `()` | Player cerrado (por error irrecuperable o accion de usuario) |
| `onPlayerReload` | `()` | Player recargado (ej: siguiente episodio) |

### Navegacion de contenido

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onNext` | `()` | Usuario presiona boton "siguiente" |
| `onPrevious` | `()` | Usuario presiona boton "anterior" |
| `nextEpisodeIncoming` | `(nextEpisodeId: String)` | Contador de siguiente episodio aparece (X segundos antes del final) |
| `onNewSourceAdded` | `(config: MediastreamPlayerConfig)` | Nueva fuente cargada (ej: episodio siguiente cargado) |
| `onLocalSourceAdded` | `()` | Fuente local (descarga offline) agregada |

### Pantalla completa

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onFullscreen` | `()` | Player entro en modo fullscreen |
| `offFullscreen` | `()` | Player salio de modo fullscreen |

### Publicidad (Ads)

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onAdEvents` | `(type: AdEvent.AdEventType)` | Evento de anuncio: STARTED, PAUSED, RESUMED, COMPLETED, CLICKED, etc. |
| `onAdErrorEvent` | `(error: AdError)` | Error al cargar o reproducir un anuncio |

**AdEvent.AdEventType valores comunes:**
- `LOADED` — anuncio cargado
- `STARTED` — anuncio comenzando
- `FIRST_QUARTILE`, `MIDPOINT`, `THIRD_QUARTILE` — progreso
- `COMPLETED` — anuncio terminado
- `PAUSED` / `RESUMED` — pausa/reanudo
- `CLICKED` — usuario hizo click
- `SKIPPED` — usuario skippeo
- `AD_BREAK_STARTED` / `AD_BREAK_ENDED` — inicio/fin del bloque de ads

### Google Cast / Chromecast

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onCastAvailable` | `(state: Boolean?)` | Dispositivos Cast disponibles en la red |
| `onCastSessionStarting` | `()` | Iniciando conexion a Cast |
| `onCastSessionStarted` | `()` | Conectado a dispositivo Cast |
| `onCastSessionEnding` | `()` | Terminando sesion Cast |
| `onCastSessionEnded` | `()` | Sesion Cast finalizada |
| `onCastSessionResumed` | `()` | Sesion Cast reanudada |

### Errores detallados

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onPlaybackErrors` | `(error: JSONObject?)` | Errores de reproduccion (pueden ser no criticos) |
| `onEmbedErrors` | `(error: JSONObject?)` | Errores de configuracion o embed (API errors) |

**Diferencia clave:**
- `onError(String?)` — error critico, el player se detiene
- `onPlaybackErrors(JSONObject?)` — error de playback, puede recuperarse
- `onEmbedErrors(JSONObject?)` — error de configuracion/API

### Audio en vivo

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onLiveAudioCurrentSongChanged` | `(data: JSONObject?)` | Cambio de cancion/metadata en stream de audio en vivo (via SSE) |

### UI / UX

| Metodo | Firma | Cuando se dispara |
|--------|-------|-------------------|
| `onDismissButton` | `()` | Usuario presiona el boton de dismiss/cerrar |
| `onConfigChange` | `(config: MediastreamMiniPlayerConfig?)` | Cambio en la configuracion del mini-player |

---

## Orden tipico de callbacks en reproduccion normal

```
playerViewReady(view)
       │
       ▼
   onReady()
       │
       ▼
    onPlay()
       │
       ▼
  onBuffering()   ← puede ocurrir multiples veces durante reproduccion
       │
       ▼
    onEnd()
```

### Con anuncios (preroll)
```
playerViewReady(view)
       │
       ▼
onAdEvents(LOADED)
       │
       ▼
onAdEvents(STARTED)
       │
       ▼
onAdEvents(COMPLETED)
       │
       ▼
   onReady()
       │
       ▼
    onPlay()
```

---

## Implementacion minima recomendada

```kotlin
player.addPlayerCallback(object : MediastreamPlayerCallback {
    override fun playerViewReady(msplayerView: PlayerView?) {
        // El player esta listo — buena oportunidad para mostrar loading
    }

    override fun onReady() {
        // Ocultar loading indicator
    }

    override fun onPlay() {
        // Actualizar UI (boton pause, etc.)
    }

    override fun onPause() {
        // Actualizar UI (boton play, etc.)
    }

    override fun onEnd() {
        // Navegar a pantalla siguiente o mostrar replay
    }

    override fun onError(error: String?) {
        // Mostrar mensaje de error al usuario
        showError(error ?: "Error desconocido")
    }

    override fun onEmbedErrors(error: JSONObject?) {
        // Error de configuracion — generalmente el contenido no existe
        handleEmbedError(error)
    }

    override fun onPlaybackErrors(error: JSONObject?) {
        // Error de playback — puede ser transitorio
        logPlaybackError(error)
    }
})
```

---

## Testing — Escenarios a cubrir

### Callbacks de ciclo de vida
- [ ] `playerViewReady` se dispara antes que `onReady`
- [ ] `onReady` se dispara antes que `onPlay` cuando autoplay=true
- [ ] `onPlay` se dispara al reanudar despues de pausa
- [ ] `onPause` se dispara al pausar
- [ ] `onBuffering` se dispara en rebuffering
- [ ] `onEnd` se dispara al terminar el contenido
- [ ] `onError` se dispara con error critico
- [ ] `onPlayerClosed` se dispara tras error irrecuperable

### Callbacks de ads
- [ ] `onAdEvents(STARTED)` se dispara al iniciar preroll
- [ ] `onAdEvents(COMPLETED)` se dispara al terminar el ad
- [ ] `onAdErrorEvent` se dispara cuando el ad tag falla
- [ ] Secuencia correcta: AD_STARTED → AD_COMPLETED → onReady → onPlay

### Callbacks de navegacion
- [ ] `nextEpisodeIncoming` se dispara N segundos antes del final
- [ ] `onNext` se dispara al presionar el boton siguiente
- [ ] `onNewSourceAdded` se dispara al cargar el siguiente episodio

### Callbacks de Cast
- [ ] `onCastAvailable(true)` cuando hay dispositivos en la red
- [ ] Ciclo completo: Starting → Started → Ending → Ended

### Multiples callbacks
- [ ] Dos callbacks registrados ambos reciben el mismo evento
- [ ] Un callback null no produce NullPointerException

---

*Feature: 03-callbacks | SDK v9.9.0 | 2026-04-16*
