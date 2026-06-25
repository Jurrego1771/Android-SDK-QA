# Reels (ReelsV2) — reglas y criterios

> **Feature:** `reels` · **id_prefix:** `REELS` · **deeplink:** `sdkqa://scenario/reels`
> **Alcance:** feed vertical de video corto a pantalla completa (estilo TikTok / Reels / Shorts).
> ViewPager2 vertical gestionado por `ReelsV2Handler` con un **pool de 6 ExoPlayers** reutilizables;
> el SDK fetchea dinámicamente los "related reels" del player configurado en plataforma.
> Versión del SDK bajo test: la fija `app/build.gradle.kts`.

> **Relación con Vertical (línea 11.0.0):** el modo **Vertical player** (`VideoTypes.VERTICAL` +
> `CustomPlaylistOrigin`) **reusa la infraestructura de Reels** (ViewPager2, pool de players,
> auto-advance). Ver feature `pip-v10-upcoming`→no; ver `merge_candidate` en INDEX (`reels ← vertical`).
> Los callbacks nuevos `onSwipeToItem`/`onEndReached`/`onEpisodeInfoClick` son de la familia
> **Vertical/Reels** → afectan a esta feature (ver [[risks]] REELS-RISK-005).

---

## 1. Qué es correcto (reglas de comportamiento)

- **Activación por plataforma, no por flag local.** El SDK entra en modo reels cuando la config de la
  plataforma trae `playerSkin == "reels"` o `type == "REELS"` (`MediastreamPlayer.shouldActivateReels`).
- **`playerId` obligatorio.** Sin él, el endpoint de related reels (`?player=<playerId>`) no pagina el
  feed (ver [[risks]] REELS-RISK-001).
- **Efectos al activar** (`ReelsV2Handler.activate`): fuerza orientación **PORTRAIT**, borra el
  `accessToken` del config (no exponerlo en las URLs de cada reel), monta el `ViewPager2` vertical.
- **Callbacks solo del reel ACTIVO.** Solo el reel en foco emite eventos; los otros 5 del pool no
  (ver [[risks]] REELS-RISK-003).
- **Loop, no auto-avance.** El clip actual loopea; llegar al final NO auto-avanza ni dispara `onNext`
  (en 10.0.x). El swipe es lo que cambia de reel.
- **Preferencias en memoria.** `isMuted`/`isMetadataVisible` no persisten entre sesiones (por diseño).

### Config mínima
```kotlin
MediastreamPlayerConfig().apply {
    id          = "MEDIA_ID"      // primer reel del feed
    playerId    = "PLAYER_ID"     // OBLIGATORIO — player de reels configurado en plataforma
    accountID   = "ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.VOD
    environment = MediastreamPlayerConfig.Environment.PRODUCTION
    autoplay    = true
}
```
> Fuente SDK: `reelsv2/{ReelsV2Handler,ReelsContentManager,PlayerPool}.kt`,
> `reelsv2/viewpager/{ViewPagerMediaAdapter,ViewPagerMediaHolder}.kt`.
> QA: `VideoReelsScenarioActivity` (full-screen, sin debug panel).

---

## 2. Criterios de aceptación

- **REELS-AC-001** — El **título** del reel proviene de la metadata del contenido (no del nombre de
  archivo) — ver [[defects]] REELS-DEF-001.
- **REELS-AC-002** — Modo reels se activa y el primer reel hace **autoplay** al entrar en foco.
- **REELS-AC-003** — **Swipe arriba** → reproduce el siguiente reel (en 10.0.x: `onPause → onNext →
  onPlay`). ⚠️ En 11.0.0 el callback de navegación puede ser `onSwipeToItem` (ver REELS-RISK-005).
- **REELS-AC-004** — **Swipe abajo** → reproduce el reel anterior.
- **REELS-AC-005** — Reel al **final** → loopea, NO auto-avanza, NO dispara `onNext`.
- **REELS-AC-006** — Callbacks NO se disparan para reels **fuera de foco** (solo el activo).
- **REELS-AC-007** — El feed **pagina**: tras varios swipes sigue cargando reels (related reels API).
- **REELS-AC-008** — Botón **mute** silencia/activa el audio globalmente y persiste al hacer swipe.
- **REELS-AC-009** — **Tap** en el video hace toggle play/pause del reel activo.
- **REELS-AC-010** — Botón **dismiss** dispara `onDismissButton` y cierra el feed.
- **REELS-AC-011** — Barra de progreso visible pero **NO seekeable**.
- **REELS-AC-012** — **Ads**: cada N reels aparece un intersticial y `onAdEvents` se dispara.
- **REELS-AC-013** — **Ads**: `ALL_ADS_COMPLETED` auto-avanza al siguiente reel.
- **REELS-AC-014** — `playerId` ausente → el feed no pagina (degradación/error observable).

## 3. Dependencias clave
- `androidx.viewpager2` — el feed vertical. `androidx.media3` — el pool de ExoPlayers.
- Endpoint de **related reels** de la plataforma (`?player=<playerId>`) — paginación del feed.

## 4. Comparación con el mercado (referencia)
- **= estándar** (TikTok/Reels/Shorts): swipe vertical, autoplay en foco, loop del actual, tap-pausa,
  audio con sonido por defecto, sin scrubbing, precarga adyacente, ads intersticiales cada N.
- **⚠ propio del SDK**: botón "ojo" (visibility) que oculta/muestra la metadata.
