package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import com.example.sdk_qa.scenarios.video.VideoEpisodeApiScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeCustomScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests del flujo de navegación entre episodios.
 *
 * Verifica dos modos del SDK:
 *
 * MODO API — el SDK resuelve el siguiente episodio desde la plataforma.
 *   El cliente no interviene. El overlay aparece automáticamente.
 *
 * MODO CUSTOM — el cliente controla la lista de episodios.
 *   El SDK dispara [nextEpisodeIncoming] para que el cliente llame
 *   [updateNextEpisode(config)] con el siguiente.
 *
 * Callbacks verificados:
 *   - nextEpisodeIncoming — SDK avisa que el overlay está por aparecer
 *   - onNext             — el player avanzó al siguiente episodio
 *   - onNewSourceAdded   — el SDK cargó el nuevo contenido
 *
 * Los tests de seek son @LargeTest porque necesitan esperar que el contenido
 * cargue, buscar cerca del final, y esperar el callback de navegación.
 */
@RunWith(AndroidJUnit4::class)
class EpisodeNextTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT_READY = 20_000L
    private val TIMEOUT_NAV   = 30_000L // navegación requiere esperar el overlay + auto-advance

    // =========================================================================
    // MODO API
    // =========================================================================

    // -------------------------------------------------------------------------
    // [EPISODE-API-01] El episodio carga correctamente
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun episodeApi_onReady_fires() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-02] nextEpisodeIncoming dispara cuando se acerca el final
    //
    // El scenario tiene nextEpisodeTime = 15 — el overlay aparece 15s antes
    // del final. Hacemos seek a 20s antes del final para activarlo.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun episodeApi_seekNearEnd_nextEpisodeIncoming_fires() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { activity ->
                duration = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("El episodio debe tener duración conocida para este test")
                .that(duration).isGreaterThan(30_000L)

            // Seek 20s antes del final para activar el overlay (nextEpisodeTime = 15)
            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(duration - 20_000L)
            }

            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-03] onNext dispara después de nextEpisodeIncoming
    //
    // En modo API el SDK avanza automáticamente al siguiente episodio.
    // onNext confirma que el player ya está reproduciendo el siguiente.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun episodeApi_onNext_fires_after_nextEpisodeIncoming() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { activity ->
                duration = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("El episodio debe tener duración conocida para este test")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(duration - 20_000L)
            }

            // Esperar la secuencia completa: incoming → (overlay) → next
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)
            scenario.awaitCallback("onNext", TIMEOUT_NAV)

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-04] onNewSourceAdded confirma que el nuevo episodio cargó
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun episodeApi_onNewSourceAdded_fires_after_onNext() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { activity ->
                duration = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("El episodio debe tener duración conocida para este test")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(duration - 20_000L)
            }

            scenario.awaitCallback("onNext",         TIMEOUT_NAV)
            scenario.awaitCallback("onNewSourceAdded", TIMEOUT_NAV)

            // Verificar secuencia correcta: incoming → next → newSource
            val captor = scenario.getCallbackCaptor()
            assertWithMessage("nextEpisodeIncoming debe haber ocurrido")
                .that(captor.hasEvent("nextEpisodeIncoming")).isTrue()

            scenario.assertNoErrorFired()
        }
    }

    // =========================================================================
    // MODO CUSTOM
    // =========================================================================

    // -------------------------------------------------------------------------
    // [EPISODE-CUSTOM-01] El episodio en modo custom carga correctamente
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun episodeCustom_onReady_fires() {
        ActivityScenario.launch(VideoEpisodeCustomScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-CUSTOM-02] nextEpisodeIncoming dispara en modo custom
    //
    // En modo custom el SDK también dispara nextEpisodeIncoming — es la señal
    // para que el cliente llame updateNextEpisode(). La activity ya lo hace
    // en onNextEpisodeIncoming() mediante VideoEpisodeCustomScenarioActivity.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun episodeCustom_seekNearEnd_nextEpisodeIncoming_fires() {
        ActivityScenario.launch(VideoEpisodeCustomScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { activity ->
                duration = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("El episodio custom debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(duration - 20_000L)
            }

            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)
            scenario.assertNoErrorFired()
        }
    }
}
