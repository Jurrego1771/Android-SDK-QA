# 06 — Episodes: Next Episode UI y Autoplay

## Descripcion

El feature de siguiente episodio tiene una **UI propia** (overlay con animacion) y dos modos de operacion distintos. No es simplemente un callback — el SDK maneja todo el ciclo de vida del overlay, el temporizador, y la transicion.

---

## Dos modos de operacion

### Modo API (`isManualNextEpisodeConfig = false`)
El ID del siguiente episodio viene de la respuesta de la API (`ConfigMain`). El SDK maneja todo automaticamente:
- Detecta el siguiente episodio desde la API
- Muestra el overlay automaticamente
- Hace la transicion sin intervencion del cliente

### Modo Manual (`isManualNextEpisodeConfig = true`)
Se activa cuando el cliente define `config.nextEpisodeId` explicitamente. El SDK **espera la confirmacion del cliente** via `updateNextEpisode()` antes de mostrar el overlay.

```kotlin
// Modo manual: el cliente controla cual es el siguiente episodio
config.nextEpisodeId = "MY_CUSTOM_NEXT_ID"

// En el callback nextEpisodeIncoming, el cliente debe confirmar
override fun nextEpisodeIncoming(nextEpisodeId: String) {
    // Preparar la config del siguiente episodio
    val nextConfig = MediastreamPlayerConfig()
    nextConfig.id = myEpisodeList[currentIndex + 1]
    nextConfig.accountID = "ACCOUNT_ID"
    // ...

    // Confirmar al SDK cual es el siguiente (OBLIGATORIO en modo manual)
    player.updateNextEpisode(nextConfig)
}
```

---

## Timing del flujo completo

```
Video: |========================================| fin
                                  ^     ^
                                  |     |
              [callbackTime] ─────┘     └─── [appearTime]
              (appearTime - 3000ms)          (duration - nextEpisodeTime * 1000ms)
```

1. **`callbackTime`** = `appearTime - 3000ms`
   - El SDK dispara `nextEpisodeIncoming(nextEpisodeId)` al cliente
   - En modo manual: el cliente tiene 3 segundos para llamar a `updateNextEpisode()`

2. **`appearTime`** = `duracion - (nextEpisodeTime * 1000)`
   - El overlay aparece en pantalla
   - En modo API: aparece siempre
   - En modo manual: solo aparece si el cliente llamo a `updateNextEpisode()`

3. **Auto-transicion** = 5 segundos despues de que aparece el overlay
   - Si el usuario no toca nada, el SDK carga el siguiente episodio automaticamente

---

## Estado interno del SDK

El SDK mantiene dos data classes internas para gestionar el estado:

```kotlin
// Estado del feature (interno al SDK)
private data class NextEpisodeState(
    var appearTime: Long = -1L,          // ms donde debe aparecer el overlay
    var callbackTime: Long = -1L,        // ms donde se dispara el callback
    var monitoringActive: Boolean = false,   // esta monitoreando la posicion?
    var callbackEmitted: Boolean = false,    // ya se emitio el callback?
    var overlayVisible: Boolean = false,     // esta visible el overlay?
    var userDismissed: Boolean = false       // usuario eligio ver los creditos?
)

// UI del overlay (interno al SDK)
private data class NextEpisodeUI(
    var overlay: View? = null,                   // la vista del overlay
    var autoTransitionRunnable: Runnable? = null, // timer de 5s para auto-transicion
    var focusRunnable: Runnable? = null,          // focus para TV
    var fillAnimator: ValueAnimator? = null,      // animacion del fill progress
    var confirmedConfig: MediastreamPlayerConfig? = null  // config confirmada (modo manual)
)
```

---

## Layout del overlay: `next_episode_overlay.xml`

El overlay se posiciona en la **esquina inferior derecha** del player y contiene dos botones:

```
┌──────────────────────────────────────────────┐
│                                               │
│                   [VIDEO]                     │
│                                               │
│                    [Ver creditos] [Sig. ep →] │
│                                   [████░░░░] │ <- fill animation (5s)
└──────────────────────────────────────────────┘
```

| Elemento | ID en layout | Funcion |
|----------|-------------|---------|
| Container del overlay | `next_episode_overlay_container` | Fondo semi-transparente oscuro |
| Boton "Ver creditos" | `watch_credits_container` | El usuario quiere seguir viendo los creditos |
| Boton "Siguiente episodio" | `next_episode_container` | Ir al siguiente episodio ahora |
| Barra de progreso animada | `watch_credits_animation_fill` | Relleno de izquierda a derecha (5 segundos) |
| Texto del boton | `btn_next_episode` | Texto localizado segun `config.language` |

**Dimensiones:** 150dp x 56dp por boton, margen de 24dp desde los bordes.

---

## Comportamiento de cada boton

### Boton "Siguiente episodio" (o auto-transicion)
- Llama a `loadNextEpisode()` → carga la nueva config → dispara `onNewSourceAdded(config)` → dispara `onPlayerReload()`

### Boton "Ver creditos"
- Setea `nextEpisodeState.userDismissed = true`
- Cancela el `autoTransitionRunnable` (no hay auto-transicion)
- Oculta el overlay → el video sigue reproduciendose hasta el final
- Al llegar al final (`onEnd`), si hay siguiente episodio configurado, puede transicionar

---

## Metodo `updateNextEpisode()` (modo manual)

```kotlin
// Disponible publicamente en MediastreamPlayer
fun updateNextEpisode(config: MediastreamPlayerConfig)
```

- Solo tiene efecto en modo manual (`isManualNextEpisodeConfig = true`)
- Debe llamarse despues de recibir `nextEpisodeIncoming()` y antes de `appearTime`
- Si se llama demasiado tarde (overlay ya visible), el SDK puede ignorarlo

---

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "EPISODE_ID"
config.accountID = "ACCOUNT_ID"
config.type = MediastreamPlayerConfig.VideoTypes.EPISODE

// Tiempo antes del final donde aparece el overlay (default del SDK si null)
config.nextEpisodeTime = 15     // overlay aparece 15 segundos antes del final

// Modo manual: override del siguiente episodio
config.nextEpisodeId = "CUSTOM_NEXT_ID"  // si null, usa lo que devuelve la API

// Si true, carga el siguiente automaticamente
config.loadNextAutomatically = true
```

---

## Callbacks relevantes y su orden

```
nextEpisodeIncoming("EP2_ID")   ← 3 segundos ANTES de que aparezca el overlay
         │
         │ (modo manual: cliente llama updateNextEpisode())
         ▼
    [overlay aparece]            ← animacion fill comienza
         │
         │ (5 segundos de countdown, o click del usuario)
         ▼
onNewSourceAdded(nextConfig)     ← config del episodio siguiente
         │
         ▼
onPlayerReload()                 ← player se recarga con el nuevo episodio
         │
         ▼
playerViewReady() → onReady() → onPlay()   ← ciclo normal del nuevo episodio
```

---

## Ejemplo completo — Modo Manual (NextEpisodeActivity del SDK)

```kotlin
class MyEpisodeActivity : AppCompatActivity() {
    private val episodeIds = listOf("EP1_ID", "EP2_ID", "EP3_ID")
    private var currentIndex = 0

    private fun initPlayer(episodeId: String) {
        val config = MediastreamPlayerConfig().apply {
            id = episodeId
            accountID = "ACCOUNT_ID"
            type = MediastreamPlayerConfig.VideoTypes.EPISODE
            nextEpisodeId = if (currentIndex < episodeIds.lastIndex) "has_next" else null
            nextEpisodeTime = 15
        }

        player = MediastreamPlayer(this, this, binding.playerContainer, config, binding.root)
        player.addPlayerCallback(object : MediastreamPlayerCallback {

            override fun nextEpisodeIncoming(nextEpisodeId: String) {
                // El SDK nos avisa 3s antes: preparar la config del siguiente
                if (currentIndex < episodeIds.lastIndex) {
                    currentIndex++
                    val nextConfig = MediastreamPlayerConfig().apply {
                        id = episodeIds[currentIndex]
                        accountID = "ACCOUNT_ID"
                        type = MediastreamPlayerConfig.VideoTypes.EPISODE
                        nextEpisodeId = if (currentIndex < episodeIds.lastIndex) "has_next" else null
                        nextEpisodeTime = 15
                    }
                    player.updateNextEpisode(nextConfig)  // CONFIRMAR al SDK
                }
            }

            override fun onNewSourceAdded(config: MediastreamPlayerConfig) {
                // El SDK cargo el nuevo episodio, actualizar UI si necesario
            }
        })
    }
}
```

---

## Testing — Escenarios a cubrir

### Timing y callbacks
- [ ] `nextEpisodeIncoming` se dispara exactamente 3 segundos antes de `appearTime`
- [ ] `nextEpisodeIncoming` NO se dispara mas de una vez por reproduccion
- [ ] El overlay aparece en el segundo correcto (`duracion - nextEpisodeTime`)
- [ ] La animacion fill dura 5 segundos exactos

### Modo API
- [ ] Overlay aparece automaticamente sin llamar a `updateNextEpisode()`
- [ ] Auto-transicion ocurre a los 5 segundos sin interaccion del usuario
- [ ] `onNewSourceAdded(config)` se dispara con la config del siguiente episodio
- [ ] `onPlayerReload()` se dispara tras la transicion

### Modo Manual
- [ ] Sin llamar a `updateNextEpisode()`: el overlay NO aparece
- [ ] Llamando a `updateNextEpisode()` despues de `nextEpisodeIncoming`: el overlay aparece
- [ ] Config pasada a `updateNextEpisode()` es la que se carga en el player

### Botones del overlay
- [ ] Click en "Ver creditos": oculta overlay, continua reproduccion, NO hace auto-transicion
- [ ] Click en "Siguiente episodio": transicion inmediata al siguiente
- [ ] Auto-transicion a los 5 segundos si no hay interaccion

### Edge cases
- [ ] Ultimo episodio (sin siguiente): overlay NO aparece, `nextEpisodeIncoming` NO se dispara
- [ ] `nextEpisodeTime` mayor que la duracion del video: comportamiento graceful
- [ ] Usuario pausa durante el countdown: el timer de 5s se pausa
- [ ] TV (D-pad): el focus se mueve al boton del overlay correctamente

---

*Feature: 06-video-episode | SDK v9.9.0 | 2026-04-16*
