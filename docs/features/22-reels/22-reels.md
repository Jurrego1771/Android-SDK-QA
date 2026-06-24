# 22 — Reels (ReelsV2)

## Descripcion

Feed vertical de video corto a pantalla completa, estilo **TikTok / Instagram Reels / YouTube
Shorts**. El usuario hace swipe vertical para navegar entre clips; cada clip se reproduce en loop,
con autoplay al entrar en foco, controles minimos (mute, dismiss) y carga anticipada del contenido
adyacente. El SDK fetchea dinamicamente los "related reels" del player configurado en plataforma,
formando un feed de scroll potencialmente infinito.

Es un modo de reproduccion distinto del player normal: no es una sola Activity con un `MediastreamPlayer`,
sino un `ViewPager2` vertical gestionado por `ReelsV2Handler`, con un **pool de ExoPlayers reutilizables**.

> Fuente (SDK): `reelsv2/ReelsV2Handler.kt`, `reelsv2/ReelsContentManager.kt`,
> `reelsv2/PlayerPool.kt`, `reelsv2/viewpager/ViewPagerMediaAdapter.kt` y `ViewPagerMediaHolder.kt`.
> QA: `VideoReelsScenarioActivity` (full-screen, sin debug panel; deep link `sdkqa://scenario/reels`).

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id        = "MEDIA_ID"      // primer reel del feed
config.playerId  = "PLAYER_ID"     // OBLIGATORIO — player de reels configurado en plataforma
config.accountID = "ACCOUNT_ID"
config.type      = MediastreamPlayerConfig.VideoTypes.VOD
config.environment = MediastreamPlayerConfig.Environment.PRODUCTION
config.autoplay  = true
```

El **modo reels se activa por la plataforma**, no por un flag local: el SDK entra en modo reels
cuando la respuesta de config trae `playerSkin == "reels"` o `type == "REELS"`
(`MediastreamPlayer.shouldActivateReels`). `playerId` es **obligatorio**: sin el, el endpoint de
related reels (`?player=<playerId>`) no puede paginar el feed.

Efectos colaterales al activar (`ReelsV2Handler.activate`):
- Fuerza orientacion **PORTRAIT**.
- Borra el `accessToken` del config (para no exponerlo en las URLs de cada reel).
- Infla `reelsv2_view_pager.xml` y monta el `ViewPager2` vertical.

> **Comportamiento esperado:** los criterios de aceptación (given/when/then) son la fuente de
> verdad y viven en [`user-stories.yaml`](./user-stories.yaml). La cobertura de tests y sus gaps,
> en [`tests.yaml`](./tests.yaml). Esta sección documenta solo la *referencia* (comparación con el
> mercado y la superficie de callbacks).

## Comparación con el estándar de mercado (TikTok / Instagram Reels / YouTube Shorts)

- **= igual al estándar**: swipe vertical entre clips, autoplay al entrar en foco, loop del clip
  actual (no auto-avanza), tap para pausar, audio con sonido por defecto, sin scrubbing en la
  barra de progreso, precarga del contenido adyacente, ads intersticiales cada N clips.
- **⚠ propio del SDK (no estándar)**: botón "ojo" (visibility) que oculta/muestra la metadata
  del reel — las apps de referencia no exponen este toggle.
- **⚠ a vigilar**: el estado de mute **no persiste** entre aperturas del feed (las apps de
  mercado suelen recordar la preferencia de audio) — ver `risks.yaml`.

## Callbacks (referencia — solo para el reel activo)

Los callbacks **solo se disparan para el reel en foco** (`isActiveReel`); los reels precargados
fuera de pantalla NO emiten eventos.

| Callback | Cuando |
|----------|--------|
| `onReady` | El player del reel activo alcanza READY |
| `onPlay` / `onPause` | `onIsPlayingChanged` del reel activo |
| `onBuffering` | STATE_BUFFERING del reel activo |
| `onEnd` | STATE_ENDED (antes de loopear) |
| `onNext` / `onPrevious` | Swipe a una posicion mayor / menor |
| `onError` / `onPlaybackErrors` | Error de reproduccion (solo video regular, no ads) |
| `onDismissButton` | Tap en el boton de cerrar |
| `onAdEvents` / `onAdErrorEvent` | Eventos del ad intersticial (IMA) |
| `onPlayerClosed` | Al liberar el handler |

## Arquitectura de reproduccion

- **`ViewPager2` vertical** (`ORIENTATION_VERTICAL`) con un `RecyclerView` interno.
- **PlayerPool**: pool singleton de **6 ExoPlayers reutilizables**. Al hacer swipe se reasigna la
  misma instancia (`stop()` + `clearMediaItems()`), no se crea/libera por reel. Cada player usa
  `REPEAT_MODE_ONE` (loop infinito del reel).
- **Precarga**: `offscreenPageLimit = 2` (mantiene 2 holders en RAM); `preloadDistance = 2`
  (prefetch de configs en paralelo).

## Paginacion del feed

- **Related Reels API**: `GET {base}/api/media/{mediaId}/related/reels?player={playerId}&display={n}&dnt=true`
  → `RelatedReelsResponse { medias: [{id,title,thumbnail,url}], display }`.
- **Config por reel**: `GET {base}/video/{mediaId}.json?player={playerId}` (paralelo, `awaitAll`).
- **Cola pendiente**: los items sobrantes de una llamada Related se guardan y se consumen sin
  re-llamar a la API.
- **Trigger de fetch**: en `onPageSelected`, si `nonAdItemsAhead < preloadDistance` se pide el
  siguiente batch. Reels con `srcList` vacio se descartan.
- **Fin del feed**: cuando Related API devuelve menos items que `display`.

## Ads en reels

- `ReelsAdsConfigModel { showTitle, showDescription, url (VAST/VMAP), interval }`.
- Se inserta un ad cada `interval` videos (`createAdReelItem`, `setAdTagUri`).
- Player de ad **dedicado** (no del pool) + `ImaAdsLoader` separado; soporta **PPID**.
- `ALL_ADS_COMPLETED` → auto-swipe al siguiente reel. Si el ad falla → se elimina el slot y se
  muestra el siguiente contenido en su lugar.

## Estado / preferencias

`ReelsPreferencesManager` (singleton **en memoria, no persistente** — se pierde al cerrar):
- `isMuted = false` (arranca con sonido).
- `isMetadataVisible = true` (titulo/desc/tags visibles).

Ambos toggles se aplican **globalmente a todos los players** del feed.

## Comportamiento documentado en los unit tests del SDK

`reelsv2/ReelsV2LogicTests.kt`:
- `DynamicMediaProvider` mantiene la cola en orden FIFO (`getItemCount`, `getLastItemId`, `getReelItem`).
- Diferencia ads de videos: `getNonAdItemCount` excluye ads y `getLastItemId` **nunca** devuelve un ad
  (los ads no entran en las llamadas a Related API).
- Formato de fecha: ISO 8601 → `MM-dd-yyyy`.

## Testing

Criterios de aceptación (given/when/then) → [`user-stories.yaml`](./user-stories.yaml).
Gaps de cobertura y specs sugeridos → [`tests.yaml`](./tests.yaml).
Riesgos → [`risks.yaml`](./risks.yaml) · Defectos → [`defects.yaml`](./defects.yaml).

---

*Feature: 22-reels | SDK v10.0.7 | 2026-06-23 | Comportamiento de referencia: TikTok / Instagram Reels / YouTube Shorts*
