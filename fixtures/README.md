# Fixtures

Respuestas JSON reales capturadas de la API de Mediastream. Usadas en integration tests.

## Estado actual

| Fixture | Estado | Capturado |
|---------|--------|----------|
| vod/vod-basic.json | ⬜ PENDIENTE | — |
| vod/vod-with-preroll.json | ⬜ PENDIENTE | — |
| vod/vod-with-drm.json | ⬜ PENDIENTE | — |
| vod/vod-with-subtitles.json | ⬜ PENDIENTE | — |
| live/live-basic.json | ⬜ PENDIENTE | — |
| live/live-with-dvr.json | ⬜ PENDIENTE | — |
| episode/episode-with-next.json | ⬜ PENDIENTE | — |
| episode/episode-last.json | ⬜ PENDIENTE | — |
| audio/audio-live.json | ⬜ PENDIENTE | — |
| audio/audio-podcast.json | ⬜ PENDIENTE | — |
| errors/401-unauthorized.json | ⬜ PENDIENTE | — |
| errors/404-not-found.json | ⬜ PENDIENTE | — |
| errors/500-server-error.json | ⬜ PENDIENTE | — |

## Como capturar

Ver [docs/testing/fixtures-guide.md](../docs/testing/fixtures-guide.md)

## Reglas

- Sin tokens ni credenciales reales en los JSON
- No editar fixtures existentes (crear nuevos con sufijo descriptivo)
- Un fixture = un escenario especifico
