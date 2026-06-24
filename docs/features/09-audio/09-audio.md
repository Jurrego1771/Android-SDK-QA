# 07 — Audio (Live, Podcast, Episode)

## Descripcion

El SDK soporta reproduccion de audio en tres modalidades: live (radio), podcast (VOD audio), y episodios de audio. La diferencia con video es que no hay superficie de video — solo controles y notificacion.

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "AUDIO_ID"
config.accountID = "ACCOUNT_ID"
config.playerType = MediastreamPlayerConfig.PlayerType.AUDIO  // CLAVE

// Para live radio
config.type = MediastreamPlayerConfig.VideoTypes.LIVE
config.videoFormat = MediastreamPlayerConfig.AudioVideoFormat.ICECAST  // o DEFAULT para HLS

// Para podcast (VOD)
config.type = MediastreamPlayerConfig.VideoTypes.VOD
config.videoFormat = MediastreamPlayerConfig.AudioVideoFormat.MP3

// Para episodio de audio
config.type = MediastreamPlayerConfig.VideoTypes.EPISODE
config.loadNextAutomatically = true
```

## Formatos de audio soportados

| Formato | Enum | Uso tipico |
|---------|------|-----------|
| Icecast | `ICECAST` | Radio en vivo HTTP |
| HLS | `DEFAULT` | Radio/podcast en HLS |
| MP3 | `MP3` | Podcast MP3 directo |
| M4A / AAC | `M4A` | Podcast M4A |

## Metadata en vivo — `onLiveAudioCurrentSongChanged`

Para streams de audio en vivo, el SDK escucha SSE (Server-Sent Events) para obtener la cancion actual:

```kotlin
override fun onLiveAudioCurrentSongChanged(data: JSONObject?) {
    data?.let {
        val title = it.optString("title")
        val artist = it.optString("artist")
        val artwork = it.optString("artwork")
        updateNowPlayingUI(title, artist, artwork)
    }
}
```

## Diferencias con video

| Aspecto | Video | Audio |
|---------|-------|-------|
| Superficie visual | PlayerView visible | PlayerView oculto/inexistente |
| Notificacion | Opcional | Critica para control en background |
| Background | Opcional | Requiere ForegroundService |
| Gestos | Zoom, swipe | No aplica |

## Reproduccion en background

El audio en background requiere `MediastreamPlayerService` o `MediastreamPlayerServiceWithSync` declarados en el Manifest. El SDK maneja esto internamente cuando `playerType = AUDIO`.

## Testing — Escenarios a cubrir

- [ ] Audio live reproduce correctamente sin superficie de video
- [ ] Audio podcast VOD reproduce y termina correctamente
- [ ] `onLiveAudioCurrentSongChanged` se dispara con metadata correcta
- [ ] Reproduccion continua al ir a background (home button)
- [ ] Notificacion de media aparece con controles funcionales
- [ ] Pausa/play desde la notificacion funciona
- [ ] Formato ICECAST reproduce correctamente
- [ ] Audio episodio carga siguiente episodio correctamente

---

*Feature: 07-audio | SDK v9.9.0 | 2026-04-16*
