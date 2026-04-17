# 13 — Google Cast / Chromecast

## Descripcion

Soporte para transmitir contenido a dispositivos Chromecast via Google Cast SDK, integrado con Media3.

## Requisito de configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "CONTENT_ID"
config.accountID = "ACCOUNT_ID"
// El Cast se habilita automaticamente si hay un DefaultCastOptionsProvider
// declarado en el Manifest del SDK
```

El Manifest del SDK ya incluye:
```xml
<meta-data
    android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
    android:value="com.google.android.gms.cast.framework.media.widget.DefaultCastOptionsProvider"/>
```

## Propiedad del player

```kotlin
val castHabilitado: Boolean = player.castEnable
```

## Callbacks de Cast

```kotlin
override fun onCastAvailable(state: Boolean?) {
    // state = true: hay dispositivos Cast en la red
    // state = false: no hay dispositivos disponibles
    castButton.isVisible = state == true
}

override fun onCastSessionStarting() { showCastConnecting() }
override fun onCastSessionStarted() { showCastConnected() }
override fun onCastSessionEnding() { /* ... */ }
override fun onCastSessionEnded() { showLocalPlayback() }
override fun onCastSessionResumed() { showCastConnected() }
```

## Flujo de Cast

```
Usuario abre app → onCastAvailable(true) si hay Chromecast en red
        │
Usuario toca boton de Cast
        │
        ▼
onCastSessionStarting()
        │
        ▼
onCastSessionStarted()     ← reproduccion pasa al TV
        │
Usuario cierra Cast
        │
        ▼
onCastSessionEnding()
        │
        ▼
onCastSessionEnded()       ← reproduccion vuelve al dispositivo
```

## Testing — Escenarios a cubrir

- [ ] `onCastAvailable(true)` cuando hay Chromecast en la red
- [ ] `onCastAvailable(false)` cuando no hay dispositivos
- [ ] Ciclo completo: Starting → Started → Ending → Ended
- [ ] La reproduccion continua en el TV durante sesion Cast
- [ ] Al terminar Cast, la reproduccion retoma en el dispositivo en la posicion correcta
- [ ] `castEnable` es `true` cuando Cast esta disponible

**Nota:** Los tests E2E de Cast requieren un dispositivo Chromecast fisico o emulado en la red. Para unit/integration tests, mockear `CastContext` y verificar que los callbacks se disparan.

---

*Feature: 13-chromecast | SDK v9.9.0 | 2026-04-16*
