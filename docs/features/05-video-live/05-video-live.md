# 05 — Video Live

## Descripcion

Reproduccion de streams en vivo. No tiene inicio/fin fijo. Puede combinarse con DVR (ver feature 08).

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "LIVE_CHANNEL_ID"
config.accountID = "ACCOUNT_ID"
config.type = MediastreamPlayerConfig.VideoTypes.LIVE
config.autoplay = true
config.dvr = false          // sin DVR: el usuario siempre esta en el borde del live
```

## Diferencias con VOD

| Aspecto | VOD | Live |
|---------|-----|------|
| Seekbar | Posicion 0 a duracion | Sin seekbar (o DVR timeline) |
| `onEnd` | Se dispara al terminar | No se dispara (stream continuo) |
| `startAt` | Funciona | Ignorado (siempre live edge) |
| `loop` | Funciona | Ignorado |
| DVR | No aplica | Opcional |

## Reconexion automatica

Si el stream se interrumpe temporalmente, ExoPlayer/SDK intenta reconectar automaticamente. Si la reconexion falla indefinidamente, se dispara `onError`.

## Control de live edge

El usuario siempre inicia en el borde del live (live edge). Si el servidor no emite, el player entra en estado de rebuffering y dispara `onBuffering`.

## Testing — Escenarios a cubrir

- [ ] Live carga y reproduce correctamente
- [ ] No hay seekbar visible (sin DVR)
- [ ] `onEnd` NO se dispara durante reproduccion normal
- [ ] `onBuffering` se dispara cuando el servidor no emite
- [ ] Reconexion automatica tras perdida temporal de red
- [ ] `onError` tras fallo de reconexion prolongado
- [ ] Live con stream caido desde el inicio → error apropiado
- [ ] Cambio de calidad adaptativo (ABR) no interrumpe reproduccion

---

*Feature: 05-video-live | SDK v9.9.0 | 2026-04-16*
