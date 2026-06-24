# core-player — Business Rules

> Cada regla cita evidencia en el código fuente (`file:line`). Donde un comportamiento no se pudo confirmar leyendo el código, se marca como `(no verificado)`.

## Construcción e inicialización

**BR-CORE-001** — El constructor estándar exige `Activity`
El constructor `(context, activity, playerContainer, config, container)` valida `require(context is Activity)` y lanza `IllegalArgumentException` si no lo es (`MediastreamPlayer.kt:1210`). Además `context`, `config` y `playerContainer` no pueden ser nulos (`:1209-1212`). Los constructores de mini-player y FragmentManager tienen esa línea de `require(Activity)` comentada (`:1257`, `:1311`) — aceptan `Context` no-Activity.

**BR-CORE-002** — El idioma de la UI se fija en construcción vía `config.language`
`LocaleHelper.setLocale(context, config.language)` se llama en cada constructor con args (`:1215, :1263, :1316`). El idioma efectivo es el del `MediastreamPlayerConfig`, cuyo default es `Language.ENGLISH` (ver BR-CORE-014).

**BR-CORE-003** — `customPlayerView` reemplaza el layout inflado por defecto
Si `config.customPlayerView != null`, se usa esa `PlayerView`; si no, se infla `R.layout.player_view_layout` (o `player_view_layout_surface` en Fire OS) (`:1221-1229`). En Fire OS se usa un layout con `SurfaceView`.

**BR-CORE-004** — La cookie store es persistente por sesión/DRM
Cada construcción crea `MediastreamPersistentCookieStore(context)` y la registra como `CookieHandler` global (`:1234-1236`). Expuesta vía `getCookieStore()` (`:4853`).

## Control de reproducción

**BR-CORE-005** — `forward`/`backward` reciben milisegundos (`Long`)
Firmas reales: `forward(amount: Long)` (`:5603`) y `backward(amount: Long)` (`:5630`). El parámetro es la cantidad en ms, no segundos. `seekTo(position: Long)` es posición absoluta en ms (`:5657`).

**BR-CORE-006** — `changeSpeed` delega directamente en ExoPlayer
`changeSpeed(playbackSpeed: Float)` llama `msPlayer?.setPlaybackSpeed(playbackSpeed)` (`:8238-8240`). No valida el rango del valor.

**BR-CORE-007** — `getContentDuration` retorna 0 si el player no está listo
`getContentDuration()` retorna `0L` cuando `msPlayer?.playbackState != Player.STATE_READY`; si no, `msPlayer?.contentDuration ?: 0L` (`:8569-8574`). A diferencia de `getDuration()` que retorna `msPlayer?.duration ?: 0` sin chequear el estado (`:5995-6002`).

**BR-CORE-008** — Todos los getters numéricos degradan a 0 ante excepción
`getHeight()`, `getBitrate()` (vía `getVideoBitrate`), `getBandwidth()`, `getCurrentPosition()`, `getDuration()` envuelven el acceso a media3 en try/catch y retornan `0`/`0L` ante error (`:5941-6002`). Nunca lanzan al integrador.

## Recarga y persistencia de config

**BR-CORE-009** — La recarga mezcla propiedades "persistentes" del config previo
`MediastreamPlayerConfig.mergePersistentFrom(previous)` (`MediastreamPlayerConfig.kt:451-481`) mantiene del config previo: `showDismissButton`, `isDebug`, `showControls`, `showFullScreenButton`, `appHandlesWindowInsets`, `applyEdgeSafeMargins`, `showBrightnessBar`, `forceBackPressedWhenFullScreen`, `trackEnable`, `adaptResizeModeToOrientation`, `cachedRdid`, `cachedIsLat`. En cambio toma del nuevo config: `language`, `dvr`, `dvrStart`, `dvrEnd`, `startAt`. Para `volume`/`autoplay`/`customPlayerView`/`baseColor`/`customBackgroundForAudioPlayer` usa el nuevo si está presente, sino el previo.

**BR-CORE-010** — `language` SÍ cambia en recarga
A diferencia de otros flags de UI persistentes, `mergePersistentFrom` usa **el `language` del nuevo config** (`MediastreamPlayerConfig.kt:464-465`), permitiendo que `reloadPlayer(newConfig)` cambie el idioma de la UI.

**BR-CORE-011** — `updateNextEpisode` recibe un `MediastreamPlayerConfig`, no un String
Firma real: `updateNextEpisode(config: MediastreamPlayerConfig)` (`MediastreamPlayer.kt:5241`). Coincide con la nota de memoria QA: pasar un `String` no compila.

**BR-CORE-012** — `updateMsConfig` solo reasigna la referencia de config
`updateMsConfig(config)` ejecuta `msConfig = config` sin reinicializar el player (`:8242-8244`). Para aplicar cambios de fuente se requiere `reloadPlayer()`.

## Callbacks

**BR-CORE-013** — `addPlayerCallback` deduplica y entrega errores pendientes
No añade un callback ya presente (`if (!msCallbacks.contains(callback))`, `:1071`). Si hay `pendingError`/`pendigEmbedError` acumulados antes del registro, los entrega inmediatamente al registrar y los limpia (`:1074-1086`). Esto evita perder errores emitidos antes de que el integrador se suscriba.

## Configuración — defaults y enums

**BR-CORE-014** — Idioma por defecto del player es **inglés**
`var language: Language = Language.ENGLISH` (`MediastreamPlayerConfig.kt:184`). Enum `Language { ENGLISH("en"), SPANISH("es"), PORTUGUESE("pt") }` (`:63-68`). DISCREPANCIA vs i18n web (cuyo default era `es`): aquí el default es `en`.

**BR-CORE-015** — Entorno por defecto es PRODUCTION
`var environment = Environment.PRODUCTION` (`:79`). `Environment { DEV("https://develop.mdstrm.com"), PRODUCTION("https://mdstrm.com") }` (`:33-38`). Existe también `OTTApps { DEV, PRODUCTION }` con URLs de plataforma OTT para Android Auto (`:40-44`).

**BR-CORE-016** — Tipo de contenido por defecto es VOD
`var type = VideoTypes.VOD` (`:93`). `VideoTypes { LIVE("live-stream"), VOD("video"), EPISODE("episode") }`. `fromKey(key)` cae a `LIVE` si no hay match (`:21-31`).

**BR-CORE-017** — Formato por defecto es HLS
`AudioVideoFormat.DEFAULT` mapea a `"hls"` (`:46-54`). Valores: `DASH("mpd")`, `MP4("mp4")`, `M4A("mp4")`, `MP3("mp3")`, `ICECAST("icecast")`, `DEFAULT("hls")`. `var videoFormat = AudioVideoFormat.DEFAULT` (`:86`).

**BR-CORE-018** — `PlayerType` por defecto es VIDEO
`PlayerType { AUDIO("audio"), VIDEO("video"), DEFAULT("video") }` (`:56-61`). `var playerType = PlayerType.DEFAULT` (`:85`), que también es `"video"`.

**BR-CORE-019** — Flags ternarios usan `FlagStatus`
`FlagStatus { ENABLE, DISABLE, NONE }` (`:15-19`) se usa para: `muteAds=NONE`, `pauseOnScreenClick=DISABLE`, `pip=NONE`, `showSubtitles=NONE`, `speedInControlBar=NONE`, `showTitle=ENABLE` (`:153-164`). `NONE` significa "usar el valor que resuelva la plataforma/config remota".

**BR-CORE-020** — `autoplay`, `showControls` y trackeo están activos por defecto
`autoplay = true` (`:167`), `showControls = true` (`:90`), `trackEnable = true` (`:109`), `automaticallyReconect = true` (`:116`), `isMaxResolutionBasedOnScreenSize = true` (`:168`), `isForceHighestSupportedBitrateEnabled = true` (`:169`).

**BR-CORE-021** — `startAt = -1` significa "desde el inicio"
`var startAt = -1` (`:110`); el valor `-1` es el centinela de "no especificado". Tipo `Int` (ms o segundos según uso interno; el `copy()`/`merge` lo tratan como entero plano).

## DRM

**BR-CORE-022** — `DrmData` SÍ existe como clase pública anidada
`class DrmData(var drmUrl: String, var drmHeaders: Map<String, String>)` (`MediastreamPlayerConfig.kt:602`) y la propiedad `var drmData: DrmData? = null` (`:78`). Es una API normal y presente en 10.0.x (también `getDrmData`/`setDrmData` en el binario 10.0.8-alpha01). El SDK también puede resolver DRM automáticamente al cargar por `id`.

## Ads — construcción de query string

**BR-CORE-023** — `getAdQueryString` auto-inyecta ppid/rdid/idtype/is_lat
`getAdQueryString(platform: String, baseUrl: String?): String?` (`:301`) añade `mobile=true`, `platformType=<platform>` y, si no están ya en la URL ni en `adCustomAtributtes`: `ppid` (fallback `googleImaPpid` → ppid de atributos → `customerID`), `rdid`, `idtype` (`afai` en firetv, `aaid` en android/androidtv), `is_lat`. Codifica especialmente `cust_params`. Retorna `null` si `baseUrl` es null o el query queda vacío.

**BR-CORE-024** — Device IDs de ads se obtienen en background, no bloqueante
`fetchDeviceIdsAsync(context)` (`:204`) lanza un `Thread` que obtiene GAID vía `AdvertisingIdClient`, con fallback a Amazon AAID en Fire OS (`isAmazonBuild()`, `:245`). Cachea en `cachedRdid`/`cachedIsLat`. `waitForDeviceIdsCache(timeoutMs=3000)` bloquea (solo desde background) hasta cachear o timeout (`:294`).

## Versión y dispositivo

**BR-CORE-025** — `getVersion()` retorna un literal hardcodeado
`getVersion()` retorna un string literal embebido en el código (`:7799-7801`), no derivado de `BuildConfig`. En el binario QA (10.0.8-alpha01) el literal es `"10.0.8-alpha01"`; en el código fuente local 10.0.7 es `"10.0.7"`. Cambiar la versión real del SDK no actualiza este valor automáticamente.

**BR-CORE-026** — `getDeviceType` clasifica en firetv / androidtv / android
`getDeviceType(context)` retorna `"firetv"`, `"androidtv"` o `"android"` (también `"android"` como fallback) según `isFireTV()`/`isAndroidTV()`/`isSmartphoneOrTablet()` (`:1031-1038`). Este valor es el `platform` que se pasa a `getAdQueryString`.

## Ciclo de vida

**BR-CORE-027** — No hay métodos públicos onResume/onPause/onStop/onDestroy
La clase implementa `LifecycleOwner` (`:180`) y gestiona el `lifecycleRegistry` internamente. La liberación de recursos se hace explícitamente con `releasePlayer()` (`:6025`). No existen hooks públicos `onResume()`/`onDestroy()` que el integrador deba llamar (verificado: el grep de funciones públicas no los encuentra).
