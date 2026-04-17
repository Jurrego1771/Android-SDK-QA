# SDK Real Usage — Hallazgos de Integración v11.0.0-alpha.01

> Documento vivo. Actualizar cada vez que se descubra un comportamiento real del SDK
> distinto a lo documentado en SDK_Reference.md o docs/features/*.md.
> Última actualización: 2026-04-16

---

## 1. Build & Dependencias

### AGP 9.0+ — Kotlin built-in
El plugin `org.jetbrains.kotlin.android` ya **no se debe aplicar** con AGP 9.0+.
AGP tiene soporte Kotlin nativo. Aplicarlo causa:
```
Cannot add extension with name 'kotlin', as there is an extension already registered
```
**Solución:** Eliminar el plugin. Configurar Kotlin con:
```kotlin
// app/build.gradle.kts
android {
    kotlin { jvmToolchain(17) }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

### Java Target: 17 (no 11)
El SDK se compila con Java 17. Usar `VERSION_11` puede causar incompatibilidades.
```kotlin
sourceCompatibility = JavaVersion.VERSION_17
targetCompatibility = JavaVersion.VERSION_17
kotlin { jvmToolchain(17) }
```

### Core Library Desugaring obligatorio
El SDK, IMA y Media3 requieren desugaring. Versión mínima requerida: **2.1.5**
```kotlin
// compileOptions
isCoreLibraryDesugaringEnabled = true

// dependencies
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
```

### Repositorios requeridos en settings.gradle.kts
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://npaw.jfrog.io/artifactory/youbora/") }
}
```

---

## 2. MediastreamPlayerConfig — Propiedades Confirmadas en v11

### Propiedades que EXISTEN ✅
```kotlin
id                    // String — media ID
accountID             // String — account ID
type                  // VideoTypes enum (VOD, LIVE, EPISODE)
playerType            // PlayerType enum (VIDEO, AUDIO)
environment           // Environment enum (PRODUCTION, DEV)
autoplay              // Boolean
isDebug               // Boolean
loop                  // Boolean
dvr                   // Boolean
pip                   // FlagStatus (ENABLE, DISABLE, NONE)
notificationHasNext   // Boolean — control en notificación
nextEpisodeId         // String? — activa modo manual de next episode
nextEpisodeTime       // Int — segundos antes del fin para disparar callback
playerId              // String — ID del player configurado en plataforma (reels)
appHandlesWindowInsets // Boolean — el app maneja insets de sistema
pauseOnScreenClick    // FlagStatus — desactivar pause al tocar pantalla
showDismissButton     // Boolean — mostrar botón X en el player
trackEnable           // Boolean — habilitar/deshabilitar tracking analítico
```

### Propiedades que NO EXISTEN en v11 ❌
```kotlin
notificationTitle       // ❌ eliminado — SDK toma metadatos del contenido
notificationDescription // ❌ eliminado
notificationHasPrev     // ❌ eliminado (solo existe notificationHasNext)
drmData / DrmData       // ❌ MediastreamPlayerConfig.DrmData no existe
                        //    DRM se resuelve automáticamente vía plataforma
```

---

## 3. MediastreamPlayer — Métodos Confirmados en v11

### Constructor
```kotlin
MediastreamPlayer(
    context: Context,
    config: MediastreamPlayerConfig,
    container: ViewGroup,        // FrameLayout donde vive el player
    playerView: PlayerView,      // androidx.media3.ui.PlayerView
    supportFragmentManager: FragmentManager
)
```

### Métodos disponibles ✅
```kotlin
player.reloadPlayer(config: MediastreamPlayerConfig)   // cambiar contenido sin destruir
player.releasePlayer()                                  // destruir — llamar en onDestroy
player.addPlayerCallback(callback: MediastreamPlayerCallback)
player.startPiP()                                       // entrar en Picture-in-Picture
player.handleTVKeyEvent(keyCode: Int, event: KeyEvent): Boolean
player.onPictureInPictureModeChanged(isInPip: Boolean)
player.updateNextEpisode(config: MediastreamPlayerConfig) // modo manual next episode
player.msPlayer                                         // ExoPlayer — acceso directo
```

### msPlayer (ExoPlayer) — Métodos útiles para QA
```kotlin
player.msPlayer?.play()
player.msPlayer?.pause()
player.msPlayer?.seekTo(positionMs: Long)
player.msPlayer?.currentPosition       // Long — posición actual en ms
player.msPlayer?.duration              // Long — duración total en ms
player.msPlayer?.isPlaying             // Boolean
player.msPlayer?.playbackState         // Player.STATE_*
player.msPlayer?.volume                // Float 0.0-1.0
```

---

## 4. MediastreamPlayerCallback — Interface Completamente Abstracta

**IMPORTANTE:** No tiene métodos con implementación por defecto.
Implementar un `object : MediastreamPlayerCallback` requiere implementar **todos** los métodos.

### Firma corregida en v11
```kotlin
// ✅ v11 — enteredForPip como parámetro
override fun onFullscreen(enteredForPip: Boolean) { }

// ❌ v10 y anterior
// override fun onFullscreen() { }
```

### `nextEpisodeIncoming` tiene implementación por defecto
Es el ÚNICO método del interface con body default `{}` — los demás son abstractos puros.

### IMPORTANTE: usar block body, no expression body
`Log.d()` retorna `Int`. Con expression body `= Log.d(...)` Kotlin infiere `Int` como return type,
incompatible con `Unit`. Siempre usar block body en callbacks:
```kotlin
// ✅ Correcto — retorna Unit
override fun onReady() { Log.d(TAG, "onReady") }

// ❌ Error de compilación — infiere Int (return type de Log.d)
override fun onReady() = Log.d(TAG, "onReady")
```

### Solución para escenarios que solo necesitan uno o dos callbacks
No crear anonymous objects parciales. En cambio, exponer hooks `open` en `BaseScenarioActivity`:
```kotlin
// BaseScenarioActivity
open fun onNextEpisodeIncoming(nextEpisodeId: String) {}

// El logging callback centralizado llama al hook:
override fun nextEpisodeIncoming(nextEpisodeId: String) {
    log("nextEpisodeIncoming", detail = nextEpisodeId)
    onNextEpisodeIncoming(nextEpisodeId)  // hook para subclases
}
```

---

## 5. DRM en v11

`MediastreamPlayerConfig.DrmData` **no existe** como clase pública en v11.

El flujo correcto es:
1. Configurar el contenido con DRM en la plataforma Mediastream (DEV o PROD)
2. Cargar el contenido solo con `id` + `accountID` + `environment`
3. El SDK resuelve la licencia internamente

**Proveedor DRM de la cuenta:** Axinom Widevine
- License URL: `https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense`
- Auth: JWT firmado con Communication Key (generado server-side, no exponer en cliente)
- Communication Key ID: `20f3aae7-608c-4a42-b830-b332015b5e65`

**Contenido DRM disponible:** Solo en DEV (`699afcb05a41925324fa4605`)

---

## 6. Reels — Configuración Específica

Requiere **dos IDs** distintos:
```kotlin
config.id = "6980e43ac0ac0673d0944d63"   // media ID del reel
config.playerId = "6980ccd0654c284dc952b544" // player ID configurado en plataforma
```

Configuración recomendada para reels:
```kotlin
MediastreamPlayerConfig().apply {
    id = TestContent.Reels.MEDIA_INITIAL
    playerId = TestContent.Reels.PLAYER_ID
    accountID = TestContent.ACCOUNT_ID
    type = MediastreamPlayerConfig.VideoTypes.VOD
    environment = TestContent.ENV
    autoplay = true
    loop = false
    pauseOnScreenClick = MediastreamPlayerConfig.FlagStatus.DISABLE
    showDismissButton = true
    appHandlesWindowInsets = true
    trackEnable = false
    isDebug = true
}
```

Layout recomendado: FrameLayout programático, pantalla completa, sin chrome de UI.

---

## 7. updateNextEpisode — Firma Correcta

Recibe `MediastreamPlayerConfig`, NO un String:
```kotlin
// ✅ Correcto
val nextConfig = MediastreamPlayerConfig().apply {
    id = "next_episode_id"
    accountID = TestContent.ACCOUNT_ID
    type = MediastreamPlayerConfig.VideoTypes.EPISODE
    environment = TestContent.ENV
}
player.updateNextEpisode(nextConfig)

// ❌ Incorrecto (no compila)
player.updateNextEpisode("next_episode_id")
```

---

## 8. AndroidManifest — Configuración Necesaria

```xml
<!-- Permite HTTP — necesario para algunos streams en DEV -->
android:usesCleartextTraffic="true"

<!-- Servicios del SDK — necesarios para background playback -->
<service
    android:name="am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />

<service
    android:name="am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerServiceWithSync"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

---

## 9. Pendientes / Comportamiento No Confirmado

| Comportamiento | Estado | Notas |
|---|---|---|
| `skipAd()` en IMA | ❓ | ExoPlayer no lo expone. ¿SDK lo tiene? |
| `notificationHasNext` funciona en audio | ❓ | Confirmar en runtime |
| `appHandlesWindowInsets` — efecto real | ❓ | Probar en Android 14+ |
| DRM auto sin drmData | ❓ | Probar con contenido DEV |
| Loop en reels (`loop=true`) | ❓ | Confirmar que onEnd no se dispara |
| `trackEnable=false` desactiva Youbora | ❓ | Verificar en logs de red |
| PiP `pipReplaceActivityContentWithPlayer` | ❓ | Documentado, no confirmado en v11 |
| `pipExpandToFullscreenFirst` | ❓ | Documentado, no confirmado en v11 |
