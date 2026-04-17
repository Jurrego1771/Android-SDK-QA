# Feature → Test Matrix

> Mapa de qué features tienen tests y en qué archivos.
> Actualizar cuando se agregan tests. Ver también `risk-map/COVERAGE_TRACKER.md`.

---

## Estado al 2026-04-16

### Tests Existentes

| Archivo | Feature | Tipo | IDs de Test |
|---------|---------|------|-------------|
| `integration/VodIntegrationTest.kt` | VOD (src directo) | Integration | INT-VOD-01..05 |
| `integration/AudioIntegrationTest.kt` | Audio | Integration | — |
| `integration/AudioFocusTest.kt` | Audio Focus | Integration | — |
| `integration/EpisodeNextTest.kt` | Episodios / Next | Integration | — |
| `integration/ErrorRecoveryTest.kt` | Error Recovery | Integration | — |
| `integration/LifecycleTest.kt` | Lifecycle | Integration | — |
| `integration/PlayerLifecycleTest.kt` | Player Lifecycle | Integration | — |
| `smoke/SmokeTest.kt` | Smoke / Basic | Smoke | — |

### Features Sin Tests (prioridad del Risk Map)

| Feature | Riesgo | Archivo sugerido | Notas |
|---------|--------|-----------------|-------|
| Inicialización con `id` + API | 🔴 CRÍTICO | `integration/VodApiTest.kt` | Requiere accountID + network |
| Manejo error HTTP (4xx/5xx) | 🔴 CRÍTICO | `integration/ErrorHandlingTest.kt` | |
| Orden correcto de callbacks | 🔴 CRÍTICO | `integration/CallbackOrderTest.kt` | |
| DVR seek hacia atrás | 🔴 CRÍTICO | `integration/DvrTest.kt` | Stream live con DVR |
| DVR offset (fictitious timeline) | 🔴 CRÍTICO | `integration/DvrTest.kt` | |
| Preroll IMA | 🔴 CRÍTICO | `integration/AdsTest.kt` | Requiere adURL |
| Error de ad → contenido continua | 🔴 CRÍTICO | `integration/AdsTest.kt` | URL de ad inválida |
| Background audio playback | 🔴 CRÍTICO | `integration/AudioBackgroundTest.kt` | Requiere UI Automator |
| Next episode modo manual: timing | 🔴 CRÍTICO | `integration/EpisodeManualTest.kt` | |
| Next episode modo manual: solo con updateNextEpisode | 🔴 CRÍTICO | `integration/EpisodeManualTest.kt` | |
| startPiP() sin Activity | 🔴 CRÍTICO | `integration/PipTest.kt` | |
| Service forwardea nextEpisodeIncoming | 🔴 CRÍTICO | `integration/ServiceTest.kt` | |

---

## Escenarios de Activities (Main App)

| Activity | Feature | Scenario |
|----------|---------|---------|
| `VideoVodScenarioActivity` | VOD Video | Reproducción VOD básica |
| `VideoLiveScenarioActivity` | Live Video | Reproducción live |
| `VideoLiveDvrScenarioActivity` | Live + DVR | Live con seekbar DVR |
| `VideoEpisodeApiScenarioActivity` | Episode API | Episodios modo automático |
| `VideoEpisodeCustomScenarioActivity` | Episode Manual | Episodios modo manual |
| `VideoAdsScenarioActivity` | Ads IMA | Preroll con IMA |
| `VideoDrmScenarioActivity` | DRM | Contenido con Widevine |
| `VideoPipScenarioActivity` | PiP | Picture-in-Picture |
| `VideoReelsScenarioActivity` | Reels | Player de reels |
| `VideoContentSwitcherActivity` | Content Switch | Cambio de contenido |
| `VideoWithServiceScenarioActivity` | Service | Player con MediastreamPlayerService |
| `AudioVodScenarioActivity` | Audio VOD | Podcast |
| `AudioLiveScenarioActivity` | Audio Live | Radio |
| `AudioEpisodeScenarioActivity` | Audio Episode | Episodio de audio |
| `AudioWithServiceScenarioActivity` | Audio Service | Audio en background |

---

## Carpetas de Tests (destino para test generados)

```
app/src/androidTest/java/com/example/sdk_qa/
├── smoke/
│   └── SmokeTest.kt
├── integration/
│   ├── VodIntegrationTest.kt     ← existente
│   ├── AudioIntegrationTest.kt   ← existente
│   ├── AudioFocusTest.kt         ← existente
│   ├── EpisodeNextTest.kt        ← existente
│   ├── ErrorRecoveryTest.kt      ← existente
│   ├── LifecycleTest.kt          ← existente
│   ├── PlayerLifecycleTest.kt    ← existente
│   ├── VodApiTest.kt             ← por crear
│   ├── CallbackOrderTest.kt      ← por crear
│   ├── DvrTest.kt                ← por crear
│   ├── AdsTest.kt                ← por crear
│   ├── AudioBackgroundTest.kt    ← por crear
│   ├── EpisodeManualTest.kt      ← por crear
│   └── PipTest.kt                ← por crear
└── utils/
    ├── SdkTestExtensions.kt      ← existente
    └── SdkTestRule.kt            ← existente
```
