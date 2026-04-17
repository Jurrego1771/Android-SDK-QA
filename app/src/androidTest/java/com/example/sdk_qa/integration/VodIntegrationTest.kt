package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
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
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests — fuente directa HLS (Mux test stream, sin API de Mediastream).
 *
 * Objetivo: verificar el comportamiento del SDK con una fuente de red estable y pública,
 * sin depender del backend de Mediastream. Aptos para CI en cada commit.
 *
 * Stream: https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
 * Es el stream de referencia de la industria — usado por ExoPlayer, Media3, Mux y otros.
 * Timeout: 15s (stream sin DRM, sin ads, debería resolver rápido).
 *
 * Anotados con @MediumTest: `adb shell am instrument -e size medium`
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class VodIntegrationTest {

    private val TIMEOUT = 15_000L

    // -------------------------------------------------------------------------
    // [INT-VOD-01] El container del player está visible al arrancar
    // -------------------------------------------------------------------------
    @Test
    fun playerContainer_isVisible_onStart() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            onView(withId(R.id.player_container)).check(matches(isDisplayed()))
        }
    }

    // -------------------------------------------------------------------------
    // [INT-VOD-02] src directo (sin accountID) dispara onReady
    // -------------------------------------------------------------------------
    @Test
    fun srcDirect_onReady_fires() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-VOD-03] onPlay dispara después de onReady (secuencia de callbacks)
    // -------------------------------------------------------------------------
    @Test
    fun onPlay_fires_after_onReady() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-VOD-04] seekTo mueve la posición del player
    //
    // Usa la duración real del contenido para calcular el target de seek,
    // ya que distintos IDs de prueba pueden tener duraciones distintas.
    // -------------------------------------------------------------------------
    @Test
    fun seekTo_changesPosition() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Obtener duración real del contenido
            var durationMs = 0L
            scenario.onActivity { activity ->
                durationMs = activity.player?.msPlayer?.duration ?: 0L
            }

            // Necesitamos al menos 2s para que el seek sea significativo
            if (durationMs < 2_000L) {
                // Duración desconocida o video demasiado corto — omitir seek
                return
            }

            // Seek al 75% de la duración real
            val targetMs = (durationMs * 3L) / 4L
            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(targetMs)
            }

            // Dar tiempo al seek para que aplique
            Thread.sleep(1_000)

            var position = 0L
            scenario.onActivity { activity ->
                position = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            assertWithMessage(
                "La posición debería estar cerca de ${targetMs}ms (duración real: ${durationMs}ms)"
            )
                .that(position)
                .isGreaterThan(targetMs - 5_000L)
        }
    }

    // -------------------------------------------------------------------------
    // [INT-VOD-05] reloadPlayer vuelve a disparar onReady
    // -------------------------------------------------------------------------
    @Test
    fun reloadPlayer_fires_onReady_again() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            // Primera carga
            scenario.awaitCallback("onReady", TIMEOUT)

            // Reset y reload
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(DirectHlsActivity.buildDirectConfig())
            }

            // Segunda carga debe volver a disparar onReady
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }
}

