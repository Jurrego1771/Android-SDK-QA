---
name: core-player-api
description: API real verificada de MediastreamPlayer/Config/Callback en SDK 10.0.8-alpha01 (binario QA) / 10.0.7 (fuente local) — NO existe v11
metadata:
  type: project
---
VERSIÓN (resuelto 2026-06-12): la versión canónica para QA es **10.0.8-alpha01** — es el binario Maven contra el que compila el proyecto QA (`SDK-Android-Qa/app/build.gradle.kts:76`, `io.github.mediastream:mediastreamplatformsdkandroid:10.0.8-alpha01`). El repo de código fuente local `D:\repos\mediastream\MediastreamPlatformSDKAndroid` (rama master) está en **10.0.7** con API casi idéntica, así que leer 10.0.7 para firmas es válido. **NO existe ninguna rama "v11"**: la antigua memoria `sdk_v11_real_api` era un snapshot experimental/erróneo, ya corregido. Las notas "v11" están deprecadas/falsas. Verificado contra el .aar 10.0.8-alpha01: `DrmData` EXISTE (getDrmData/setDrmData, DrmData(drmUrl, drmHeaders)); `MediastreamPlayerCallback` tiene DefaultImpls (onFullscreen(enteredForPip=false), nextEpisodeIncoming, nextEpisodeLoadRequested son default, NO es abstracta total); `notificationDescription` y `notificationHasPrevious` EXISTEN; `notificationTitle` NO existe (usa `notificationSongName`); `getVersion()` retorna literal hardcodeado = "10.0.8-alpha01" en QA, "10.0.7" en fuente.

Verificado leyendo el código 10.0.7. Citas file:line en `mediastreamplatformsdkandroid/src/main/java/am/mediastre/mediastreamplatformsdkandroid/`.

## MediastreamPlayer.kt (8803 líneas) — `class MediastreamPlayer : LifecycleOwner` (:180)
4 constructores (:1200-1309): () vacío; estándar `(context, activity, playerContainer: FrameLayout?, config, container: FrameLayout)` con `require(context is Activity)`; MiniPlayer `(context, config, container?, playerContainer?, msMiniPlayerConfig?, isFromSyncService=false)`; FragmentManager. En MiniPlayer/Fragment el require(Activity) está COMENTADO.
- Propiedad pública: `var msPlayer: Player? = null` (:210).
- Control: `forward(amount: Long)`, `backward(amount: Long)`, `seekTo(position: Long)` — TODO en ms Long. `changeSpeed(playbackSpeed: Float)` (sin validar). `play()/pause()/previous()`.
- Getters: `getDuration():Long` (no chequea estado), `getContentDuration():Long` (retorna 0 si != STATE_READY), `getCurrentPosition():Long`, `getBitrate():Int`, `getBandwidth():Long`, `getHeight():Int` — todos try/catch → 0. `getVersion():String?` retorna LITERAL "10.0.7" (:7799). `isVodCase()` = dvrStart y dvrEnd no vacíos (:6594). `getCurrentMsPlayer():Player?` devuelve prerollPlayerForSsai si existe, sino msPlayer (:8276).
- Callbacks: `addPlayerCallback(cb?)` deduplica (contains) y reentrega pendingError/pendigEmbedError al registrar (:1069). `getMediastreamCallbacks():List<...>`.
- Sin hooks públicos onResume/onPause/onDestroy. Limpieza: `releasePlayer()` (:6025), `releaseAdsLoader()` (:6005).
- `getDeviceType(context):String` → "firetv"/"androidtv"/"android" (:1031). `handleTVKeyEvent(keyCode:Int, event:KeyEvent):Boolean`.
- DVR (firmas confirmadas): `seekBackward(seekBackMs=10_000)`, `seekForward(seekForwardMs=10_000)`, `switchToDvr(startTime, endTime?=null)`, `switchToDvrByOffset(secondsAgo, durationSeconds?=null)`, `switchToLive()`, `seekInDvr(positionMs)`, `isInDvrMode():Boolean`, `getCurrentDvrPosition():Long`, `getDvrDuration():Long`, `getDvrWindowDurationSeconds():Int`.

## MediastreamPlayerConfig.kt (645) — enums + ~80 props
Enums: `FlagStatus{ENABLE,DISABLE,NONE}`; `VideoTypes{LIVE("live-stream"),VOD("video"),EPISODE("episode")}` (fromKey→LIVE default); `Environment{DEV,PRODUCTION}`; `OTTApps{DEV,PRODUCTION}` (Android Auto); `AudioVideoFormat{DASH("mpd"),MP4,M4A,MP3,ICECAST,DEFAULT("hls")}`; `PlayerType{AUDIO,VIDEO,DEFAULT="video"}`; `Language{ENGLISH("en"),SPANISH("es"),PORTUGUESE("pt")}`.
Defaults clave: `language=ENGLISH` (:184), `environment=PRODUCTION`, `type=VOD`, `videoFormat=DEFAULT`, `playerType=DEFAULT`, `autoplay=true`, `showControls=true`, `trackEnable=true`, `startAt=-1`.
- `class DrmData(var drmUrl:String, var drmHeaders:Map<String,String>)` (:602) y `var drmData:DrmData?` (:78) SÍ EXISTEN.
- `class AdCustomAtributte(var key, var value)` (:600). `var adCustomAtributtes:ArrayList<AdCustomAtributte>?`.
- Métodos: `copy()`, `mergePersistentFrom(previous)` (:451 — language/dvr/startAt del NUEVO; isDebug/showControls/showFullScreenButton/showBrightnessBar/trackEnable/etc. del PREVIO), `addAdCustomAttribute(k,v)`, `getAdQueryString(platform, baseUrl?):String?` (auto-inyecta ppid/rdid/idtype/is_lat), `ensureDAITagParamsFallback(platform)`, `fetchDeviceIdsAsync(context)` (Thread; GAID o Amazon AAID en Fire OS), `waitForDeviceIdsCache(timeoutMs=3000):Boolean` (solo background), `toDebugString()`.

## MediastreamPlayerCallback.kt — interfaz (NO completamente abstracta)
`onFullscreen(enteredForPip: Boolean = false)` tiene default; `nextEpisodeIncoming`/`nextEpisodeLoadRequested` tienen body {} default. El resto son abstractos. Incluye: playerViewReady, onPlay/onPause/onReady/onEnd/onPlayerClosed/onBuffering, onError(String?), onNext/onPrevious, offFullscreen, onNewSourceAdded(config)/onLocalSourceAdded, onAdEvents(AdEvent.AdEventType)/onAdErrorEvent(AdError), onConfigChange(MediastreamMiniPlayerConfig?), 10x onCast*, onPlaybackErrors(JSONObject?)/onEmbedErrors(JSONObject?), onLiveAudioCurrentSongChanged(JSONObject?), onDismissButton, onPlayerReload.

## NOTAS de API (NO son contradicciones con v11 — v11 no existe)
1. DrmData es API normal presente en 10.0.x (CORE-DEF-007 reframeado a not_a_defect/info; CORE-LEARN-003).
2. Default de idioma del player = ENGLISH (no español como i18n web).
3. getVersion() hardcodeado: "10.0.8-alpha01" en binario QA, "10.0.7" en fuente. CORE-DEF-006 expected_value="10.0.8-alpha01".
4. Summary decía `forward(amount)` sin tipo → es `Long` (ms).
5. MediastreamPlayerCallback NO es 100% abstracta (onFullscreen/nextEpisodeIncoming/nextEpisodeLoadRequested son default vía DefaultImpls).

## Bugs core-player conocidos (de memoria sdk_known_bugs, mapeados)
CORE-DEF-001 IMA AdTagLoader leak; CORE-DEF-002 SDK silencia 404 (no onError); CORE-DEF-003 WebView WebMessageListenerHolder leak (DVR); CORE-DEF-004 IMA ActivityLifecycleCallback global cross-test; CORE-DEF-005 playerViewReady en thread no-main.
**Validación**: estos 5 fueron confirmados contra la suite QA en Sony BRAVIA, NO re-validados contra el binario 10.0.8-alpha01 → marcados `validation_status: needs-revalidation` en defects.yaml.
