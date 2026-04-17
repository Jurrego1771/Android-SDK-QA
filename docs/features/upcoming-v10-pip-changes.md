# Cambios PiP planificados — v10.0.0 (NO en produccion aun)

> **Estado:** Documentado internamente en el SDK (`context/pip-implementation.md`) como rama `10.0.0`.
> **En el codigo actual (v9.9.0 / main):** estos cambios NO existen todavia.
> **Accion requerida al llegar a produccion:** actualizar `docs/features/14-pip.md`, `docs/features/03-callbacks.md`, y los tests de PiP.

---

## Nuevas opciones de configuracion

```kotlin
// Estas propiedades NO existen en v9.9.0 — llegan en v10.0.0
config.pipReplaceActivityContentWithPlayer = true   // ver descripcion abajo
config.pipExpandToFullscreenFirst = true            // ver descripcion abajo
```

### `pipReplaceActivityContentWithPlayer`

**El problema que resuelve:** Cuando el player esta en un contenedor pequeno (no ocupa toda la Activity), Android al entrar en PiP encoge TODA la ventana de la Activity. El resultado es que el video aparece muy pequeno dentro de la ventana PiP (rodeado de otros elementos UI como headers, scrolls, etc.).

**La solucion:** Con `pipReplaceActivityContentWithPlayer = true`, el SDK reemplaza temporalmente el contenido completo de la Activity con solo el player antes de entrar en PiP. Al salir de PiP, restaura el layout original.

```
Sin la opcion (player en contenedor pequeno):
┌─────────────┐    PiP    ┌───┐
│ Header      │  ──────►  │H P│  ← todo comprimido, video irreconocible
│ ┌─────────┐ │           │S v│
│ │ Player  │ │           └───┘
│ └─────────┘ │
│ Scroll...   │
└─────────────┘

Con pipReplaceActivityContentWithPlayer = true:
┌─────────────┐    PiP    ┌───┐
│ Header      │  ──────►  │   │  ← solo el video, limpio
│ ┌─────────┐ │           │ V │
│ │ Player  │ │           │   │
│ └─────────┘ │           └───┘
│ Scroll...   │
└─────────────┘
```

### `pipExpandToFullscreenFirst`

Entra en fullscreen primero y luego en PiP. Util para asegurar que el video ocupa toda la pantalla antes de la transicion. Esta opcion ya existia antes de v10.0.0 pero se formaliza como campo de config.

---

## Cambio de API en callback — BREAKING CHANGE

### Firma actual (v9.9.0)
```kotlin
// En MediastreamPlayerCallback
fun onFullscreen()
```

### Firma nueva (v10.0.0)
```kotlin
// En la interfaz — con valor default para retrocompatibilidad Kotlin
fun onFullscreen(enteredForPip: Boolean = false)

// En las implementaciones — SIN valor default (requerido por Kotlin)
override fun onFullscreen(enteredForPip: Boolean) {
    if (enteredForPip) {
        // El fullscreen se abrio SOLO para preparar la entrada a PiP
        // NO forzar rotacion a landscape en este caso
        return
    }
    // Fullscreen abierto por el usuario (boton) — comportamiento normal
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}
```

### Que significa `enteredForPip`

| Valor | Significado | Accion recomendada |
|-------|-------------|-------------------|
| `true` | El SDK entro en fullscreen SOLO para preparar PiP (`pipExpandToFullscreenFirst`) | NO forzar rotacion a landscape |
| `false` | Fullscreen abierto por el usuario (boton de fullscreen) | Comportamiento normal (rotar si se desea) |

---

## Impacto en integradores — lista de cambios requeridos

Cuando llegue v10.0.0, **todos los integradores del SDK deben**:

1. **Actualizar la firma de `onFullscreen`** en todas las implementaciones de `MediastreamPlayerCallback`:
   ```kotlin
   // ANTES (v9.9.0)
   override fun onFullscreen() {
       requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
   }

   // DESPUES (v10.0.0)
   override fun onFullscreen(enteredForPip: Boolean) {
       if (enteredForPip) return  // no rotar para PiP interno
       requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
   }
   ```

2. **Archivos del sample app que requieren update** (todos usan `onFullscreen()` sin parametro):
   - `VideoLiveActivity.kt`
   - `VideoOnDemandActivity.kt`
   - `VideoLocalLiveVodActivity.kt`
   - `LiveMediastreamUIActivity.kt`
   - `LiveWithReloadButtonsActivity.kt`
   - `NextEpisodeActivity.kt`
   - `AudioOnDemandAsServiceActivity.kt`
   - `AudioWithSyncServiceActivity.kt`
   - `EpisodeAudioAsServiceActivity.kt`
   - `LiveAudioAsServiceActivity.kt`
   - `MediastreamPlayerService.kt` (implementacion interna del SDK)
   - `MediastreamPlayerServiceWithSync.kt` (implementacion interna del SDK)

3. **Agregar nueva config si se usa player en contenedor pequeno:**
   ```kotlin
   config.pipReplaceActivityContentWithPlayer = true
   ```

---

## Tests a agregar cuando llegue v10.0.0

- [ ] `onFullscreen(enteredForPip=true)` se dispara cuando `pipExpandToFullscreenFirst=true`
- [ ] `onFullscreen(enteredForPip=false)` se dispara cuando el usuario presiona el boton de fullscreen
- [ ] Con `pipReplaceActivityContentWithPlayer=true`: solo el player es visible en la ventana PiP
- [ ] Con `pipReplaceActivityContentWithPlayer=false`: toda la Activity se encoge (comportamiento anterior)
- [ ] Al salir de PiP con `pipReplaceActivityContentWithPlayer=true`: el layout original se restaura correctamente
- [ ] Retrocompatibilidad: implementaciones con `onFullscreen()` sin parametro no rompen en v10.0.0

---

*Cambio pendiente: v10.0.0 | Documentado: 2026-04-16 | Fuente: context/pip-implementation.md en el repo del SDK*
