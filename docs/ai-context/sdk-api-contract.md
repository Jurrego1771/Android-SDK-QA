# SDK API Contract — Mediastream Platform SDK Android v11.0.0-alpha.01

> **Fuente de verdad:** código fuente del SDK en `D:\repos\mediastream\MediastreamPlatformSDKAndroid`
> y compilación real contra `io.github.mediastream:mediastreamplatformsdkandroid:11.0.0-alpha.01`.
> Este archivo es la referencia para generación de tests — no inventar firmas o comportamientos.

---

## 1. MediastreamPlayer — Constructor

El constructor usado en este proyecto (con FragmentManager):

```kotlin
MediastreamPlayer(
    context: Context,               // Activity context
    config: MediastreamPlayerConfig,
    container: FrameLayout?,        // contenedor raíz (para fullscreen)
    playerContainer: FrameLayout?,  // contenedor del player view
    fragmentManager: FragmentManager
)
```

**Contrato de construcción:**
- El player se inicializa SINCRÓNICAMENTE pero la carga de contenido es ASÍNCRONA
- Si `config.id` está presente, hace una llamada a la API de Mediastream para resolver la URL
- Si `config.src` está presente, omite la API y usa la URL directamente
- Si `config.autoplay = true` (default), inicia la reproducción automáticamente tras la carga

---

## 2. MediastreamPlayer — Propiedades Públicas

| Propiedad | Tipo | Descripción | Puede ser null |
|-----------|------|-------------|----------------|
| `msPlayer` | `Player?` | Instancia subyacente de ExoPlayer (Media3). Permite seekTo, currentPosition, isPlaying, etc. | Sí — null si player no inicializado |
| `autoplay` | `Boolean` | Si la reproducción debe iniciar automáticamente | No |
| `castEnable` | `Boolean` | Si el soporte Chromecast está habilitado | No |
| `prerollPlayerForSsai` | `Player?` | Player de preroll para SSAI (anuncios server-side) | Sí |

---

## 3. MediastreamPlayer — Métodos Públicos

### Registro de callbacks
```kotlin
fun addPlayerCallback(callback: MediastreamPlayerCallback?)
fun getMediastreamCallbacks(): List<MediastreamPlayerCallback?>
```
- Se pueden registrar múltiples callbacks
- Un callback registrado no se puede des-registrar actualmente (no hay removePlayerCallback)

### Control de reproducción
```kotlin
fun play()                          // Inicia/reanuda reproducción
fun pause()                         // Pausa reproducción
fun forward(amount: Long)           // Avanza [amount] milisegundos
fun backward(amount: Long)          // Retrocede [amount] milisegundos
fun seekTo(position: Long)          // Seek a posición en milisegundos absolutos
fun next()                          // Pasa al siguiente (operator overload)
fun previous()                      // Pasa al anterior
fun changeSpeed(playbackSpeed: Float) // Cambia velocidad (0.5, 1.0, 1.5, 2.0)
```

### Navegación de episodios
```kotlin
fun handleNextButtonClick()         // Simula click en botón next
fun playNext()                      // Carga y reproduce siguiente episodio
fun playPrev()                      // Carga y reproduce episodio anterior
fun updateNextEpisode(config: MediastreamPlayerConfig) // Confirma el siguiente episodio (modo manual)
fun isNextOverlayVisible(): Boolean // True si el overlay de siguiente episodio está visible
fun reloadPlayer(config: MediastreamPlayerConfig)      // Recarga con nueva config
fun reloadPlayerForNextAndPrevious(config: MediastreamPlayerConfig)
```

**IMPORTANTE:** `updateNextEpisode()` recibe `MediastreamPlayerConfig`, NO un String de ID.

### Estado del player
```kotlin
fun isPlaying(): Boolean            // True si está reproduciendo activamente
fun isBuffering(): Boolean          // True si está en buffering
fun isVodCase(): Boolean            // True si el tipo es VOD
fun getCurrentPosition(): Long      // Posición actual en milisegundos
fun getDuration(): Long             // Duración total en milisegundos
fun getContentDuration(): Long      // Duración del contenido sin ads
fun getFixedCurrentTime(): Long     // Posición para DVR (offset corregido)
fun getCurrentUrl(): String?        // URL actual del stream
fun getCurrentVideoPlayingFormat(): String? // Formato actual ("hls", "mp4", etc.)
fun getCurrentMediaConfig(): MediastreamPlayerConfig? // Config activa
fun getResolution(): String         // Resolución actual ("1920x1080")
fun getScreenResolution(): String?  // Resolución de pantalla
fun getHeight(): Int                // Alto del video en px
fun getBitrate(): Int               // Bitrate actual en bps
fun getBandwidth(): Long            // Ancho de banda disponible en bps
fun getVersion(): String?           // Versión del SDK
```

### UI y controles
```kotlin
fun enterFullscreen()               // Entra a pantalla completa programáticamente
fun exitFullscreen()                // Sale de pantalla completa
fun startPiP()                      // Inicia Picture-in-Picture
fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) // Debe llamarse desde Activity
fun showSettingsMenu()              // Muestra menú de calidad/idioma
fun showSubtitleAudioMenuForTV()    // Menú subtítulos/audio para TV
fun showDaiClickerView(show: Boolean) // Muestra/oculta clicker de ads DAI
fun dismissButton()                 // Activa el botón dismiss
```

### Brillo y volumen
```kotlin
fun getSessionBrightness(): Float?  // Brillo actual (0.0 - 1.0), null si no configurado
fun setSessionBrightness(brightness: Float) // Establece brillo de sesión
fun getSessionVolume(): Float?      // Volumen actual (0.0 - 1.0)
fun setSessionVolume(volume: Float) // Establece volumen de sesión
```

### Chromecast
```kotlin
fun showChromeCastDialog()          // Abre selector de dispositivos Cast
fun currentRemoteMediaClient(): RemoteMediaClient? // Cliente Cast activo
fun updateMetadataFromAPI()         // Actualiza metadata en el receptor Cast
```

### DVR
```kotlin
fun setupEnhancedDvrTimeline()      // Configura la timeline DVR extendida
fun cleanupDvrResources()           // Libera recursos DVR
fun removeDvrQueryKeys(url: String?): String // Limpia params DVR de una URL
```

### Ciclo de vida
```kotlin
fun releasePlayer()                 // Libera todos los recursos del player — llamar en onDestroy
fun releaseAdsLoader()              // Libera el AdsLoader de IMA
fun runOnUiThread(runnable: Runnable) // Ejecuta en el main thread
fun handleTVKeyEvent(keyCode: Int, event: KeyEvent): Boolean // Maneja eventos de teclado TV
fun restartControllerAutoHideIfPaused() // Reinicia el autohide de controles
```

### Inicialización
```kotlin
fun isFirstInitialization(): Boolean // True si es la primera inicialización
fun markAsInitialized()             // Marca como inicializado
```

### Static / Companion
```kotlin
MediastreamPlayer.isAndroidTV(context: Context): Boolean
MediastreamPlayer.isSmartphoneOrTablet(context: Context): Boolean
MediastreamPlayer.isFireTV(): Boolean
MediastreamPlayer.getDeviceType(context: Context): String // "smart-tv", "mobile", etc.
MediastreamPlayer.getEMBED_HOST(): String
```

---

## 4. MediastreamPlayerConfig — Propiedades

### Identificación de contenido (OBLIGATORIO uno de los dos)
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `id` | `String?` | null | ID del contenido en plataforma Mediastream |
| `src` | `String?` | null | URL directa del stream (omite la API) |
| `accountID` | `String?` | null | ID de cuenta Mediastream |
| `playerId` | `String?` | null | ID del player configurado en plataforma (requerido para Reels) |

### Tipo de contenido
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `type` | `VideoTypes` | `VOD` | Tipo: `LIVE`, `VOD`, `EPISODE` |
| `playerType` | `PlayerType` | `DEFAULT` | `AUDIO`, `VIDEO`, `DEFAULT` |
| `videoFormat` | `AudioVideoFormat` | `DEFAULT` (HLS) | `DASH`, `MP4`, `M4A`, `MP3`, `ICECAST`, `DEFAULT` |
| `environment` | `Environment` | `PRODUCTION` | `DEV`, `PRODUCTION` |

### Reproducción
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `autoplay` | `Boolean` | `true` | Inicia reproducción automáticamente al cargar |
| `startAt` | `Int` | `-1` | Tiempo de inicio en segundos (-1 = desde el principio) |
| `loop` | `Boolean` | `false` | Repite al terminar |
| `showControls` | `Boolean` | `true` | Muestra controles de reproducción |
| `needReload` | `Boolean` | `false` | Fuerza recarga de contenido |
| `automaticallyReconect` | `Boolean` | `true` | Reconecta automáticamente en streams live |
| `tryToReconnectOnPlaybackError` | `Boolean` | `false` | Reintenta en errores de playback |

### DVR (solo para Live)
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `dvr` | `Boolean` | `false` | Habilita modo DVR |
| `windowDvr` | `Int` | `0` | Ventana de tiempo DVR en segundos |
| `dvrStart` | `String?` | null | Timestamp ISO de inicio del DVR |
| `dvrEnd` | `String?` | null | Timestamp ISO de fin del DVR |

### Episodios
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `loadNextAutomatically` | `Boolean` | `false` | Carga automáticamente el siguiente episodio |
| `nextEpisodeId` | `String?` | null | ID del siguiente episodio (modo manual) |
| `nextEpisodeTime` | `Int?` | null | Segundos antes del final para mostrar overlay de siguiente episodio |
| `nextPrevAutomatically` | `Boolean` | `false` | Permite navegación prev/next automática |

### Feature flags (FlagStatus: ENABLE, DISABLE, NONE)
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `pip` | `FlagStatus` | `NONE` | Control de PiP (NONE = decide la API/plataforma) |
| `showSubtitles` | `FlagStatus` | `NONE` | Control de subtítulos |
| `muteAds` | `FlagStatus` | `NONE` | Mutear anuncios |
| `pauseOnScreenClick` | `FlagStatus` | `DISABLE` | Pausa al tocar la pantalla |
| `speedInControlBar` | `FlagStatus` | `NONE` | Botón de velocidad en la barra de controles |
| `showTitle` | `FlagStatus` | `ENABLE` | Muestra el título del contenido |

### Publicidad
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `adURL` | `String?` | null | URL del tag VAST/VMAP para anuncios |
| `adCustomAtributtes` | `ArrayList<AdCustomAtributte>?` | vacío | Atributos personalizados para anuncios |
| `googleImaPpid` | `String?` | null | PPID para Google IMA |
| `adPreloadTimeoutMs` | `Long?` | null | Timeout de precarga de anuncios en ms |
| `vastLoadTimeoutMs` | `Int?` | null | Timeout de carga VAST en ms |
| `adTagParametersForDAI` | `MutableMap<Util.AdTagParameter, String>` | vacío | Parámetros para DAI |

### UI y visual
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `showDismissButton` | `Boolean` | `false` | Muestra botón de cerrar/minimizar |
| `showFullScreenButton` | `Boolean` | `true` | Muestra botón de pantalla completa |
| `initPlayerOnFullScreenMode` | `Boolean` | `false` | Inicia en modo fullscreen |
| `initialHideController` | `Boolean` | `true` | Oculta controles al inicio |
| `customPlayerView` | `PlayerView?` | null | Vista personalizada del player |
| `baseColor` | `Int` | `-1` | Color base de la UI (-1 = default) |
| `language` | `Language` | `ENGLISH` | Idioma UI: `ENGLISH`, `SPANISH`, `PORTUGUESE` |
| `enablePlayerZoom` | `Boolean` | `false` | Habilita zoom con gestos |
| `forceBackPressedWhenFullScreen` | `Boolean` | `false` | Fuerza comportamiento back en fullscreen |

### Calidad y formato
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `maxProfile` | `String?` | null | Perfil de resolución máxima ("720p", "1080p") |
| `denyAdaptativeMode` | `Boolean` | `false` | Desactiva ABR (calidad adaptativa) |
| `isMaxResolutionBasedOnScreenSize` | `Boolean` | `true` | Limita resolución al tamaño de pantalla |
| `isForceHighestSupportedBitrateEnabled` | `Boolean` | `true` | Fuerza el bitrate más alto soportado |
| `playlistVideoFormat` | `AudioVideoFormat?` | null | Formato para playlists |

### Notificaciones (para audio background)
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `notificationHasNext` | `Boolean` | `true` | Muestra botón siguiente en notificación |
| `notificationHasPrevious` | `Boolean` | `true` | Muestra botón anterior en notificación |
| `notificationIconUrl` | `Int` | exo_notification_small_icon | Icono pequeño (resource ID) |
| `notificationImageUrl` | `String` | URL default Mediastream | URL de imagen del artwork |
| `notificationColor` | `Int` | `-1` | Color de la notificación |
| `notificationSongName` | `String` | `""` | Nombre de la canción |
| `notificationAlbumName` | `String` | `""` | Nombre del álbum |
| `fillAutomaticallyAudioNotification` | `Boolean` | `true` | Rellena notificación con metadata de la API |

### Audio y streaming
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `volume` | `Float?` | null | Volumen inicial (0.0 - 1.0) |
| `subtitles` | `Boolean` | `false` | Habilita subtítulos |
| `castAvailable` | `Boolean` | `false` | Habilita soporte Chromecast |
| `tryToGetMetadataFromLiveWhenAudio` | `Boolean` | `true` | Obtiene metadata SSE en audio live |
| `trackEnable` | `Boolean` | `true` | Habilita tracking de analíticas |
| `isDebug` | `Boolean` | `false` | Logs de debug |
| `protocol` | `String` | `"https"` | Protocolo de red |

### Analytics
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `appName` | `String?` | null | Nombre de la app (para analytics) |
| `appVersion` | `String?` | null | Versión de la app |
| `distributorId` | `String?` | null | ID del distribuidor |
| `customerID` | `String?` | null | ID del cliente |
| `analyticsCustom` | `String?` | null | Datos custom de analytics |
| `youboraExtraParams` | `Array<String?>` | 20 nulls | Parámetros extra para Youbora |

### DRM
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `drmData` | `DrmData?` | null | Datos DRM (en v11 el SDK resuelve DRM automáticamente por ID) |

**Nota v11:** `DrmData` es una clase anidada pero no expuesta públicamente en v11. El DRM se resuelve automáticamente cuando se usa `id` con contenido DRM en la plataforma.

### Acceso seguro
| Propiedad | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `accessToken` | `String?` | null | Token de acceso para contenido protegido |
| `referer` | `String?` | null | Referer para validación de origen |

### Métodos de MediastreamPlayerConfig
```kotlin
fun copy(): MediastreamPlayerConfig          // Copia profunda de la configuración
fun addAdCustomAttribute(key: String, value: String) // Agrega atributo custom de ad
fun getAdQueryString(platform: String, baseUrl: String?): String? // Genera query string de ads
fun getPPIDFromAdTagParameters(): String?    // Obtiene PPID de los atributos
```

---

## 5. MediastreamPlayerCallback — Todos los Callbacks

> **CRÍTICO v11:** Esta interfaz NO tiene implementaciones default. TODOS los métodos deben implementarse.
> No se puede crear un anonymous object parcial sin compilar error.

### Ciclo de vida del player
```kotlin
fun playerViewReady(msplayerView: PlayerView?)
// → Vista del player lista para renderizar. msplayerView puede ser null.
// → Timing: primero en dispararse, antes de cargar contenido

fun onReady()
// → El player completó la inicialización y está listo para reproducir
// → Para VOD con autoplay=true: dispara antes de onPlay
// → Para Live: dispara cuando el buffer es suficiente para reproducir

fun onPlay()
// → La reproducción ha iniciado o reanudado
// → Para autoplay=true: dispara después de onReady
// → Puede dispararse múltiples veces (cada resume)

fun onPause()
// → La reproducción fue pausada explícitamente
// → NO dispara en buffering (eso es onBuffering)

fun onBuffering()
// → El player está cargando datos (buffering)
// → Puede dispararse múltiples veces durante la reproducción
// → La reproducción se reanuda automáticamente cuando el buffer sea suficiente

fun onEnd()
// → La reproducción llegó al final del contenido
// → Solo para VOD y EPISODE — Live no tiene fin
// → Si loop=true: no se dispara (reinicia automáticamente)

fun onPlayerClosed()
// → El player fue cerrado por error irrecuperable o acción del usuario

fun onPlayerReload()
// → El player fue recargado (llamado reloadPlayer() o reconexión automática)
```

### Errores
```kotlin
fun onError(error: String?)
// → Error CRÍTICO que detiene la reproducción completamente
// → El player no puede recuperarse sin acción del usuario
// → error: mensaje de texto del error, puede ser null

fun onPlaybackErrors(error: JSONObject?)
// → Errores de reproducción NO críticos o recuperables
// → El player puede continuar o reintentar
// → error: JSONObject con detalles técnicos del error de ExoPlayer

fun onEmbedErrors(error: JSONObject?)
// → Errores relacionados con la configuración del embed/API de Mediastream
// → Ej: ID no encontrado, cuenta inválida, respuesta malformada de la API
// → error: JSONObject con detalles del error de la plataforma
```

### Navegación
```kotlin
fun onNext()
// → Se presionó o se activó el botón de siguiente contenido
// → Para episodios: indica que el usuario quiere ir al siguiente

fun onPrevious()
// → Se presionó el botón de contenido anterior

fun nextEpisodeIncoming(nextEpisodeId: String)
// → CRÍTICO para modo manual de episodios
// → Se dispara cuando falta [nextEpisodeTime] segundos para que termine el episodio
// → nextEpisodeId: ID del siguiente episodio (puede ser el configurado en API o el manual)
// → En este momento el integrador DEBE llamar player.updateNextEpisode(config) si está en modo manual
// → Se dispara EXACTAMENTE UNA VEZ por episodio
// → Si no se llama updateNextEpisode() en modo manual, el overlay NO aparece

fun onNewSourceAdded(config: MediastreamPlayerConfig)
// → Se cargó una nueva fuente de contenido (nuevo episodio, nuevo stream)
// → config: la configuración del nuevo contenido

fun onLocalSourceAdded()
// → Se cargó contenido descargado localmente
```

### Fullscreen
```kotlin
fun onFullscreen(enteredForPip: Boolean)
// → El player entró en modo fullscreen
// → enteredForPip: true si el fullscreen fue iniciado como preparación para PiP
// → FIRMA v11 — fue onFullscreen() sin parámetros en versiones anteriores

fun offFullscreen()
// → El player salió del modo fullscreen
```

### Anuncios (IMA / Google Ads)
```kotlin
fun onAdEvents(type: AdEvent.AdEventType)
// → Evento de ad IMA. AdEventType incluye:
//   STARTED, PAUSED, RESUMED, COMPLETED, SKIPPED,
//   FIRST_QUARTILE, MIDPOINT, THIRD_QUARTILE,
//   ALL_ADS_COMPLETED, CONTENT_RESUME_REQUESTED,
//   CONTENT_PAUSE_REQUESTED, AD_BREAK_STARTED, AD_BREAK_ENDED
// → Se dispara para cada cambio de estado del ad

fun onAdErrorEvent(error: AdError)
// → Error al reproducir un anuncio
// → El SDK continúa con el contenido principal después del error
// → error: AdError con errorCode y message
```

### Chromecast / Google Cast
```kotlin
fun onCastAvailable(state: Boolean?)
// → state=true: hay dispositivos Cast disponibles en la red
// → state=false: no hay dispositivos Cast disponibles

fun onCastSessionStarting()   // Conectando al receptor Cast
fun onCastSessionStarted()    // Sesión Cast establecida — reproducción en TV
fun onCastSessionStartFailed() // Falló la conexión al Cast
fun onCastSessionEnding()     // Cerrando sesión Cast
fun onCastSessionEnded()      // Sesión Cast cerrada — reproducción vuelve al móvil
fun onCastSessionResuming()   // Retomando sesión Cast existente
fun onCastSessionResumed()    // Sesión Cast retomada exitosamente
fun onCastSessionResumeFailed() // Falló al retomar sesión Cast
fun onCastSessionSuspended()  // Sesión Cast suspendida (app en background)
```

### Configuración dinámica
```kotlin
fun onConfigChange(config: MediastreamMiniPlayerConfig?)
// → La configuración del mini player cambió
// → config: nueva configuración del mini player, puede ser null
```

### Audio en vivo
```kotlin
fun onLiveAudioCurrentSongChanged(data: JSONObject?)
// → La canción actual del stream de audio en vivo cambió
// → Se activa vía SSE (Server-Sent Events) del endpoint de metadata
// → data: JSONObject con campos "title", "artist", "album", "artwork", puede ser null
// → Solo activo cuando tryToGetMetadataFromLiveWhenAudio=true y playerType=AUDIO
```

### UI
```kotlin
fun onDismissButton()
// → Se presionó el botón de dismiss/cerrar
// → Solo si showDismissButton=true en la config
```

---

## 6. MediastreamPlayer — Enums de Config

```kotlin
// VideoTypes
MediastreamPlayerConfig.VideoTypes.LIVE      // "live-stream"
MediastreamPlayerConfig.VideoTypes.VOD       // "video"
MediastreamPlayerConfig.VideoTypes.EPISODE   // "episode"

// AudioVideoFormat
MediastreamPlayerConfig.AudioVideoFormat.DASH    // "mpd"
MediastreamPlayerConfig.AudioVideoFormat.MP4     // "mp4"
MediastreamPlayerConfig.AudioVideoFormat.M4A     // "mp4"
MediastreamPlayerConfig.AudioVideoFormat.MP3     // "mp3"
MediastreamPlayerConfig.AudioVideoFormat.ICECAST // "icecast"
MediastreamPlayerConfig.AudioVideoFormat.DEFAULT // "hls" (por defecto)

// PlayerType
MediastreamPlayerConfig.PlayerType.AUDIO    // "audio"
MediastreamPlayerConfig.PlayerType.VIDEO    // "video"
MediastreamPlayerConfig.PlayerType.DEFAULT  // "video"

// Environment
MediastreamPlayerConfig.Environment.DEV         // "https://develop.mdstrm.com"
MediastreamPlayerConfig.Environment.PRODUCTION  // "https://mdstrm.com"

// FlagStatus
MediastreamPlayerConfig.FlagStatus.ENABLE
MediastreamPlayerConfig.FlagStatus.DISABLE
MediastreamPlayerConfig.FlagStatus.NONE     // El SDK decide (basado en config de plataforma)

// Language
MediastreamPlayerConfig.Language.ENGLISH    // "en"
MediastreamPlayerConfig.Language.SPANISH    // "es"
MediastreamPlayerConfig.Language.PORTUGUESE // "pt"
```

---

## 7. Contratos de Comportamiento (Timing y Orden)

### Secuencia de callbacks en VOD con autoplay=true:
```
playerViewReady → onBuffering → onReady → onPlay → [onBuffering → onPlay]* → onEnd
```

### Secuencia de callbacks en VOD con autoplay=false:
```
playerViewReady → onBuffering → onReady → [usuario llama play()] → onPlay
```

### Secuencia en carga de episodio siguiente:
```
... → nextEpisodeIncoming(id) → [updateNextEpisode(config)] → onEnd → onNewSourceAdded(config) → onPlay
```

### Secuencia con error crítico:
```
playerViewReady → onError(msg) → onPlayerClosed
```
o
```
onPlay → [durante reproducción] → onError(msg) → onPlayerClosed
```

### Secuencia de ad preroll (IMA):
```
onReady → onAdEvents(CONTENT_PAUSE_REQUESTED) → onAdEvents(STARTED)
→ onAdEvents(FIRST_QUARTILE) → onAdEvents(MIDPOINT) → onAdEvents(THIRD_QUARTILE)
→ onAdEvents(COMPLETED) → onAdEvents(CONTENT_RESUME_REQUESTED)
→ onAdEvents(ALL_ADS_COMPLETED) → onPlay [contenido principal]
```

### Threading:
- Todos los callbacks se disparan en el **Main Thread (UI Thread)**
- `msPlayer.seekTo()`, `msPlayer.currentPosition` son seguros desde cualquier thread
- Las operaciones en el player interno deben hacerse desde el Main Thread

---

## 8. Propiedades que NO EXISTEN en v11 (errores de compilación)

- `notificationTitle` — eliminado
- `notificationDescription` — eliminado
- `notificationHasPrev` — eliminado (solo existe `notificationHasNext`)
- `MediastreamPlayerConfig.DrmData` — no expuesta públicamente como clase

---

## 9. Requisitos de Build para compilar con el SDK

```kotlin
// build.gradle.kts (app)
compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
// Dependencia obligatoria:
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
// Java 17 mínimo (no 11)
// AGP 9.0+: No usar plugin kotlin-android, usar android { kotlin { jvmToolchain(17) } }
```
