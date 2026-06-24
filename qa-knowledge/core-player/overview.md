# core-player — Overview

## Qué hace

`MediastreamPlayer` es la clase central del SDK de Android de Mediastream (Lightning Player). Orquesta todo el ciclo de reproducción: ExoPlayer/Media3, vista del player (`PlayerView`), ads (IMA/DAI), DVR/Live, Chromecast, Picture-in-Picture, fullscreen, analytics y ciclo de vida. Es el punto de entrada que un integrador instancia para reproducir contenido de la plataforma Mediastream (por `id` + `accountID`) o una fuente directa (`src`).

**Versión canónica del SDK (QA):** `10.0.8-alpha01` — es el binario Maven contra el que compila el proyecto QA (`io.github.mediastream:mediastreamplatformsdkandroid:10.0.8-alpha01`, `app/build.gradle.kts:76`). En ese binario `MediastreamPlayer.getVersion()` retorna el literal `"10.0.8-alpha01"`.
**Repo de código fuente local:** `D:\repos\mediastream\MediastreamPlatformSDKAndroid` (rama `master`) está en **10.0.7**, con una API prácticamente idéntica al binario 10.0.8-alpha01; las citas `file:line` de este documento provienen de leer ese código fuente 10.0.7 y son válidas para 10.0.8-alpha01.
**Namespace:** `am.mediastre.mediastreamplatformsdkandroid` · minSdk 24 · compileSdk 35 · Java/Kotlin 17.

> NOTA de versión: la versión canónica para el conocimiento de QA es **10.0.8-alpha01** (el binario que QA usa). El código fuente local que se leyó para documentar firmas es 10.0.7, una representación fiel y casi idéntica de ese binario. No existe ninguna rama "v11"; cualquier nota previa de memoria que mencionara "v11" estaba obsoleta/equivocada y no aplica.

## Archivos clave del módulo

| Archivo | Rol | Líneas |
|---------|-----|--------|
| `MediastreamPlayer.kt` | Clase principal. Implementa `LifecycleOwner`. 3 constructores públicos + uno vacío. | ~8.803 |
| `MediastreamPlayerConfig.kt` | Configuración + enums + `copy()`/`mergePersistentFrom()`/`getAdQueryString()`. | 645 |
| `MediastreamPlayerCallback.kt` | Interfaz de eventos (listener). | 47 |
| `MediastreamMiniPlayerConfig.kt` | Config del mini-player (sync service / audio). | 72 |
| `MediastreamPlayerPip.kt` | Helper de Picture-in-Picture (`enterPictureInPictureMode`, `snapshotSourceRect`). | 47 |
| `MediastreamPlayerLoadControl.kt` | `LoadControl` custom de ExoPlayer (buffers). | 226 |
| `MediastreamCustomTimer.kt` / `MediastreamICustomTaskFactory.kt` | Temporizador de tareas periódicas. | 47 / 7 |
| `CustomMediaSourceFactory.kt` | Factory de `MediaSource` (HLS/DASH/etc.). | 161 |
| `PlayerZoomGestureListener.kt` | Gesto pinch-to-zoom del video. | 121 |
| `C.kt` | Constantes. | 25 |
| `MessageEvent.kt` / `UpdateNotificationEvent.kt` | Eventos EventBus. | 4 / 2 |

## Superficie pública (API) — constructores

`MediastreamPlayer` expone **4 constructores** (`MediastreamPlayer.kt:1200-1309`):

```kotlin
// 1. Vacío (uso interno / herencia)
constructor()                                                                    // :1200

// 2. Estándar (Activity) — el más usado por integradores
constructor(context: Context, activity: Activity, playerContainer: FrameLayout?,
            config: MediastreamPlayerConfig, container: FrameLayout)             // :1202

// 3. MiniPlayer / Sync service
constructor(context: Context, config: MediastreamPlayerConfig, container: FrameLayout?,
            playerContainer: FrameLayout?, msMiniPlayerConfig: MediastreamMiniPlayerConfig?,
            isFromSyncService: Boolean = false)                                  // :1248

// 4. Con FragmentManager
constructor(context: Context, config: MediastreamPlayerConfig, container: FrameLayout?,
            playerContainer: FrameLayout?, fragmentManager: FragmentManager)     // :1303
```

Precondiciones (todos los constructores con args): `context` no nulo; en el constructor estándar `context` debe ser `Activity` (`require(context is Activity)`, `:1210`); `config` no nulo; `playerContainer` no nulo. El locale se aplica vía `LocaleHelper.setLocale(context, config.language)` (`:1215`).

## Métodos públicos por feature (verificados contra `MediastreamPlayer.kt`)

| Feature | Métodos (con `file:line`) |
|---------|---------------------------|
| Callbacks | `addPlayerCallback(callback: MediastreamPlayerCallback?)` :1069 · `getMediastreamCallbacks(): List<MediastreamPlayerCallback?>` :1196 |
| Control | `play()` :5542 · `pause()` :5576 · `forward(amount: Long)` :5603 · `backward(amount: Long)` :5630 · `seekTo(position: Long)` :5657 · `previous()` :5679 · `changeSpeed(playbackSpeed: Float)` :8238 |
| Carga / recarga | `reloadPlayer(config)` :5728 · `reloadPlayerForNextAndPrevious(config)` :5685 · `mergeConfigForReload(previousConfig, newConfig)` (open) :5719 · `updateMsConfig(config)` :8242 · `updateNextEpisode(config: MediastreamPlayerConfig)` :5241 · `playNext()` :4910 · `playPrev()` :4933 · `handleNextButtonClick()` :4887 |
| Estado / getters | `isPlaying(): Boolean` :5276 · `isBuffering(): Boolean` :5280 · `isVodCase(): Boolean` :6594 · `getCurrentPosition(): Long` :5985 · `getDuration(): Long` :5995 · `getContentDuration(): Long` :8569 · `getCurrentUrl(): String?` :4841 · `getCurrentVideoPlayingFormat(): String?` :4845 · `getCurrentMediaConfig(): MediastreamPlayerConfig?` :4849 · `getMediaTitle(): String` :5304 · `getMediaPoster(): String` :5308 · `getResolution(): String` :4817 · `getBitrate(): Int` :5951 · `getBandwidth(): Long` :5975 · `getHeight(): Int` :5941 · `getVersion(): String?` :7799 |
| Fullscreen | `enterFullscreen()` :4827 · `exitFullscreen()` :4832 |
| PiP | `startPiP()` (@RequiresApi O) :1089 · `onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean)` :1113 |
| Sesión brillo/volumen | `getSessionBrightness(): Float?` :1183 · `setSessionBrightness(brightness: Float)` :1184 · `getSessionVolume(): Float?` :1185 · `setSessionVolume(volume: Float)` :1186 |
| Menús / UI | `showSettingsMenu()` :6226 · `showSubtitleAudioMenuForTV()` :6273 · `dismissButton()` :8497 · `showDaiClickerView(show: Boolean)` :7675 |
| Cast | `showChromeCastDialog()` :8089 · `SendCurrentItemToCast()` :8093 · `currentRemoteMediaClient(): RemoteMediaClient?` :8097 |
| TV / dispositivo | `handleTVKeyEvent(keyCode: Int, event: KeyEvent): Boolean` :4135 · `isAndroidTV(context: Context): Boolean` :1017 · `isFireTV(): Boolean` :1027 · `isSmartphoneOrTablet(context: Context): Boolean` :1022 · `getDeviceType(context: Context): String` :1031 |
| Ciclo de vida / recursos | `releasePlayer()` :6025 · `releaseAdsLoader()` :6005 · `RetryNetworkConnection()` :7905 · `runOnUiThread(runnable: Runnable)` :7915 |
| IDs de sesión | `getPBId(): String?` :4857 · `getSId(): String?` :4861 · `getUId(): String?` :4869 · `getPPID(): String?` :4869 · `getCookieStore(): CookieStore?` :4853 |
| DVR (ver módulo dvr-live) | `switchToDvr(startTime, endTime?=null)` :7285 · `switchToDvrByOffset(secondsAgo, durationSeconds?=null)` :7364 · `switchToLive()` :7376 · `seekInDvr(positionMs)` :7445 · `seekBackward(seekBackMs=10_000)` :7467 · `seekForward(seekForwardMs=10_000)` :7555 · `isInDvrMode(): Boolean` :7401 · `getCurrentDvrPosition(): Long` :7411 · `getDvrDuration(): Long` :7421 · `getDvrWindowDurationSeconds(): Int` :7435 |
| Acceso media3 | `getCurrentMsPlayer(): Player?` :8276 · `getPlayerView(): PlayerView?` :8284 · `getMediaItem(): MediaItem?` :8580 · `getMediaMetaData(): MediaMetadata?` :8576 · `getPlayerContext(): Context?` :4837 · **propiedad pública** `var msPlayer: Player? = null` :210 |
| Android Auto | `setPlayer(controller: MediaController, isRequiredToCallAPI: Boolean = false)` :8249 |

**Clases internas notables:** `MediaInfoConfig(id: String, type: VideoTypes, format: AudioVideoFormat)` (`:1048`); controlador de timeline/DVR (`setupEnhancedDvrTimeline()` :2731, etc.).

## Flujo de inicialización

```
MediastreamPlayer(context, activity, playerContainer, config, container)
    ↓ LocaleHelper.setLocale(context, config.language)        [aplica idioma de UI]
    ↓ infla player_view_layout (o customPlayerView)
    ↓ MediastreamPersistentCookieStore + CookieManager        [sesión/DRM]
    ↓ lifecycleRegistry.currentState = INITIALIZED
    ↓ callSetupConfig()  ──► (si tiene id) API getMediaInfo → ConfigMain → CreateMediaItem
    ↓ initExoplayer()  /  initZoomGesture()  /  applyEdgeToEdgeInsets()
    ↓ callbacks: onReady → (autoplay) onPlay
addPlayerCallback(cb)  ─► el integrador recibe eventos vía MediastreamPlayerCallback
...
releasePlayer()  ─► libera ExoPlayer, ads loader, recursos
```

## Modos de carga de contenido

- **Por plataforma:** `config.id` + `config.accountID` (+ opcional `accessToken`, `environment`). El SDK llama a la API `getMediaInfo` y resuelve fuente, DRM, ads, subtítulos y metadatos.
- **Fuente directa:** `config.src` (URL HLS/DASH/MP4/etc. según `videoFormat`).
- **Tipos:** `VideoTypes.LIVE` / `VOD` / `EPISODE`.
- **Tipo de player:** `PlayerType.AUDIO` / `VIDEO` (default `VIDEO`).

## Interacciones con otros módulos

- **dvr-live:** los métodos `switchToDvr*`, `seekBackward/Forward`, `isInDvrMode`, `getDvrDuration` viven en esta clase pero conceptualmente pertenecen al feature DVR. Ver `qa-knowledge/dvr-live/`.
- **ads (IMA/DAI):** `releaseAdsLoader()`, `showDaiClickerView()`, `getAdQueryString()`/`ensureDAITagParamsFallback()` (en config). Conocido: IMA retiene `Activity` (ver defects).
- **analytics:** `MediastreamPlayerCollector` (Mediastream), Comscore y Konodrac se conectan vía la config (`trackEnable`, `profileID`, `customerID`, `konodracChannel`, `youboraExtraParams`).
- **cast (Chromecast):** 10 callbacks `onCastSession*` en la interfaz + `showChromeCastDialog()`.
- **service/background:** `MediastreamPlayerService` y `MediastreamPlayerServiceWithSync` usan el constructor con `MediastreamMiniPlayerConfig` e `isFromSyncService`.
