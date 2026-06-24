# 01 — Inicializacion del Player

## Constructores disponibles

El SDK expone tres constructores para `MediastreamPlayer`. La eleccion depende del contexto de uso.

---

### Constructor 1 — Estandar (uso mas comun)

```kotlin
MediastreamPlayer(
    context: Context,
    activity: Activity,
    playerContainer: FrameLayout?,  // contenedor del player UI
    config: MediastreamPlayerConfig,
    container: FrameLayout          // contenedor raiz de la pantalla
)
```

**Cuando usarlo:** Activities normales con video/audio. El caso mas frecuente.

**Responsabilidades del caller:**
- Proveer un `FrameLayout` como `playerContainer` (donde se inserta la vista del player)
- Proveer `container` como el layout raiz (usado para fullscreen y PiP)

---

### Constructor 2 — Con MiniPlayerConfig y servicio

```kotlin
MediastreamPlayer(
    context: Context,
    config: MediastreamPlayerConfig,
    container: FrameLayout?,
    playerContainer: FrameLayout?,
    msMiniPlayerConfig: MediastreamMiniPlayerConfig?,
    isFromSyncService: Boolean = false
)
```

**Cuando usarlo:**
- Cuando se necesita el mini-player flotante (`msMiniPlayerConfig != null`)
- Cuando se inicializa desde un `Service` en background (`isFromSyncService = true`)

**Notas:**
- `msMiniPlayerConfig` puede ser null (el parametro es opcional)
- `isFromSyncService = true` cuando se usa junto con `MediastreamPlayerServiceWithSync`

---

### Constructor 3 — Con FragmentManager

```kotlin
MediastreamPlayer(
    context: Context,
    config: MediastreamPlayerConfig,
    container: FrameLayout?,
    playerContainer: FrameLayout?,
    fragmentManager: FragmentManager
)
```

**Cuando usarlo:** Cuando el player se embebe en un Fragment y se necesita acceso al `FragmentManager` (dialogo de tracks, subtitulos, etc.)

---

## Flujo de inicializacion interno

```
new MediastreamPlayer(...)
        │
        ▼
ApiService.getMediaInfo(id, accountID)   ← llamada HTTP a la API
        │
        ▼
Parsea ConfigMain (JSON → modelo)
        │
        ▼
Construye ExoPlayer / MediaSource
        │
        ├─► Configura IMA ads (si adURL presente)
        ├─► Configura DRM (si drmData presente)
        ├─► Configura Comscore (si comscore presente en ConfigMain)
        ├─► Configura Youbora (si youbora presente en ConfigMain)
        │
        ▼
playerViewReady(playerView)   ← primer callback al cliente
        │
        ▼
onReady()                     ← player listo para reproducir
```

---

## Requisitos minimos de configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "MEDIA_ID"          // REQUERIDO (o src)
config.accountID = "ACCOUNT_ID" // REQUERIDO
config.type = MediastreamPlayerConfig.VideoTypes.VOD
config.environment = MediastreamPlayerConfig.Environment.PRODUCTION
```

Alternativa con URL directa (omite llamada a API):
```kotlin
config.src = "https://example.com/video.m3u8"
// id puede ser null si src esta definido
```

---

## Ciclo de vida del player

El cliente es responsable de manejar el ciclo de vida de la Activity/Fragment:

```kotlin
// En Activity
override fun onDestroy() {
    super.onDestroy()
    // El player debe liberarse para evitar memory leaks
    // (ver documentacion de ExoPlayer lifecycle)
}

override fun onPictureInPictureModeChanged(isInPiP: Boolean, ...) {
    player.onPictureInPictureModeChanged(isInPiP)
}
```

---

## Propiedades publicas post-inicializacion

| Propiedad | Tipo | Descripcion |
|-----------|------|-------------|
| `msPlayer` | `Player?` | Instancia ExoPlayer. **Puede ser null.** |
| `autoplay` | `Boolean` | Si la reproduccion inicia automaticamente |
| `castEnable` | `Boolean` | Si Cast esta habilitado |
| `prerollPlayerForSsai` | `Player?` | Player de preroll para SSAI |

---

## Casos limite y riesgos

| Escenario | Comportamiento esperado | Riesgo |
|-----------|------------------------|--------|
| `id` null y `src` null | Error en inicializacion | ALTO |
| Sin conectividad al inicializar | `onEmbedErrors()` callback | ALTO |
| `playerContainer` null | Player inicializa sin UI visible | MEDIO |
| Activity destruida antes de callback | Posible NPE/leak | ALTO |
| Doble inicializacion sin release | Estado inconsistente | MEDIO |

---

## Testing — Escenarios a cubrir

- [ ] Inicializacion exitosa con `id` valido
- [ ] Inicializacion exitosa con `src` directo
- [ ] Error cuando API devuelve 404
- [ ] Error cuando API devuelve 401 (token invalido)
- [ ] Error sin conectividad
- [ ] Inicializacion con cada constructor (3 variantes)
- [ ] Callback `playerViewReady` se dispara
- [ ] Callback `onReady` se dispara despues de `playerViewReady`

---

*Feature: 01-initialization | SDK v9.9.0 | 2026-04-16*
