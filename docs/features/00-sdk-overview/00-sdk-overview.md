# 00 — Vision General del SDK

## Que es

**Mediastream Platform SDK Android** es una libreria de reproduccion de medios para Android basada en ExoPlayer/Media3. Se publica en Maven Central y encapsula la integracion con la plataforma Mediastream: carga de configuracion via API, reproduccion adaptativa, anuncios, analytics, DRM, DVR, y soporte multi-plataforma.

**Coordenadas Maven:**
```
io.github.mediastream:mediastreamplatformsdkandroid:9.9.0
```

---

## Arquitectura general

```
┌─────────────────────────────────────────────────────┐
│                   App del cliente                    │
├─────────────────────────────────────────────────────┤
│              MediastreamPlayer (fachada)             │
├──────────────┬──────────────┬────────────────────────┤
│  ExoPlayer   │  ApiService  │  Analytics             │
│  (Media3)    │  (Retrofit)  │  (Comscore / Youbora)  │
├──────────────┼──────────────┼────────────────────────┤
│  IMA Ads     │  Cast SDK    │  DRM (Widevine)        │
└──────────────┴──────────────┴────────────────────────┘
```

El flujo tipico de inicializacion es:

1. El cliente crea un `MediastreamPlayerConfig` con el `id` del contenido y `accountID`
2. `MediastreamPlayer` llama a `ApiService.getMediaInfo()` para obtener el `ConfigMain` (metadatos, URLs, config de ads, analytics, etc.)
3. Se construye el `ExoPlayer` con las fuentes y configuraciones obtenidas
4. Se disparan callbacks al cliente via `MediastreamPlayerCallback`

---

## Modulos del proyecto

| Modulo | Tipo | Descripcion |
|--------|------|-------------|
| `mediastreamplatformsdkandroid` | Android Library | SDK principal, se publica en Maven |
| `app` | Android Application | App de ejemplo y testing manual |

---

## Dependencias principales

| Libreria | Version | Rol |
|----------|---------|-----|
| ExoPlayer / Media3 | 1.5.0 | Motor de reproduccion |
| Retrofit2 | 2.9.0 | Cliente HTTP para la API |
| OkHttp3 | 4.10.0 | HTTP + logging + SSE |
| Gson | 2.10.1 | Deserializacion JSON |
| Comscore | 6.12.0 | Analytics de streaming |
| ITG SDK | 2.6.38-mediastream | Analytics in-game |
| NPAW / Youbora adapter | 6.8.7 | Analytics de calidad |
| Google IMA | via Media3 | Publicidad (VAST/VMAP/DAI) |
| Google Cast | via Media3 | Chromecast |
| Google Cronet | 18.0.1 | HTTP/QUIC |
| Glide | 4.13.2 | Carga de imagenes |
| EventBus | 3.3.1 | Comunicacion interna entre componentes |

---

## Plataformas soportadas

| Plataforma | Soporte |
|------------|---------|
| Smartphones y tablets | Completo |
| Android TV | Completo (UI especifica) |
| Fire TV | Completo |
| Android Automotive | Parcial (Auto tab, podcast) |
| Chromecast | Via Cast SDK |

**SDK minimo:** API 24 (Android 7.0)
**Target SDK:** 35

---

## Clases publicas principales

| Clase | Descripcion |
|-------|-------------|
| `MediastreamPlayer` | Punto de entrada. Inicializa y controla el player |
| `MediastreamPlayerConfig` | Configuracion de reproduccion (50+ propiedades) |
| `MediastreamPlayerCallback` | Interfaz de eventos/callbacks (35+ metodos) |
| `MediastreamMiniPlayerConfig` | Configuracion del mini-player flotante |
| `MediastreamPlayerPip` | Control de Picture-in-Picture |

---

## Tipos de contenido

| Tipo | Enum | Descripcion |
|------|------|-------------|
| Video bajo demanda | `VideoTypes.VOD` | Archivo de video con inicio/fin |
| Live stream | `VideoTypes.LIVE` | Stream en vivo, puede tener DVR |
| Episodio | `VideoTypes.EPISODE` | VOD con metadata de serie, soporte next/prev |

---

## Formatos de streaming

| Formato | Enum | Protocolo |
|---------|------|-----------|
| HLS (default) | `AudioVideoFormat.DEFAULT` | HTTP Live Streaming |
| DASH | `AudioVideoFormat.DASH` | Dynamic Adaptive Streaming |
| MP4 | `AudioVideoFormat.MP4` | Archivo directo |
| MP3 | `AudioVideoFormat.MP3` | Audio directo |
| M4A | `AudioVideoFormat.M4A` | Audio AAC |
| ICECAST | `AudioVideoFormat.ICECAST` | Radio por internet |

---

## Entornos

| Entorno | Enum | Uso |
|---------|------|-----|
| Produccion | `Environment.PRODUCTION` | Default, API real |
| Desarrollo | `Environment.DEV` | Testing contra API dev |

---

## Notas criticas para testing

- `msPlayer` (instancia ExoPlayer) **puede ser null** — siempre verificar antes de llamar metodos directos
- El SDK llama a la API en inicializacion — los tests de integracion deben mockear `ApiService`
- Los callbacks son asincronos — usar `runTest` + coroutines test en unit tests
- ExoPlayer requiere contexto Android real — usar Robolectric o emulador para integration tests
- El DVR fictitious timeline tiene logica propia de offsets — feature de alto riesgo

---

*Feature: 00-sdk-overview | SDK v9.9.0 | 2026-04-16*
