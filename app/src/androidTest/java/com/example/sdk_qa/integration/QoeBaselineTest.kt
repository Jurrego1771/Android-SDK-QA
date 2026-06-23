package com.example.sdk_qa.integration

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.core.PlaybackMetrics
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.awaitFirstFrame
import com.example.sdk_qa.utils.metricsSnapshot
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline de QoE — NO es un gate, es una MEDICIÓN. Reproduce el VOD canónico varias veces y
 * loguea los valores reales de TTFF/rebuffer/bitrate del SDK para calibrar los umbrales de
 * [PlaybackQoeTest] con números reales en vez de techos adivinados.
 *
 * Cómo correrlo limpio (sin uiautomator/screencap robando CPU/GPU durante la medición):
 *
 *   adb logcat -c
 *   adb shell am instrument -w \
 *     -e class com.example.sdk_qa.integration.QoeBaselineTest \
 *     com.example.sdk_qa.test/androidx.test.runner.AndroidJUnitRunner
 *   adb logcat -d -s QOE_BASELINE:I
 *
 * Cada iteración es una sesión de reproducción fresca (ActivityScenario nuevo). Se reporta cada
 * iteración + un resumen min/mediana/max. @LargeTest para que NO entre en la suite de CI normal
 * (size medium) — es on-demand.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class QoeBaselineTest {

    private val TAG = "QOE_BASELINE"
    private val READY_TIMEOUT = 15_000L
    private val SOAK_MS = 30_000L
    private val ITERATIONS = 3

    @Test
    fun measureBaseline_vodCdn() {
        val snaps = mutableListOf<PlaybackMetrics.Snapshot>()

        Log.i(TAG, "═══ BASELINE START · ${ITERATIONS} iters · soak=${SOAK_MS}ms · device=${android.os.Build.MODEL} ═══")

        repeat(ITERATIONS) { i ->
            ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
                scenario.awaitCallback("onPlay", READY_TIMEOUT)
                val ttff = scenario.awaitFirstFrame(READY_TIMEOUT)
                Thread.sleep(SOAK_MS)

                val m = scenario.metricsSnapshot()
                snaps += m
                Log.i(TAG, line(i + 1, ttff, m))
            }
            // Pausa breve entre iteraciones para que el device no acumule estado térmico/red.
            Thread.sleep(2_000)
        }

        // --- Resumen estadístico ---
        val ttffs = snaps.map { it.ttffMs }.filter { it >= 0 }.sorted()
        val ratios = snaps.map { it.rebufferRatio }.sorted()
        val rebufs = snaps.map { it.rebufferCount }.sorted()
        val bitrates = snaps.map { it.currentBitrateBps }.filter { it > 0 }.sorted()
        val drops = snaps.map { it.droppedFrames }.sorted()

        Log.i(TAG, "═══ SUMMARY (n=${snaps.size}) ═══")
        Log.i(TAG, "TTFF ms      min=${ttffs.firstOrNull()} med=${median(ttffs)} max=${ttffs.lastOrNull()}")
        Log.i(TAG, "RebufRatio   min=${fmt(ratios.firstOrNull())} med=${fmt(medianD(ratios))} max=${fmt(ratios.lastOrNull())}")
        Log.i(TAG, "RebufCount   min=${rebufs.firstOrNull()} med=${median(rebufs.map { it.toLong() })} max=${rebufs.lastOrNull()}")
        Log.i(TAG, "Bitrate bps  min=${bitrates.firstOrNull()} med=${median(bitrates.map { it.toLong() })} max=${bitrates.lastOrNull()}")
        Log.i(TAG, "Dropped      min=${drops.firstOrNull()} med=${median(drops.map { it.toLong() })} max=${drops.lastOrNull()}")
        Log.i(TAG, "═══ BASELINE END ═══")
    }

    private fun line(iter: Int, ttff: Long, m: PlaybackMetrics.Snapshot): String = buildString {
        append("iter=$iter ")
        append("ttffMs=$ttff ")
        append("rebufCount=${m.rebufferCount} rebufMs=${m.rebufferMs} rebufRatio=${fmt(m.rebufferRatio)} ")
        append("bitrateBps=${m.currentBitrateBps} switches=${m.bitrateSwitches} ")
        append("bwBps=${m.measuredBandwidthBps} ")
        append("dropped=${m.droppedFrames} res=${m.resolution} codec=${m.videoCodec} ")
        append("bufMs=${m.bufferHealthMs} loadErr=${m.loadErrorCount}")
    }

    private fun median(xs: List<Long>): Long? =
        if (xs.isEmpty()) null else xs.sorted()[xs.size / 2]

    private fun medianD(xs: List<Double>): Double? =
        if (xs.isEmpty()) null else xs.sorted()[xs.size / 2]

    private fun fmt(d: Double?): String = if (d == null) "—" else "%.4f".format(d)
}
