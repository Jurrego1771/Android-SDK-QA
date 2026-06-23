package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.sdk_qa.annotation.MobileOnly
import com.example.sdk_qa.core.CallbackCaptor
import com.example.sdk_qa.scenarios.video.VideoReelsScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.uiDevice
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests del feed de Reels (ReelsV2) — ViewPager2 vertical con pool de ExoPlayers.
 *
 * Reels es [VideoReelsScenarioActivity] (FullScreenScenarioActivity), NO BaseScenarioActivity,
 * por eso no usa las extensiones tipadas a BaseScenarioActivity: lee el [CallbackCaptor] expuesto
 * vía [captor]. Observabilidad real verificada en device (callbacks en main thread, sin errores).
 *
 * Comportamiento de referencia: TikTok / Instagram Reels / YouTube Shorts.
 * Trazabilidad: cada test referencia su criterio de aceptación (docs/features/22-reels/user-stories.yaml).
 */
@RunWith(AndroidJUnit4::class)
class ReelsTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT_READY = 25_000L   // cold start de reels es lento (pool + preload)
    private val TIMEOUT_NAV   = 15_000L

    /** Lee el captor de la activity de reels (FullScreen no expone las extensiones de Base). */
    private fun ActivityScenario<VideoReelsScenarioActivity>.captor(): CallbackCaptor {
        var c: CallbackCaptor? = null
        onActivity { c = it.callbackCaptor }
        return c!!
    }

    /** Swipe vertical sobre el centro de la pantalla. up=true → siguiente reel; up=false → anterior. */
    private fun swipeVertical(up: Boolean) {
        val d = uiDevice()
        val x = d.displayWidth / 2
        val yFar = (d.displayHeight * 0.80).toInt()
        val yNear = (d.displayHeight * 0.20).toInt()
        if (up) d.swipe(x, yFar, x, yNear, 20)   // dedo sube → next
        else d.swipe(x, yNear, x, yFar, 20)      // dedo baja → previous
        d.waitForIdle()
    }

    // =========================================================================
    // [GAP-REELS-001 · REELS-AC-001] Activación + autoplay del primer reel
    // =========================================================================

    @Test
    @MediumTest
    fun reels_onReady_fires() {
        ActivityScenario.launch(VideoReelsScenarioActivity::class.java).use { scenario ->
            val received = scenario.captor().awaitEvent("onReady", TIMEOUT_READY)
            assertWithMessage("El primer reel debería disparar onReady\n" +
                "  recibidos: ${scenario.captor().allEvents()}")
                .that(received).isTrue()
        }
    }

    @Test
    @MediumTest
    fun reels_onPlay_fires_after_onReady() {
        ActivityScenario.launch(VideoReelsScenarioActivity::class.java).use { scenario ->
            assertWithMessage("onReady no llegó").that(
                scenario.captor().awaitEvent("onReady", TIMEOUT_READY)
            ).isTrue()
            assertWithMessage("autoplay: onPlay debería llegar tras onReady").that(
                scenario.captor().awaitEvent("onPlay", TIMEOUT_NAV)
            ).isTrue()
            val order = scenario.captor().eventOrderSnapshot()
            assertWithMessage("onReady debe preceder a onPlay — orden: $order")
                .that(order.indexOf("onReady")).isLessThan(order.indexOf("onPlay"))
        }
    }

    // =========================================================================
    // [REELS-AC-001/004] Los callbacks llegan en el main thread
    // =========================================================================

    @Test
    @MediumTest
    fun reels_lifecycleCallbacks_arriveOnMainThread() {
        ActivityScenario.launch(VideoReelsScenarioActivity::class.java).use { scenario ->
            scenario.captor().awaitEvent("onReady", TIMEOUT_READY)
            scenario.captor().awaitEvent("onPlay", TIMEOUT_NAV)
            val captor = scenario.captor()
            for (event in listOf("onReady", "onPlay")) {
                assertWithMessage("$event debería llegar en el main thread")
                    .that(captor.firedOnMainThread(event)).isTrue()
            }
        }
    }

    // =========================================================================
    // [GAP-REELS-001] El feed carga sin errores
    // =========================================================================

    @Test
    @MediumTest
    fun reels_load_doesNotFireError() {
        ActivityScenario.launch(VideoReelsScenarioActivity::class.java).use { scenario ->
            scenario.captor().awaitEvent("onReady", TIMEOUT_READY)
            val captor = scenario.captor()
            for (err in listOf("onError", "onEmbedErrors", "onPlaybackErrors")) {
                assertWithMessage("No debería dispararse '$err' al cargar el feed\n" +
                    "  recibidos: ${captor.allEvents()}")
                    .that(captor.hasEvent(err)).isFalse()
            }
        }
    }

    // =========================================================================
    // [GAP-REELS-003 · REELS-AC-002] Swipe arriba → onNext y reproduce el siguiente
    // =========================================================================

    @Test
    @MediumTest
    @MobileOnly // swipe táctil; reels es UX de móvil
    fun reels_swipeUp_fires_onNext() {
        ActivityScenario.launch(VideoReelsScenarioActivity::class.java).use { scenario ->
            assertWithMessage("primer reel no quedó listo").that(
                scenario.captor().awaitEvent("onReady", TIMEOUT_READY)
            ).isTrue()
            uiDevice().waitForIdle()

            swipeVertical(up = true)

            assertWithMessage("swipe arriba debería disparar onNext\n" +
                "  recibidos: ${scenario.captor().allEvents()}")
                .that(scenario.captor().awaitEvent("onNext", TIMEOUT_NAV)).isTrue()
        }
    }

    // =========================================================================
    // [GAP-REELS-004 · REELS-AC-003] Swipe abajo → onPrevious y reproduce el anterior
    // =========================================================================

    @Test
    @MediumTest
    @MobileOnly
    fun reels_swipeDown_afterNext_fires_onPrevious() {
        ActivityScenario.launch(VideoReelsScenarioActivity::class.java).use { scenario ->
            scenario.captor().awaitEvent("onReady", TIMEOUT_READY)
            uiDevice().waitForIdle()

            // Avanzar al reel 2 para luego poder retroceder.
            swipeVertical(up = true)
            assertWithMessage("no se avanzó al siguiente reel (onNext)")
                .that(scenario.captor().awaitEvent("onNext", TIMEOUT_NAV)).isTrue()

            // Retroceder al reel 1.
            swipeVertical(up = false)
            assertWithMessage("swipe abajo debería disparar onPrevious\n" +
                "  recibidos: ${scenario.captor().allEvents()}")
                .that(scenario.captor().awaitEvent("onPrevious", TIMEOUT_NAV)).isTrue()
        }
    }
}
