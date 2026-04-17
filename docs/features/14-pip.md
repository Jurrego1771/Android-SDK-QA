# 14 — Picture-in-Picture (PiP)

## Descripcion

Modo flotante que permite al usuario salir de la app mientras el video continua reproduciendose en una ventana superpuesta. El SDK implementa PiP via la clase `MediastreamPlayerPip` con aspect ratio fijo de 16:9.

**Requiere:** Android 8.0+ (API 26)

---

## Implementacion interna

El SDK usa `MediastreamPlayerPip` como handler:

```kotlin
// Interno al SDK — se crea durante la inicializacion del player
class MediastreamPlayerPip(private val activity: Activity) {

    fun enterPictureInPictureMode() {
        val pipParams = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))   // siempre 16:9, no configurable
            .build()
        activity.enterPictureInPictureMode(pipParams)
    }

    fun updatePipParams(): PictureInPictureParams {
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
    }
}
```

**El aspect ratio 16:9 es fijo** — no hay forma de configurarlo desde `MediastreamPlayerConfig`.

---

## Configuracion de PiP

PiP tiene un campo de configuracion propio en `MediastreamPlayerConfig`:

```kotlin
config.pip = MediastreamPlayerConfig.FlagStatus.ENABLE   // forzar PiP habilitado
config.pip = MediastreamPlayerConfig.FlagStatus.DISABLE  // forzar PiP deshabilitado
config.pip = MediastreamPlayerConfig.FlagStatus.NONE     // usar el valor de la API (default)
```

### Logica de resolucion de `pip`

```kotlin
// Interno en MediastreamPlayer.startPiP()
val shouldEnterPiP = when (msConfig?.pip) {
    FlagStatus.ENABLE  -> true                          // config local: habilitar
    FlagStatus.DISABLE -> false                         // config local: deshabilitar
    FlagStatus.NONE    -> mediaInfo?.player?.pip == true // usar valor de la API
    else               -> false
}
```

**Jerarquia:**
1. Si `config.pip = ENABLE` o `DISABLE` → se respeta el valor local
2. Si `config.pip = NONE` (o null) → se lee el campo `pip` de la respuesta de la API
3. Si la API tampoco tiene el valor → `false` (PiP deshabilitado)

---

## Uso desde la Activity del cliente

```kotlin
class VideoActivity : AppCompatActivity() {
    private lateinit var player: MediastreamPlayer

    // 1. Habilitar PiP en el Manifest (en el proyecto cliente, NO en el SDK)
    // android:supportsPictureInPicture="true"
    // android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"

    // 2. Iniciar PiP (por ejemplo al presionar el boton home o un boton custom)
    fun onUserPressHome() {
        player.startPiP()
    }

    // 3. OBLIGATORIO: notificar al SDK cuando cambia el estado de PiP
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        player.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // El SDK delega a MediastreamPlayerPip.onPictureInPictureModeChanged()
    }
}
```

---

## Comportamiento

| Accion | Resultado |
|--------|-----------|
| `startPiP()` con `pip=DISABLE` | Nada ocurre (SDK ignora la llamada) |
| `startPiP()` con `pip=ENABLE` | Player entra en ventana flotante 16:9 |
| `startPiP()` con `pip=NONE` + API tiene `pip=true` | Player entra en ventana flotante |
| `startPiP()` con `pip=NONE` + API tiene `pip=false` | Nada ocurre |
| `onPictureInPictureModeChanged(true)` | SDK adapta UI para PiP |
| `onPictureInPictureModeChanged(false)` | SDK restaura UI completa |
| Usuario cierra la ventana PiP | `onPlayerClosed()` callback |

---

## Limitaciones conocidas

- **Aspect ratio fijo:** siempre 16:9. No apto para contenido vertical o 4:3
- **Requiere Activity:** `MediastreamPlayerPip` necesita una `Activity` en su constructor. Si el player se inicializa sin Activity (desde Service), PiP no esta disponible (`pipHandler` sera null)
- **API 26 minimo:** en dispositivos con Android 7.x, `startPiP()` no hace nada
- **`onPictureInPictureModeChanged` es el cliente el responsable** de llamarlo — si no lo hace, el SDK no ajusta su UI correctamente

---

## Testing — Escenarios a cubrir

### Configuracion
- [ ] `pip=ENABLE` → `startPiP()` entra en modo PiP
- [ ] `pip=DISABLE` → `startPiP()` NO entra en modo PiP
- [ ] `pip=NONE` + API devuelve `pip=true` → `startPiP()` entra en PiP
- [ ] `pip=NONE` + API devuelve `pip=false` → `startPiP()` NO entra en PiP
- [ ] `pip=NONE` + API no devuelve campo `pip` → NO entra en PiP (default false)

### Comportamiento de UI
- [ ] La reproduccion continua sin interrupcion al entrar a PiP
- [ ] El aspect ratio de la ventana PiP es 16:9
- [ ] `onPictureInPictureModeChanged(true)` oculta los controles correctamente
- [ ] `onPictureInPictureModeChanged(false)` restaura los controles
- [ ] Cerrar la ventana PiP dispara `onPlayerClosed()`

### Casos limite
- [ ] `startPiP()` en dispositivo con API < 26 no lanza excepcion
- [ ] `startPiP()` cuando `pipHandler` es null (sin Activity) no crashea
- [ ] Cliente NO llama a `onPictureInPictureModeChanged()` → SDK no crashea (solo UI incorrecta)

---

*Feature: 14-pip | SDK v9.9.0 | 2026-04-16*
