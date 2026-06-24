# 02 — MediastreamPlayerConfig

## Descripcion

Clase de configuracion del player. Se instancia directamente (no tiene builder pattern formal) y sus propiedades se asignan antes de pasarla al constructor de `MediastreamPlayer`.

---

## Enums

### VideoTypes
| Valor | String API | Descripcion |
|-------|-----------|-------------|
| `VOD` | `"video"` | Video bajo demanda |
| `LIVE` | `"live-stream"` | Stream en vivo |
| `EPISODE` | `"episode"` | Episodio de serie |

### Environment
| Valor | Descripcion |
|-------|-------------|
| `PRODUCTION` | API de produccion (default) |
| `DEV` | API de desarrollo |

### AudioVideoFormat
| Valor | Descripcion |
|-------|-------------|
| `DEFAULT` | HLS (default) |
| `DASH` | MPEG-DASH |
| `MP4` | Video MP4 directo |
| `MP3` | Audio MP3 |
| `M4A` | Audio AAC/M4A |
| `ICECAST` | Stream de radio Icecast |

### PlayerType
| Valor | Descripcion |
|-------|-------------|
| `DEFAULT` | Video con UI estandar |
| `VIDEO` | Fuerza modo video |
| `AUDIO` | Fuerza modo audio (sin superficie de video) |

### Language
| Valor | Codigo | Descripcion |
|-------|--------|-------------|
| `ENGLISH` | `"en"` | Ingles (default) |
| `SPANISH` | `"es"` | Espanol |
| `PORTUGUESE` | `"pt"` | Portugues |

### FlagStatus
| Valor | Descripcion |
|-------|-------------|
| `ENABLE` | Habilitado explicitamente |
| `DISABLE` | Deshabilitado explicitamente |
| `NONE` | No especificado (comportamiento por defecto del SDK) |

---

## Propiedades — Identificacion y tipo

| Propiedad | Tipo | Default | Requerido | Descripcion |
|-----------|------|---------|-----------|-------------|
| `id` | `String?` | null | Si (o `src`) | ID del contenido en la plataforma Mediastream |
| `accountID` | `String?` | null | Si | ID de la cuenta |
| `type` | `VideoTypes` | `VOD` | No | Tipo de contenido |
| `environment` | `Environment` | `PRODUCTION` | No | Entorno de la API |
| `src` | `String?` | null | No | URL directa del stream (omite llamada a API) |
| `accessToken` | `String?` | null | No | Token para contenido protegido |
| `videoFormat` | `AudioVideoFormat` | `DEFAULT` | No | Formato del medio |
| `playerType` | `PlayerType` | `DEFAULT` | No | Tipo de player |

---

## Propiedades — Reproduccion

| Propiedad | Tipo | Default | Descripcion |
|-----------|------|---------|-------------|
| `autoplay` | `Boolean` | `true` | Inicia reproduccion automaticamente |
| `loop` | `Boolean` | `false` | Repite el contenido al terminar |
| `startAt` | `Int` | `-1` | Posicion de inicio en segundos (-1 = desde el principio) |
| `volume` | `Float?` | null | Volumen inicial (0.0 - 1.0) |
| `dvr` | `Boolean` | `false` | Habilitar DVR en streams en vivo |
| `windowDvr` | `Int` | 0 | Ventana DVR en segundos |
| `loadNextAutomatically` | `Boolean` | `false` | Carga automatica del siguiente episodio |
| `nextEpisodeId` | `String?` | null | ID del episodio siguiente (override del detectado por API) |
| `nextEpisodeTime` | `Int?` | null | Segundos antes del final para mostrar sugerencia de siguiente |

---

## Propiedades — UI y controles

| Propiedad | Tipo | Default | Descripcion |
|-----------|------|---------|-------------|
| `showControls` | `Boolean` | `true` | Muestra controles de reproduccion |
| `showFullScreenButton` | `Boolean` | `true` | Muestra boton de pantalla completa |
| `showDismissButton` | `Boolean` | `false` | Muestra boton de cerrar/minimizar |
| `showBrightnessBar` | `Boolean` | `true` | Muestra control de brillo (swipe lateral) |
| `language` | `Language` | `ENGLISH` | Idioma de la UI |
| `customPlayerView` | `PlayerView?` | null | Vista personalizada (override del PlayerView por defecto) |

---

## Propiedades — Publicidad (Ads)

| Propiedad | Tipo | Default | Descripcion |
|-----------|------|---------|-------------|
| `adURL` | `String?` | null | URL del tag VAST/VMAP para anuncios |
| `googleImaPpid` | `String?` | null | Publisher Provided ID para Google IMA |
| `muteAds` | `FlagStatus` | `NONE` | Control de mute en anuncios |
| `adCustomAtributtes` | `ArrayList<AdCustomAtributte>?` | null | Key-value pairs adicionales para el ad tag |

### Clase AdCustomAtributte
```kotlin
data class AdCustomAtributte(
    val key: String,
    val value: String
)
```

---

## Propiedades — DRM

| Propiedad | Tipo | Descripcion |
|-----------|------|-------------|
| `drmData` | `DrmData?` | Configuracion DRM para contenido protegido |

### Clase DrmData
```kotlin
data class DrmData(
    val licenseUrl: String,      // URL del servidor de licencias
    val headers: Map<String, String>? // Headers HTTP para la solicitud de licencia
)
```

---

## Propiedades — Notificaciones

| Propiedad | Tipo | Descripcion |
|-----------|------|-------------|
| `notificationHasNext` | `Boolean` | Muestra boton "siguiente" en la notificacion |
| `notificationIconUrl` | `String?` | URL del icono/artwork para la notificacion |
| `notificationTitle` | `String?` | Titulo en la notificacion de media |
| `notificationDescription` | `String?` | Descripcion en la notificacion |

---

## Propiedades — Analytics / Debug

| Propiedad | Tipo | Default | Descripcion |
|-----------|------|---------|-------------|
| `appName` | `String?` | null | Nombre de la app (para analytics) |
| `appVersion` | `String?` | null | Version de la app (para analytics) |
| `isDebug` | `Boolean` | `false` | Habilita logs verbose del SDK |

---

## Metodos

### `copy(): MediastreamPlayerConfig`
Crea una copia profunda de la configuracion. Util para reutilizar base config con cambios por episodio.

```kotlin
val episodeConfig = baseConfig.copy()
episodeConfig.id = "EPISODE_2_ID"
```

### `mergePersistentFrom(previous: MediastreamPlayerConfig?)`
Fusiona propiedades persistentes de sesion (volumen, brillo, flags de UI) desde una config previa. Se usa internamente al recargar el player entre episodios.

### `addAdCustomAttribute(key: String, value: String)`
Agrega un atributo custom al ad tag URL.

### `getAdQueryString(platform: String, baseUrl: String?): String`
Genera el query string final para la URL de anuncios inyectando parametros (PPID, GDPR, etc.).

---

## Ejemplos de configuracion

### VOD basico
```kotlin
val config = MediastreamPlayerConfig()
config.id = "abc123"
config.accountID = "my-account"
config.type = MediastreamPlayerConfig.VideoTypes.VOD
config.autoplay = true
```

### Live con DVR
```kotlin
val config = MediastreamPlayerConfig()
config.id = "live-channel-id"
config.accountID = "my-account"
config.type = MediastreamPlayerConfig.VideoTypes.LIVE
config.dvr = true
config.windowDvr = 7200  // 2 horas de DVR
```

### Audio podcast
```kotlin
val config = MediastreamPlayerConfig()
config.id = "podcast-ep-01"
config.accountID = "my-account"
config.type = MediastreamPlayerConfig.VideoTypes.EPISODE
config.playerType = MediastreamPlayerConfig.PlayerType.AUDIO
config.loadNextAutomatically = true
```

### Con anuncios y DRM
```kotlin
val config = MediastreamPlayerConfig()
config.id = "premium-content"
config.accountID = "my-account"
config.adURL = "https://ads.example.com/tag.xml"
config.drmData = MediastreamPlayerConfig.DrmData(
    licenseUrl = "https://license.example.com/widevine",
    headers = mapOf("X-Auth-Token" to "token123")
)
```

---

## Testing — Escenarios a cubrir

- [ ] Config minima valida (id + accountID)
- [ ] `copy()` produce objeto independiente (modificar copia no afecta original)
- [ ] `mergePersistentFrom(null)` no lanza excepcion
- [ ] `mergePersistentFrom(previous)` copia volumen y flags correctamente
- [ ] `addAdCustomAttribute()` agrega el par correctamente
- [ ] `getAdQueryString()` incluye todos los parametros
- [ ] Config con `src` ignora `id` en la llamada a la API
- [ ] Enum defaults correctos (type=VOD, environment=PRODUCTION, language=ENGLISH)

---

*Feature: 02-player-config | SDK v9.9.0 | 2026-04-16*
