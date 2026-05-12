package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import androidx.media3.common.C
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import com.example.sdk_qa.scenarios.video.VideoSubtitleScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de subtítulos — config flags, selección de tracks y estabilidad.
 *
 * Estructura de tests:
 *   SUB-01..03 — Config-level: siempre corren, no dependen de contenido con subtítulos.
 *   SUB-04..07 — Track-level: usan assumeTrue y se omiten si el contenido
 *                no tiene tracks de texto configurados en la plataforma.
 *
 * Para activar SUB-04..07: actualizar TestContent.Video.VOD_WITH_SUBTITLES
 * con un ID de contenido que tenga subtítulos (WebVTT o ASS/SSA) en PRODUCTION.
 */
@RunWith(AndroidJUnit4::class)
class SubtitlesTest {

    @get:Rule val sdkRule = SdkTestRule()

    private val TIMEOUT = 20_000L

    // -------------------------------------------------------------------------
    // [SUB-01] Player carga correctamente con showSubtitles = ENABLE
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun subtitles_onReady_fires_withShowSubtitlesEnabled() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SUB-02] showSubtitles = DISABLE no crashea la reproducción
    //
    // Verifica que el flag DISABLE es procesado por el SDK sin lanzar excepción
    // ni dejar al player en estado de error.
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun subtitles_showDisable_doesNotCrash() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                val config = activity.buildConfig().apply {
                    showSubtitles = MediastreamPlayerConfig.FlagStatus.DISABLE
                }
                activity.player?.reloadPlayer(config)
            }
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SUB-03] Ciclo de flags ENABLE → DISABLE → NONE no deja estado inválido
    //
    // Simula un usuario que activa/desactiva subtítulos varias veces.
    // El player debe sobrevivir a los tres estados sin error.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun subtitles_flagCycle_enable_disable_none_doesNotCrash() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            listOf(
                MediastreamPlayerConfig.FlagStatus.ENABLE,
                MediastreamPlayerConfig.FlagStatus.DISABLE,
                MediastreamPlayerConfig.FlagStatus.NONE
            ).forEach { flag ->
                scenario.onActivity { activity ->
                    activity.callbackCaptor.reset()
                    activity.player?.reloadPlayer(activity.buildConfig().apply {
                        showSubtitles = flag
                    })
                }
                scenario.awaitCallback("onReady", TIMEOUT)
            }

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SUB-04] El contenido expone tracks de texto cuando tiene subtítulos
    //
    // Omitido automáticamente si TestContent.Video.VOD_WITH_SUBTITLES no tiene
    // subtítulos configurados en la plataforma.
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun subtitles_textTracksAvailable_whenContentHasSubtitles() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            var textTrackCount = 0
            scenario.onActivity { activity ->
                textTrackCount = activity.player?.msPlayer?.currentTracks?.groups
                    ?.count { it.type == C.TRACK_TYPE_TEXT } ?: 0
            }

            assumeTrue(
                "TestContent.Video.VOD_WITH_SUBTITLES no tiene tracks de texto — " +
                "actualizar con ID de contenido que tenga subtítulos configurados en PRODUCTION",
                textTrackCount > 0
            )
        }
    }

    // -------------------------------------------------------------------------
    // [SUB-05] Deshabilitar todos los tracks de texto (apagar subtítulos) no crashea
    //
    // Simula el flujo: usuario selecciona "Sin subtítulos" en el selector de tracks.
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun subtitles_disableAllTextTracks_doesNotCrash() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.let { exo ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            }

            Thread.sleep(1_000)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SUB-06] Seleccionar el primer track de texto disponible no crashea
    //
    // Omitido si no hay tracks de texto en el contenido actual.
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun subtitles_selectFirstTextTrack_doesNotCrash() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            var textTrackCount = 0
            scenario.onActivity { activity ->
                textTrackCount = activity.player?.msPlayer?.currentTracks?.groups
                    ?.count { it.type == C.TRACK_TYPE_TEXT } ?: 0
            }
            assumeTrue("No hay tracks de texto en este contenido", textTrackCount > 0)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.let { exo ->
                    val language = exo.currentTracks.groups
                        .firstOrNull { it.type == C.TRACK_TYPE_TEXT }
                        ?.getTrackFormat(0)?.language ?: ""
                    exo.trackSelectionParameters = exo.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(language)
                        .build()
                }
            }

            Thread.sleep(1_000)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SUB-07] Cambiar entre dos tracks de texto no crashea
    //
    // Omitido si hay menos de 2 tracks de texto (contenido con un solo idioma).
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun subtitles_switchBetweenTracks_doesNotCrash() {
        ActivityScenario.launch(VideoSubtitleScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            var textTrackCount = 0
            scenario.onActivity { activity ->
                textTrackCount = activity.player?.msPlayer?.currentTracks?.groups
                    ?.count { it.type == C.TRACK_TYPE_TEXT } ?: 0
            }
            assumeTrue("Se necesitan al menos 2 tracks de texto para este test", textTrackCount >= 2)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.let { exo ->
                    val textGroups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    exo.trackSelectionParameters = exo.trackSelectionParameters
                        .buildUpon()
                        .setPreferredTextLanguage(textGroups[0].getTrackFormat(0).language ?: "")
                        .build()
                }
            }

            Thread.sleep(500)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.let { exo ->
                    val textGroups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    exo.trackSelectionParameters = exo.trackSelectionParameters
                        .buildUpon()
                        .setPreferredTextLanguage(textGroups[1].getTrackFormat(0).language ?: "")
                        .build()
                }
            }

            Thread.sleep(500)
            scenario.assertNoErrorFired()
        }
    }
}
