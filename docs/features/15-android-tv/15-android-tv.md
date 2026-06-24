# 15 — Android TV / Fire TV

## Descripcion

El SDK detecta automaticamente cuando se ejecuta en Android TV o Fire TV y adapta la UI: controles optimizados para control remoto, dialogos de seleccion de tracks/subtitulos para TV, y navegacion por D-pad.

## Deteccion del dispositivo

```kotlin
// Metodos estaticos disponibles en el SDK
MediastreamPlayer.isAndroidTV(context)          // Boolean
MediastreamPlayer.isFireTV()                     // Boolean
MediastreamPlayer.isSmartphoneOrTablet(context) // Boolean
MediastreamPlayer.getDeviceType(context)         // String: "smart-tv", "mobile", etc.
MediastreamPlayer.getMaxSupportedResolution(context) // String
```

## Diferencias de UI en TV

| Elemento | Mobile | TV |
|----------|--------|----|
| Controles | Touch | D-pad optimizado |
| Dialogo de tracks | `TrackSelectionDialog` | `TVSubtitleAudioDialog` |
| Layouts | `layout/` | `layout-television/` |
| Fullscreen | Toggle manual | Siempre fullscreen |
| Gestos | Zoom, swipe | No aplica |

## Configuracion especifica para TV

No se requiere configuracion especial — el SDK detecta el tipo de dispositivo automaticamente. Sin embargo, se recomienda:

```kotlin
config.showBrightnessBar = false  // no relevante en TV
config.showFullScreenButton = false  // siempre fullscreen en TV
```

## Android Leanback

El SDK declara en su Manifest que no requiere hardware tactil:
```xml
<!-- En AndroidManifest.xml del SDK -->
<!-- tools:ignore="MissingLeanbackLauncher,ImpliedTouchscreenHardware" -->
```

## Fire TV

Fire TV se detecta via `Build.MANUFACTURER` y `Build.MODEL`. El comportamiento es identico a Android TV.

## Testing — Escenarios a cubrir

- [ ] `isAndroidTV()` devuelve `true` en emulador de TV
- [ ] `isAndroidTV()` devuelve `false` en emulador de movil
- [ ] `isFireTV()` devuelve `true` en Fire TV (requiere dispositivo fisico o emulador especifico)
- [ ] UI de TV usa `TVSubtitleAudioDialog` en lugar de `TrackSelectionDialog`
- [ ] Navegacion con D-pad funciona correctamente
- [ ] Los layouts de `layout-television/` se inflan correctamente en TV

---

*Feature: 15-android-tv | SDK v9.9.0 | 2026-04-16*
