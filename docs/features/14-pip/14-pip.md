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

### Por qué NO usar `onUserLeaveHint()`

La documentación del SDK (y múltiples fuentes) indica usar `onUserLeaveHint()` como trigger. Esto es **insuficiente**:

- La guía oficial de Android dice que dispara en Home y Recientes, pero en la práctica **es inconsistente en dispositivos con capas OEM** (Samsung One UI, Xiaomi HyperOS), donde el botón de Recientes no lo dispara de forma confiable.
- Bug confirmado en SDK 10.0.3-alpha06: PiP no se activaba al presionar Recientes, el audio continuaba pero sin ventana flotante.

### Patron correcto por version de API

```kotlin
class VideoActivity : AppCompatActivity() {
    private lateinit var player: MediastreamPlayer

    // 1. Habilitar PiP en el Manifest
    // android:supportsPictureInPicture="true"
    // android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... init player ...

        // API 31+ (Android 12+): recomendacion oficial de Google.
        // El sistema gestiona la transicion para Home, Recientes y gestos de navegacion.
        // No requiere ningun trigger manual.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(true)
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    // API 26–30: onPause() cubre Home y Recientes.
    // Guardias obligatorias:
    //   !isFinishing     → evita entrar en PiP al presionar Atras
    //   player.isPlaying → evita falsos positivos (notificaciones, dialogos del sistema, llamadas)
    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            !isFinishing &&
            player.isPlaying
        ) {
            player.startPiP()
        }
    }

    // OBLIGATORIO: notificar al SDK cuando cambia el estado de PiP.
    // Si no se llama, el SDK no ajusta su UI (controles no se ocultan).
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        player.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }
}
```

### Resumen de cobertura por trigger

| Accion del usuario | `onUserLeaveHint()` | `onPause()` + guardias | `setAutoEnterEnabled` (API 31+) |
|---|---|---|---|
| Boton Home | ✅ | ✅ | ✅ |
| Boton Recientes | ⚠️ inconsistente en OEMs | ✅ | ✅ |
| Gesto swipe-up | ⚠️ inconsistente | ✅ | ✅ |
| Boton Atras | ✅ no dispara | ✅ bloqueado por `!isFinishing` | ✅ no dispara |
| Notificacion / dialogo encima | ✅ no dispara | ✅ bloqueado por `isPlaying` | ✅ no dispara |
| Llamada entrante | ✅ no dispara | ✅ bloqueado por `isPlaying` | ✅ no dispara |

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
- **`onPictureInPictureModeChanged` es responsabilidad del cliente** llamarlo — si no lo hace, el SDK no ajusta su UI correctamente
- **`onUserLeaveHint()` es inconsistente en OEMs:** Samsung One UI y Xiaomi HyperOS no garantizan su disparo al presionar Recientes — no usar como unico trigger
- **`onPause()` sin guardias genera falsos positivos:** notificaciones, dialogos del sistema y llamadas entrantes tambien disparan `onPause()` — las guardias `!isFinishing` e `isPlaying` son obligatorias

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

### Trigger — Home vs Recientes
- [ ] Boton Home activa PiP correctamente (API 26+)
- [ ] Boton Recientes activa PiP correctamente (API 26+)
- [ ] Gesto swipe-up activa PiP correctamente (API 31+)
- [ ] Boton Atras NO entra en PiP
- [ ] Bajar barra de notificaciones con video pausado NO entra en PiP
- [ ] Llamada entrante con video pausado NO entra en PiP

---

## Historial de hallazgos QA

| Fecha | Version SDK | Hallazgo |
|---|---|---|
| 2026-05-12 | 10.0.3-alpha06 | PiP no se activaba con boton Recientes en dispositivos OEM. Causa: `onUserLeaveHint()` no confiable en Samsung/Xiaomi. Fix: `onPause()` + guardias para API 26-30, `setAutoEnterEnabled` para API 31+. Gap reportado en docs oficiales del SDK. |

---

*Feature: 14-pip | SDK v9.9.0 | Actualizado 2026-05-12*
