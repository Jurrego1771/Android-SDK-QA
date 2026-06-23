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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QoE gates — convierte las métricas de calidad de experiencia (Quality of Experience) que
 * miden las empresas referentes de streaming (Mux, Brightcove, THEOplayer) en aserciones de CI.
 *
 * Fuente: DirectHlsActivity — VOD corto de Mediastream por CDN, estable, apto para cada commit.
 *
 * MIDE STEADY-STATE, NO COLD-START:
 *   El baseline limpio (QoeBaselineTest, A53/SDK 10.0.7, 2026-06-23) mostró dos regímenes muy
 *   distintos. Arranque FRÍO (red sin calentar: DNS/TCP/TLS/CDN cache miss): TTFF ~8s, rebuffer
 *   ratio ~52%, 480p. Arranque CALIENTE (steady-state): TTFF 1.5–2.6s, rebuffer 0%, 720p.
 *   Bajo el Orchestrator de CI cada test corre en proceso nuevo = frío, así que sin warm-up
 *   estos gates serían flaky. Por eso [warmUp] reproduce una sesión descartada antes de cada
 *   test: calienta la red a nivel de proceso (DNS/CDN/conexiones), dejando la medición en
 *   régimen caliente — que además es el más sensible para detectar regresiones (0% → cualquier
 *   stall se nota). El cold-start se observa por separado en QoeBaselineTest, no se gatea aquí
 *   (depende demasiado de la red de CI).
 *
 *   Umbrales calibrados al baseline caliente (med 2.6s / 0%) + margen anti-flaky. Targets de
 *   industria documentados en cada gate.
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

    // Warm-up: reproducción descartada que calienta la red del proceso antes de medir.
    private val WARMUP_MS = 6_000L

    // --- Gates de CI (calibrados al baseline CALIENTE; ver nota de régimen arriba) ---
    private val TTFF_CEILING_MS = 4_000L      // baseline caliente med 2.6s · target industria < 2s VOD
    private val REBUFFER_RATIO_CEILING = 0.02 // baseline caliente 0% · target industria < 0.005 (0.5%)

    /**
     * Calienta la red a nivel de proceso con una reproducción descartada, para que la medición
     * caiga en régimen caliente (steady-state) y no en el arranque frío de red. Crítico bajo el
     * Orchestrator, donde cada test arranca en proceso nuevo.
     */
    @Before
    fun warmUp() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", READY_TIMEOUT)
            Thread.sleep(WARMUP_MS)
        }
    }

    // -------------------------------------------------------------------------
    // [QOE-01] TTFF (Time To First Frame) — mediana bajo el techo
    // GIVEN: VOD por CDN, autoplay, red caliente (warm-up previo)
    // WHEN: se miden 3 arranques y se toma la MEDIANA del TTFF (creación → primer frame)
    // THEN: mediana < TTFF_CEILING_MS
    // Assert reason: el primer frame es el "arranque" percibido por el usuario, métrica #1 de
    //   QoE. El TTFF de muestra única es ruidoso (DNS/TCP/TLS/primer segmento); la industria lo
    //   reporta por mediana/percentil. Medir la mediana de 3 hace el gate robusto a outliers.
    // -------------------------------------------------------------------------
    @Test
    fun ttffMedian_isBelowCeiling() {
        val samples = (1..3).map { measureTtffOnce() }
        samples.forEach {
            assertWithMessage("El SDK nunca renderizó el primer frame en una de las muestras")
                .that(it).isAtLeast(0L)
        }
        val median = samples.sorted()[samples.size / 2]
        assertWithMessage(
            "TTFF mediana=${median}ms (muestras=${samples.sorted()}) supera el techo de CI " +
            "${TTFF_CEILING_MS}ms (target industria < 2000ms VOD)"
        ).that(median).isLessThan(TTFF_CEILING_MS)
    }

    /** Mide el TTFF de un arranque aislado (scenario fresco) y cierra. */
    private fun measureTtffOnce(): Long {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", READY_TIMEOUT)
            return scenario.awaitFirstFrame(READY_TIMEOUT)
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
