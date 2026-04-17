# Test Patterns — Cómo Escribir Tests en este Repo

> Referencia para generación de tests con IA. Estos son los patrones REALES del repo.
> NO inventar patrones — usar exactamente estos.

---

## Stack de Testing

| Herramienta | Uso |
|-------------|-----|
| `AndroidJUnit4` | Runner para todos los tests instrumentados |
| `ActivityScenario<T>` | Ciclo de vida de Activities en tests |
| `Espresso` | Assertions de UI (visibilidad, clicks) |
| `UI Automator` | Interacciones de sistema (notificaciones, PiP, home) |
| `Truth` | Assertions (`assertWithMessage(...).that(x).is...`) |
| `CountDownLatch` | Sincronización async (callback captor usa esto internamente) |
| `LeakCanary instrumentation` | Detección de memory leaks en tests (`SdkTestRule`) |
| `StrictMode` | Detección de I/O en main thread (`SdkTestRule`) |

---

## Infraestructura de Tests — Clases Clave

### BaseScenarioActivity
```kotlin
// Clase base abstracta para todos los escenarios QA
// Crear una Activity auxiliar que la extienda para cada test:
class MyTestActivity : BaseScenarioActivity() {
    override fun getScenarioTitle() = "My Test"
    override fun buildConfig() = MediastreamPlayerConfig().apply {
        src = TestContent.Video.SRC_DIRECT_HLS
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
    }
}

// Acceso desde tests:
// activity.player          → instancia de MediastreamPlayer
// activity.callbackCaptor  → CallbackCaptor para verificar callbacks
```

### CallbackCaptor
```kotlin
// Disponible en activity.callbackCaptor
captor.awaitEvent("onReady", 15_000)         // Espera evento, retorna Boolean
captor.hasEvent("onError")                   // Verifica sin bloquear
captor.firedOnMainThread("onPlay")           // Verifica el thread
captor.allEvents()                           // Todos los eventos registrados
captor.reset()                               // Limpia para el próximo test
```

### SdkTestExtensions (extensiones de ActivityScenario)
```kotlin
scenario.awaitCallback("onReady", TIMEOUT)   // Espera + assert automático
scenario.assertCallbackNotFired("onError")   // Verifica que NO ocurrió
scenario.assertNoErrorFired()                // Shortcut: assertCallbackNotFired("onError")
scenario.getCallbackCaptor()                 // Accede al captor
```

### SdkTestRule
```kotlin
// Combina LeakCanary + StrictMode en una sola regla
@get:Rule val sdkRule = SdkTestRule()

// Para encadenar con ActivityScenarioRule:
@get:Rule val rules = RuleChain
    .outerRule(SdkTestRule())
    .around(ActivityScenarioRule(MyActivity::class.java))
```

---

## Estructura de un Test — Plantilla Obligatoria

```kotlin
@RunWith(AndroidJUnit4::class)
@MediumTest  // o @SmallTest / @LargeTest según duración
class FeatureNameTest {

    private val TIMEOUT = 15_000L  // ajustar según el tipo de contenido

    // -------------------------------------------------------------------------
    // [TAG-FEATURE-01] Descripción corta del test
    // -------------------------------------------------------------------------
    @Test
    fun `given X when Y then Z`() {
        // GIVEN: setup del escenario
        ActivityScenario.launch(MyTestActivity::class.java).use { scenario ->

            // WHEN: acción principal (puede ser solo esperar un callback)
            scenario.awaitCallback("onReady", TIMEOUT)

            // THEN: assertions con razón técnica
            // Assert reason: SDK contract establece que onPlay sigue a onReady
            // cuando autoplay=true (ver sdk-api-contract.md §7)
            scenario.awaitCallback("onPlay", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }
}
```

### Tags de test recomendados:
```
[SMOKE-01]     Smoke test básico
[INT-VOD-01]   Integration: VOD
[INT-LIVE-01]  Integration: Live
[INT-EP-01]    Integration: Episodes
[INT-AUDIO-01] Integration: Audio
[INT-ADS-01]   Integration: Ads
[INT-DRM-01]   Integration: DRM
[INT-PIP-01]   Integration: PiP
[INT-CAST-01]  Integration: Chromecast
[INT-DVR-01]   Integration: DVR
[INT-CB-01]    Integration: Callbacks
[INT-LC-01]    Integration: Lifecycle
```

---

## Patrones por Tipo de Test

### Patrón 1: Verificar que un callback dispara (más común)
```kotlin
@Test
fun `src_direct_fires_onReady_within_timeout`() {
    ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
        // Assert reason: SDK contract garantiza onReady para src HLS válido en <15s
        scenario.awaitCallback("onReady", 15_000L)
        scenario.assertNoErrorFired()
    }
}
```

### Patrón 2: Verificar orden de callbacks
```kotlin
@Test
fun `onPlay_fires_after_onReady_with_autoplay`() {
    val eventOrder = mutableListOf<String>()
    var scenario: ActivityScenario<MyActivity>? = null

    scenario = ActivityScenario.launch(MyActivity::class.java)
    scenario.use { sc ->
        // Registrar un callback adicional para capturar orden
        sc.onActivity { activity ->
            activity.player?.addPlayerCallback(object : MediastreamPlayerCallback {
                // Implementar todos los métodos (no hay defaults en v11)
                override fun onReady() { eventOrder.add("onReady") }
                override fun onPlay() { eventOrder.add("onPlay") }
                override fun playerViewReady(v: PlayerView?) {}
                override fun onPause() {}
                override fun onEnd() {}
                override fun onPlayerClosed() {}
                override fun onBuffering() {}
                override fun onError(e: String?) {}
                override fun onNext() {}
                override fun onPrevious() {}
                override fun onFullscreen(enteredForPip: Boolean) {}
                override fun offFullscreen() {}
                override fun onNewSourceAdded(c: MediastreamPlayerConfig) {}
                override fun onLocalSourceAdded() {}
                override fun onAdEvents(t: AdEvent.AdEventType) {}
                override fun onAdErrorEvent(e: AdError) {}
                override fun onConfigChange(c: MediastreamMiniPlayerConfig?) {}
                override fun onCastAvailable(s: Boolean?) {}
                override fun onCastSessionStarting() {}
                override fun onCastSessionStarted() {}
                override fun onCastSessionStartFailed() {}
                override fun onCastSessionEnding() {}
                override fun onCastSessionEnded() {}
                override fun onCastSessionResuming() {}
                override fun onCastSessionResumed() {}
                override fun onCastSessionResumeFailed() {}
                override fun onCastSessionSuspended() {}
                override fun onPlaybackErrors(e: JSONObject?) {}
                override fun onEmbedErrors(e: JSONObject?) {}
                override fun onLiveAudioCurrentSongChanged(d: JSONObject?) {}
                override fun onDismissButton() {}
                override fun onPlayerReload() {}
            })
        }

        sc.awaitCallback("onReady", 15_000L)
        sc.awaitCallback("onPlay", 15_000L)

        // Assert reason: SDK contract §7 garantiza onReady ANTES de onPlay
        assertWithMessage("onReady debe disparar antes de onPlay")
            .that(eventOrder)
            .containsExactly("onReady", "onPlay").inOrder()
    }
}
```

### Patrón 3: Verificar que un callback NO dispara
```kotlin
@Test
fun `valid_config_does_not_fire_onError`() {
    ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
        scenario.awaitCallback("onReady", 15_000L)

        // Assert reason: src HLS público válido no debe producir error crítico
        scenario.assertCallbackNotFired("onError")
        scenario.assertCallbackNotFired("onEmbedErrors")
    }
}
```

### Patrón 4: Acción en el player + verificar resultado
```kotlin
@Test
fun `seekTo_moves_position_within_tolerance`() {
    ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
        scenario.awaitCallback("onReady", 15_000L)

        val targetMs = 30_000L

        // WHEN: seek a posición
        scenario.onActivity { activity ->
            activity.player?.msPlayer?.seekTo(targetMs)
        }

        Thread.sleep(1_000) // esperar a que el seek aplique

        var position = 0L
        scenario.onActivity { activity ->
            position = activity.player?.msPlayer?.currentPosition ?: 0L
        }

        // Assert reason: business-rules.md §VOD establece tolerancia de ±5000ms para seek
        assertWithMessage("Posición después de seek debe estar cerca de $targetMs ms")
            .that(position)
            .isGreaterThan(targetMs - 5_000L)
    }
}
```

### Patrón 5: Reload del player
```kotlin
@Test
fun `reloadPlayer_fires_onReady_again`() {
    ActivityScenario.launch(MyActivity::class.java).use { scenario ->
        // Primera carga
        scenario.awaitCallback("onReady", 15_000L)

        // Reset y reload
        scenario.onActivity { activity ->
            activity.callbackCaptor.reset()
            activity.player?.reloadPlayer(MyActivity.buildConfig())
        }

        // Assert reason: reloadPlayer() reinicia el ciclo de vida del player
        // y debe disparar onReady nuevamente según SDK contract §7
        scenario.awaitCallback("onReady", 15_000L)
        scenario.assertNoErrorFired()
    }
}
```

### Patrón 6: Verificar thread de callback
```kotlin
@Test
fun `onReady_fires_on_main_thread`() {
    ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
        scenario.awaitCallback("onReady", 15_000L)

        var firedOnMain = false
        scenario.onActivity { activity ->
            // Assert reason: SDK contract §7 — todos los callbacks en Main Thread
            firedOnMain = activity.callbackCaptor.firedOnMainThread("onReady")
        }

        assertWithMessage("onReady debe dispararse en el Main Thread")
            .that(firedOnMain)
            .isTrue()
    }
}
```

### Patrón 7: Test con múltiples callbacks en secuencia
```kotlin
@Test
fun `episode_nextEpisodeIncoming_fires_before_overlay`() {
    ActivityScenario.launch(EpisodeTestActivity::class.java).use { scenario ->
        scenario.awaitCallback("onReady", 20_000L)
        scenario.awaitCallback("onPlay", 20_000L)

        // Assert reason: nextEpisodeIncoming debe disparar ANTES del overlay (mode manual)
        // SDK contract §5 callbacks — se dispara con nextEpisodeTime segundos de anticipación
        scenario.awaitCallback("nextEpisodeIncoming", 60_000L) // timeout mayor para esperar el timing

        var overlayVisible = false
        scenario.onActivity { activity ->
            overlayVisible = activity.player?.isNextOverlayVisible() ?: false
        }

        // El overlay no debe aparecer inmediatamente — el integrador necesita llamar updateNextEpisode()
        assertWithMessage("El overlay no debe aparecer antes de updateNextEpisode()")
            .that(overlayVisible)
            .isFalse()

        scenario.assertNoErrorFired()
    }
}
```

---

## Reglas Anti-False-Positive

| Anti-patrón | Por qué evitar | Alternativa correcta |
|-------------|---------------|---------------------|
| `Thread.sleep()` para esperar callbacks | Race condition, flaky test | `scenario.awaitCallback()` con timeout |
| Assert solo `isNotNull()` | No verifica comportamiento | Assert el valor específico o estado |
| No verificar que test FALLA sin el SDK | False positive garantizado | Comentar la config y verificar que falla |
| Usar `TODO_` IDs de TestContent | Contenido no existe | Usar IDs ya configurados |
| Registrar callback parcial sin todos los métodos | No compila en v11 | Implementar TODOS los métodos |
| `Thread.sleep()` > 500ms en vez de latch | Flaky | Usar CountDownLatch o awaitCallback |
| Assert en el order de eventos con lista mutable | Race condition | Usar CountDownLatch + lista sincronizada |

---

## Cómo Implementar MediastreamPlayerCallback en Tests

En v11, `MediastreamPlayerCallback` es completamente abstracta. Para tests que necesitan su propio callback:

**Opción A (recomendada para la mayoría de tests):** Usar el `callbackCaptor` de `BaseScenarioActivity` directamente — ya está registrado.

**Opción B:** Para verificar orden o datos específicos, crear una clase anónima implementando TODOS los métodos:

```kotlin
// Template de callback completo para tests (copiar y pegar):
object : MediastreamPlayerCallback {
    override fun playerViewReady(msplayerView: PlayerView?) {}
    override fun onPlay() {}
    override fun onPause() {}
    override fun onReady() {}
    override fun onEnd() {}
    override fun onPlayerClosed() {}
    override fun onBuffering() {}
    override fun onError(error: String?) {}
    override fun onNext() {}
    override fun onPrevious() {}
    override fun onFullscreen(enteredForPip: Boolean) {}  // v11: tiene parámetro
    override fun offFullscreen() {}
    override fun onNewSourceAdded(config: MediastreamPlayerConfig) {}
    override fun onLocalSourceAdded() {}
    override fun onAdEvents(type: AdEvent.AdEventType) {}
    override fun onAdErrorEvent(error: AdError) {}
    override fun onConfigChange(config: MediastreamMiniPlayerConfig?) {}
    override fun onCastAvailable(state: Boolean?) {}
    override fun onCastSessionStarting() {}
    override fun onCastSessionStarted() {}
    override fun onCastSessionStartFailed() {}
    override fun onCastSessionEnding() {}
    override fun onCastSessionEnded() {}
    override fun onCastSessionResuming() {}
    override fun onCastSessionResumed() {}
    override fun onCastSessionResumeFailed() {}
    override fun onCastSessionSuspended() {}
    override fun onPlaybackErrors(error: JSONObject?) {}
    override fun onEmbedErrors(error: JSONObject?) {}
    override fun onLiveAudioCurrentSongChanged(data: JSONObject?) {}
    override fun onDismissButton() {}
    override fun onPlayerReload() {}
}
```

---

## Tamaños de Test (@SmallTest / @MediumTest / @LargeTest)

| Tag | Duración esperada | Cuándo usar |
|-----|------------------|-------------|
| `@SmallTest` | < 1s | Unit tests, config tests sin red |
| `@MediumTest` | 1-10s | Integration tests con src directo (HLS público) |
| `@LargeTest` | > 10s | Tests con API de Mediastream, Live, DVR, Ads |

---

## Imports Necesarios

```kotlin
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayer
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerCallback
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import am.mediastre.mediastreamplatformsdkandroid.MediastreamMiniPlayerConfig
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.sdk_qa.R
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.ads.interactivemedia.v3.api.AdError
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.common.truth.Truth.assertWithMessage
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
```
