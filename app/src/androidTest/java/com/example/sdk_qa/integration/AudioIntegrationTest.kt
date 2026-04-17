package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import com.example.sdk_qa.scenarios.audio.AudioLiveScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioVodScenarioActivity
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de integración para escenarios de audio (VOD y Live).
 *
 * Verifica que el SDK maneja correctamente el tipo AUDIO:
 *   - No requiere superficie de video para reproducir
 *   - Callbacks de lifecycle iguales a video (onReady, onPlay, onEnd)
 *   - Seek funciona en audio VOD
 *   - Live audio conecta sin errors
 *
 * Usa CDN real de Mediastream — requiere red activa.
 *
 * Nota: SdkTestRule (LeakCanary) no se usa aquí porque la biblioteca IMA de Google
 * (Google Interactive Media Ads) tiene leaks conocidos en sus clases internas que
 * son falsos positivos — no son leaks del Mediastream SDK. La detección de leaks
 * del SDK se cubre en PlayerLifecycleTest y LifecycleTest.
 */
@RunWith(AndroidJUnit4::class)
class AudioIntegrationTest {

    private val TIMEOUT_MEDIUM = 15_000L
    private val TIMEOUT_LARGE  = 25_000L

    // -------------------------------------------------------------------------
    // [AUDIO-01] Audio VOD carga y dispara onReady
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun audioVod_onReady_fires() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIO-02] Audio VOD dispara onPlay después de onReady
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun audioVod_onPlay_fires_after_onReady() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)
            scenario.awaitCallback("onPlay",  TIMEOUT_MEDIUM)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIO-03] Los callbacks de audio llegan en el main thread
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun audioVod_callbacks_arriveOnMainThread() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)
            scenario.awaitCallback("onPlay",  TIMEOUT_MEDIUM)

            val captor = scenario.getCallbackCaptor()
            assertWithMessage("onReady de audio debe llegar en main thread")
                .that(captor.firedOnMainThread("onReady")).isTrue()
            assertWithMessage("onPlay de audio debe llegar en main thread")
                .that(captor.firedOnMainThread("onPlay")).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIO-04] Seek en audio VOD cambia la posición
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun audioVod_seekTo_changesPosition() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)

            val targetMs = 60_000L // 1 minuto
            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(targetMs)
            }

            Thread.sleep(1_000)

            var position = 0L
            scenario.onActivity { activity ->
                position = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            assertWithMessage("La posición de audio debería estar cerca de ${targetMs / 1000}s")
                .that(position)
                .isGreaterThan(targetMs - 5_000L)
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIO-05] Audio VOD dispara onEnd al llegar al final
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun audioVod_seekToEnd_onEnd_fires() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)

            scenario.onActivity { activity ->
                val duration = activity.player?.msPlayer?.duration ?: 0L
                if (duration > 10_000L) {
                    activity.player?.msPlayer?.seekTo(duration - 3_000L)
                }
            }

            scenario.awaitCallback("onEnd", TIMEOUT_LARGE)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIO-06] Audio Live conecta y dispara onReady
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun audioLive_onReady_fires() {
        ActivityScenario.launch(AudioLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIO-07] Audio Live — callbacks en main thread
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun audioLive_callbacks_arriveOnMainThread() {
        ActivityScenario.launch(AudioLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_MEDIUM)

            val captor = scenario.getCallbackCaptor()
            assertWithMessage("onReady de live audio debe llegar en main thread")
                .that(captor.firedOnMainThread("onReady")).isTrue()
        }
    }
}
