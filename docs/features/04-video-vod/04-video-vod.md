# 04 — Video on Demand (VOD)

## Descripcion

Reproduccion de archivos de video con inicio y fin definidos. El tipo mas basico del SDK.

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "VOD_ID"
config.accountID = "ACCOUNT_ID"
config.type = MediastreamPlayerConfig.VideoTypes.VOD
config.environment = MediastreamPlayerConfig.Environment.PRODUCTION
config.autoplay = true
config.startAt = 0         // segundos, -1 = desde el inicio
config.loop = false
```

## Comportamiento esperado

| Accion | Resultado |
|--------|-----------|
| Inicializacion | API devuelve metadata + URL del stream |
| Autoplay=true | `onPlay()` se dispara automaticamente tras `onReady()` |
| Usuario pausa | `onPause()` callback |
| Usuario reanuda | `onPlay()` callback |
| Llega al final | `onEnd()` callback |
| `loop=true` + llega al final | Vuelve al inicio, NO dispara `onEnd()` |
| `startAt=120` | Inicia en el segundo 120 |

## Control directo via ExoPlayer

```kotlin
// Acceso al player nativo (verificar null siempre)
player.msPlayer?.let { exo ->
    exo.seekTo(30_000L)    // seek a 30 segundos (en milisegundos)
    exo.pause()
    exo.play()
    val duration = exo.duration    // duracion total en ms
    val position = exo.currentPosition  // posicion actual en ms
}
```

## Formatos soportados

- HLS (.m3u8) — DEFAULT
- DASH (.mpd)
- MP4 directo

## Testing — Escenarios a cubrir

- [ ] VOD carga y reproduce correctamente
- [ ] `onReady` → `onPlay` en secuencia correcta con autoplay=true
- [ ] `autoplay=false` no dispara `onPlay` automaticamente
- [ ] `startAt=60` posiciona el player en el segundo 60
- [ ] `loop=true` reinicia al llegar al final
- [ ] `onEnd` se dispara al llegar al final (loop=false)
- [ ] Seek al inicio, mitad y final del contenido
- [ ] VOD con `src` directo (sin llamada a API)
- [ ] API devuelve 404 → `onEmbedErrors` callback
- [ ] API devuelve contenido con URL de stream invalida → `onError`

---

*Feature: 04-video-vod | SDK v9.9.0 | 2026-04-16*
