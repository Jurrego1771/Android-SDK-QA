package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.core.PlaybackMetrics
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.awaitFirstFrame
import com.example.sdk_qa.utils.metricsSnapshot
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QoE gates — convierte las métricas de calidad de experiencia (Quality of Experience) que
 * miden las empresas referentes de streaming (Mux, Brightcove, THEOplayer) en aserciones de CI.
 *
 * Fuente: DirectHlsActivity — VOD corto de Mediastream por CDN, estable, apto para cada commit.
 *
 * IMPORTANTE — calibración de umbrales:
 *   Los TARGETS de industria (TTFF < 2s VOD, rebuffer ratio < 0.5%) son ideales de red/CDN
 *   en condiciones óptimas. Los GATES de aquí son techos GENEROSOS pensados para detectar
 *   REGRESIONES entre versiones del SDK sin volverse flaky por la varianza de dispositivos/red
 *   de CI. Cuando exista un baseline limpio medido en device físico, ajustar estos números
 *   hacia abajo. Cada gate documenta su target de industria y su techo de CI.
 *
 * Estas son pruebas "soak": dejan reproducir contenido real unos segundos para acumular
 * métricas significativas (rebuffer ratio sobre poco watch-time no es representativo).
 * Por eso son @LargeTest: `adb shell am instrument -e size large`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PlaybackQoeTest {

    private val READY_TIMEOUT = 15_000L

    // Ventana de soak: tiempo de reproducción real antes de evaluar métricas acumulativas.
    private val SOAK_MS = 20_000L

    // --- Gates de CI (techos generosos; ver nota de calibración arriba) ---
    private val TTFF_CEILING_MS = 5_000L      // target industria: < 2s VOD
    private val REBUFFER_RATIO_CEILING = 0.05 // target industria: < 0.005 (0.5%)

    // -------------------------------------------------------------------------
    // [QOE-01] TTFF (Time To First Frame) bajo el techo
    // GIVEN: VOD por CDN, autoplay
    // WHEN: el player arranca y renderiza el primer frame
    // THEN: TTFF (creación → primer frame) < TTFF_CEILING_MS
    // Assert reason: el primer frame es el momento percibido de "arranque" por el usuario;
    //   regresiones aquí degradan la métrica #1 de QoE de la industria.
    // -------------------------------------------------------------------------
    @Test
    fun ttff_isBelowCeiling() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", READY_TIMEOUT)

            val ttff = scenario.awaitFirstFrame(READY_TIMEOUT)
            assertWithMessage("El SDK nunca renderizó el primer frame (onRenderedFirstFrame)")
                .that(ttff).isAtLeast(0L)

            assertWithMessage(
                "TTFF=${ttff}ms supera el techo de CI ${TTFF_CEILING_MS}ms " +
                "(target industria < 2000ms VOD)"
            ).that(ttff).isLessThan(TTFF_CEILING_MS)
        }
    }

    // -------------------------------------------------------------------------
    // [QOE-02] Rebuffer ratio bajo el techo tras soak
    // GIVEN: VOD reproduciéndose ~20s
    // WHEN: se mide rebufferMs / (rebufferMs + watchMs)
    // THEN: ratio < REBUFFER_RATIO_CEILING
    // Assert reason: el rebuffering es la causa #1 de abandono de sesión en streaming;
    //   un ratio alto indica que el ABR o el buffering del SDK no sostienen la reproducción.
    // -------------------------------------------------------------------------
    @Test
    fun rebufferRatio_isBelowCeiling_afterSoak() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", READY_TIMEOUT)
            Thread.sleep(SOAK_MS)

            val m = scenario.metricsSnapshot()
            assertWithMessage(
                "Rebuffer ratio=${PlaybackMetrics.formatRatio(m.rebufferRatio)} " +
                "(${m.rebufferCount} stalls, ${m.rebufferMs}ms) supera el techo de CI " +
                "${PlaybackMetrics.formatRatio(REBUFFER_RATIO_CEILING)} (target industria < 0.5%)"
            ).that(m.rebufferRatio).isLessThan(REBUFFER_RATIO_CEILING)
        }
    }

    // -------------------------------------------------------------------------
    // [QOE-03] Sin errores de carga silenciosos durante reproducción normal
    // GIVEN: VOD estable por CDN
    // WHEN: reproduce ~20s
    // THEN: loadErrorCount == 0 (el SDK no reintentó segmentos/manifest en silencio)
    // Assert reason: el SDK silencia errores 404/de red que no llegan a onError (bug conocido
    //   sdk_known_bugs); onLoadError los expone. En contenido estable deben ser 0.
    // -------------------------------------------------------------------------
    @Test
    fun noSilentLoadErrors_duringNormalPlayback() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", READY_TIMEOUT)
            Thread.sleep(SOAK_MS)

            val m = scenario.metricsSnapshot()
            scenario.assertNoErrorFired() // ningún error vía callbacks del SDK
            assertWithMessage(
                "El SDK registró ${m.loadErrorCount} errores de carga silenciosos " +
                "(no expuestos por onError). Último: ${m.lastLoadError}"
            ).that(m.loadErrorCount).isEqualTo(0)
        }
    }

    // -------------------------------------------------------------------------
    // [QOE-04] El pipeline ABR reporta bitrate y resolución
    // GIVEN: VOD reproduciéndose
    // WHEN: se inspecciona el formato de video activo
    // THEN: currentBitrate > 0 y resolution detectada
    // Assert reason: si el SDK no reporta formato, el ABR no está operando o el track de video
    //   no se seleccionó — sanity check del pipeline de decodificación.
    // -------------------------------------------------------------------------
    @Test
    fun abrPipeline_reportsBitrateAndResolution() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", READY_TIMEOUT)
            scenario.awaitFirstFrame(READY_TIMEOUT)
            Thread.sleep(3_000) // dar tiempo a onVideoInputFormatChanged

            val m = scenario.metricsSnapshot()
            assertWithMessage("El SDK no reportó bitrate de video (ABR no operando?)")
                .that(m.currentBitrateBps).isGreaterThan(0)
            assertWithMessage("El SDK no reportó resolución de video")
                .that(m.resolution).isNotEqualTo("—")
        }
    }
}
