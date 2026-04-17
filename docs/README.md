# SDK Android QA — Documentación

Indice central del repositorio de testing y documentacion del **Mediastream Platform SDK Android** (v9.9.0).

---

## Estructura

```
docs/
├── features/          <- Documentacion por feature del SDK
├── api/               <- Referencia de la API publica
├── integration/       <- Guias de integracion y setup
└── testing/           <- Estrategia y guias de testing

risk-map/
├── RISK_MAP.md        <- Mapa de riesgos (actualizable)
└── COVERAGE_TRACKER.md <- Tracker de cobertura por feature

fixtures/              <- Respuestas JSON reales de la API para tests
```

---

## Features documentadas

| # | Feature | Archivo | Estado |
|---|---------|---------|--------|
| 00 | Vision general del SDK | [features/00-sdk-overview.md](features/00-sdk-overview.md) | ✅ |
| 01 | Inicializacion del player | [features/01-initialization.md](features/01-initialization.md) | ✅ |
| 02 | Configuracion (PlayerConfig) | [features/02-player-config.md](features/02-player-config.md) | ✅ |
| 03 | Callbacks y eventos | [features/03-callbacks.md](features/03-callbacks.md) | ✅ |
| 04 | Video VOD | [features/04-video-vod.md](features/04-video-vod.md) | ✅ |
| 05 | Video Live | [features/05-video-live.md](features/05-video-live.md) | ✅ |
| 06 | Episodes y autoplay | [features/06-video-episode.md](features/06-video-episode.md) | ✅ |
| 07 | Audio (live, podcast, episode) | [features/07-audio.md](features/07-audio.md) | ✅ |
| 08 | DVR y fictitious timeline | [features/08-dvr.md](features/08-dvr.md) | ✅ |
| 09 | DRM | [features/09-drm.md](features/09-drm.md) | ✅ |
| 10 | Ads — Google IMA / SSAI / DAI | [features/10-ads-ima.md](features/10-ads-ima.md) | ✅ |
| 11 | Analytics — Comscore | [features/11-analytics-comscore.md](features/11-analytics-comscore.md) | ✅ |
| 12 | Analytics — Youbora / NPAW | [features/12-analytics-youbora.md](features/12-analytics-youbora.md) | ✅ |
| 13 | Google Cast / Chromecast | [features/13-chromecast.md](features/13-chromecast.md) | ✅ |
| 14 | Picture-in-Picture | [features/14-pip.md](features/14-pip.md) | ✅ |
| 15 | Android TV / Fire TV | [features/15-android-tv.md](features/15-android-tv.md) | ✅ |
| 16 | Android Auto / Automotive | [features/16-android-auto.md](features/16-android-auto.md) | ✅ |
| 17 | Subtitulos (ASS/SSA, WebVTT) | [features/17-subtitles.md](features/17-subtitles.md) | ✅ |
| 18 | Descarga offline | [features/18-download.md](features/18-download.md) | ✅ |
| 19 | Notificaciones de media | [features/19-notifications.md](features/19-notifications.md) | ✅ |
| 20 | Personalizacion de UI | [features/20-ui-customization.md](features/20-ui-customization.md) | ✅ |
| 21 | Services (PlayerService + WithSync) | [features/21-services.md](features/21-services.md) | ✅ |

**Estado:** ✅ Documentado | 🚧 En progreso | ⬜ Pendiente

---

## API Reference

- [MediastreamPlayer](api/MediastreamPlayer.md)
- [MediastreamPlayerConfig](api/MediastreamPlayerConfig.md)
- [MediastreamPlayerCallback](api/MediastreamPlayerCallback.md)
- [ApiService](api/ApiService.md)

---

## Testing

- [Estrategia de Testing](testing/test-strategy.md)
- [Guia de Fixtures](testing/fixtures-guide.md)
- [Workflow con IA](testing/ai-workflow.md)

---

## Risk Map

- [RISK_MAP.md](../risk-map/RISK_MAP.md) — Mapa de riesgos y prioridades
- [COVERAGE_TRACKER.md](../risk-map/COVERAGE_TRACKER.md) — Cobertura actual por feature

---

*SDK version: 9.9.0 | Ultima actualizacion: 2026-04-16*
