# 20 — Personalizacion de UI

## Descripcion

El SDK permite personalizar la interfaz del player: controles, colores, logo, watermark, y la posibilidad de inyectar un `PlayerView` completamente custom.

## Opciones de configuracion de UI

```kotlin
val config = MediastreamPlayerConfig()
config.showControls = true            // mostrar/ocultar controles
config.showFullScreenButton = true    // boton de fullscreen
config.showDismissButton = false      // boton de cerrar/minimizar
config.showBrightnessBar = true       // barra de control de brillo (swipe)
config.language = MediastreamPlayerConfig.Language.SPANISH  // idioma de la UI
config.customPlayerView = myPlayerView  // PlayerView personalizado (override total)
```

## PlayerView personalizado

```kotlin
// Crear un PlayerView personalizado
val customView = PlayerView(context)
customView.useController = true
// ... personalizar el view

config.customPlayerView = customView
// El SDK usara este view en lugar del default
```

## Personalizacion desde la API

El objeto `ConfigMain` puede incluir configuraciones de UI que vienen de la plataforma:
- `template` — template de player
- `style` — estilos CSS-like del player
- `logo` — configuracion del logo superpuesto
- `watermark` — watermark del contenido
- `thumbnail` — thumbnail para el loading/poster

## Clases de UI del SDK

| Clase | Descripcion |
|-------|-------------|
| `MediastreamCustomPlayerControlView` | Controles del player personalizados |
| `DvrTimeBar` | Barra de tiempo para DVR |
| `TrackSelectionDialog` | Dialogo de seleccion de tracks (mobile) |
| `TVSubtitleAudioDialog` | Dialogo de tracks para TV |
| `PlayerZoomGestureListener` | Gestos de zoom con pinch |
| `MediastreamPlayerCustomizer` | Helper para aplicar customizaciones |

## Idiomas soportados

| Idioma | Enum | Strings incluidos |
|--------|------|------------------|
| Ingles | `ENGLISH` | `values/strings.xml` |
| Espanol | `SPANISH` | `values-es/strings.xml` |
| Portugues | `PORTUGUESE` | `values-pt/strings.xml` |

## Gestos soportados (mobile)

- **Pinch** — zoom in/out del video
- **Swipe vertical izquierda** — control de brillo
- **Swipe vertical derecha** — control de volumen

## Testing — Escenarios a cubrir

- [ ] `showControls=false` oculta todos los controles
- [ ] `showFullScreenButton=false` oculta solo el boton de fullscreen
- [ ] `showDismissButton=true` muestra el boton y dispara `onDismissButton()`
- [ ] `language=SPANISH` muestra textos en espanol
- [ ] `language=PORTUGUESE` muestra textos en portugues
- [ ] `customPlayerView` no null: el SDK usa el view provisto
- [ ] Gesto de pinch aplica zoom correctamente
- [ ] Brillo y volumen se controlan con swipe vertical

---

*Feature: 20-ui-customization | SDK v9.9.0 | 2026-04-16*
