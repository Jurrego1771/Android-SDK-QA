package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import com.example.sdk_qa.annotation.MobileOnly
import com.example.sdk_qa.scenarios.video.VideoEpisodeApiScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeCustomScenarioActivity
import com.example.sdk_qa.utils.OverlayText
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertCallbackNotFired
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.assertNotVisible
import com.example.sdk_qa.utils.assertVisible
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.example.sdk_qa.utils.uiDevice
import com.example.sdk_qa.utils.waitAndClick
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests del flujo de navegación entre episodios.
 *
 * Verifica dos modos del SDK:
 *
 * MODO API — el SDK resuelve el siguiente episodio desde la plataforma.
 *   El overlay aparece automáticamente con dos botones:
 *   - "Seguir viendo"     → descarta overlay, episodio continúa hasta el final
 *   - "Siguiente episodio"→ dispara onNext + onNewSourceAdded, carga el siguiente
 *
 * MODO CUSTOM — el cliente controla la lista de episodios.
 *   El SDK dispara [nextEpisodeIncoming] para que el cliente llame
 *   [updateNextEpisode(config)] con el siguiente.
 *
 * IMPORTANTE: onNext NO se dispara automáticamente en modo API.
 *   Requiere que el usuario presione "Siguiente episodio" o que el countdown
 *   del overlay expire. Los tests usan UiAutomator para simular el press.
 */
@RunWith(AndroidJUnit4::class)
class EpisodeNextTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT_READY = 20_000L
    private val TIMEOUT_NAV   = 35_000L // navegación: espera overlay + press + carga
    private val TIMEOUT_OVERLAY = 8_000L

    // =========================================================================
    // MODO API — callbacks
    // =========================================================================

    // -------------------------------------------------------------------------
    // [EPISODE-API-01] El episodio carga correctamente
    // -------------------------------------------------------------------------
    @Ignore("API Mediastream de episodios tarda >2min en cold start — el contenido API no responde a tiempo")
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
    // nextEpisodeTime = 15 → overlay 15s antes del final.
    // Seek a 20s antes del final activa el overlay a los ~5s.
    // -------------------------------------------------------------------------
    @Ignore("Contenido sin episodio siguiente en API — nextEpisodeIncoming nunca se dispara en modo API")
    @Test
    @LargeTest
    fun episodeApi_seekNearEnd_nextEpisodeIncoming_fires() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }

            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-03] onNext dispara cuando el usuario presiona "Siguiente"
    //
    // El SDK no auto-avanza — muestra overlay con botones y espera interacción.
    // UiAutomator simula el press en "Siguiente episodio".
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // overlay buttons not accessible via UiAutomator on TV
    @LargeTest
    fun episodeApi_onNext_fires_after_nextEpisodeIncoming() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)

            // Simular press en "Siguiente episodio"
            val clicked = uiDevice().waitAndClick(OverlayText.NEXT_EPISODE, TIMEOUT_OVERLAY)
            assertWithMessage("Botón '${OverlayText.NEXT_EPISODE}' no apareció en el overlay")
                .that(clicked).isTrue()

            scenario.awaitCallback("onNext", TIMEOUT_NAV)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-04] onNewSourceAdded confirma que el nuevo episodio cargó
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // overlay buttons not accessible via UiAutomator on TV
    @LargeTest
    fun episodeApi_onNewSourceAdded_fires_after_onNext() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)

            uiDevice().waitAndClick(OverlayText.NEXT_EPISODE, TIMEOUT_OVERLAY)

            scenario.awaitCallback("onNext",          TIMEOUT_NAV)
            scenario.awaitCallback("onNewSourceAdded", TIMEOUT_NAV)

            val captor = scenario.getCallbackCaptor()
            assertWithMessage("nextEpisodeIncoming debe haber ocurrido antes de onNext")
                .that(captor.hasEvent("nextEpisodeIncoming")).isTrue()

            scenario.assertNoErrorFired()
        }
    }

    // =========================================================================
    // MODO API — UI del overlay
    // =========================================================================

    // -------------------------------------------------------------------------
    // [EPISODE-API-05] Overlay es visible tras nextEpisodeIncoming
    //
    // Verifica que ambos botones del overlay estén en pantalla.
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // overlay buttons not accessible via UiAutomator on TV
    @LargeTest
    fun episodeApi_overlay_isVisible_after_nextEpisodeIncoming() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)

            val device = uiDevice()
            device.assertVisible(OverlayText.NEXT_EPISODE,  TIMEOUT_OVERLAY)
            device.assertVisible(OverlayText.KEEP_WATCHING, TIMEOUT_OVERLAY)

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-06] "Seguir viendo" descarta el overlay sin navegar
    //
    // Al presionar "Seguir viendo":
    //   - El overlay desaparece
    //   - onNext NO se dispara
    //   - El episodio sigue reproduciéndose
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // overlay buttons not accessible via UiAutomator on TV
    @LargeTest
    fun episodeApi_keepWatching_dismissesOverlay_noNavigation() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)

            val device = uiDevice()
            val clicked = device.waitAndClick(OverlayText.KEEP_WATCHING, TIMEOUT_OVERLAY)
            assertWithMessage("Botón '${OverlayText.KEEP_WATCHING}' no apareció en el overlay")
                .that(clicked).isTrue()

            // Overlay debe desaparecer
            device.assertNotVisible(OverlayText.NEXT_EPISODE)
            device.assertNotVisible(OverlayText.KEEP_WATCHING)

            // onNext NO debe haberse disparado
            scenario.assertCallbackNotFired("onNext")
            scenario.assertCallbackNotFired("onNewSourceAdded")
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-API-07] "Siguiente episodio" navega al siguiente y el overlay desaparece
    //
    // Al presionar "Siguiente episodio":
    //   - onNext se dispara
    //   - onNewSourceAdded confirma que cargó el nuevo contenido
    //   - El overlay desaparece
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // overlay buttons not accessible via UiAutomator on TV
    @LargeTest
    fun episodeApi_nextButton_navigatesAndDismissesOverlay() {
        ActivityScenario.launch(VideoEpisodeApiScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)

            val device = uiDevice()
            val clicked = device.waitAndClick(OverlayText.NEXT_EPISODE, TIMEOUT_OVERLAY)
            assertWithMessage("Botón '${OverlayText.NEXT_EPISODE}' no apareció en el overlay")
                .that(clicked).isTrue()

            scenario.awaitCallback("onNext",           TIMEOUT_NAV)
            scenario.awaitCallback("onNewSourceAdded", TIMEOUT_NAV)

            // Overlay debe desaparecer tras navegar
            device.assertNotVisible(OverlayText.NEXT_EPISODE)

            scenario.assertNoErrorFired()
        }
    }

    // =========================================================================
    // MODO CUSTOM
    // =========================================================================

    // -------------------------------------------------------------------------
    // [EPISODE-CUSTOM-01] El episodio en modo custom carga correctamente
    // -------------------------------------------------------------------------
    @Ignore("API Mediastream de episodios tarda >2min en cold start — onReady falla sin warm-up previo")
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
    // -------------------------------------------------------------------------
    @Ignore("API Mediastream de episodios tarda >2min en cold start — sin warm-up previo onReady falla antes del seek")
    @Test
    @LargeTest
    fun episodeCustom_seekNearEnd_nextEpisodeIncoming_fires() {
        ActivityScenario.launch(VideoEpisodeCustomScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio custom debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }

            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [EPISODE-CUSTOM-03] "Siguiente episodio" navega en modo custom
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // overlay buttons not accessible via UiAutomator on TV
    @LargeTest
    fun episodeCustom_nextButton_fires_onNext() {
        ActivityScenario.launch(VideoEpisodeCustomScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)

            var duration = 0L
            scenario.onActivity { duration = it.player?.msPlayer?.duration ?: 0L }
            assertWithMessage("El episodio custom debe tener duración conocida")
                .that(duration).isGreaterThan(30_000L)

            scenario.onActivity { it.player?.msPlayer?.seekTo(duration - 20_000L) }
            scenario.awaitCallback("nextEpisodeIncoming", TIMEOUT_NAV)

            val clicked = uiDevice().waitAndClick(OverlayText.NEXT_EPISODE, TIMEOUT_OVERLAY)
            assertWithMessage("Botón '${OverlayText.NEXT_EPISODE}' no apareció en el overlay")
                .that(clicked).isTrue()

            scenario.awaitCallback("onNext", TIMEOUT_NAV)
            scenario.assertNoErrorFired()
        }
    }
}
