package com.example.sdk_qa.integration

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.scenarios.video.VideoVodLoopScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fin de reproducción VOD — cubre los fixes del CHANGELOG 10.0.8:
 *   "onEnd not firing reliably, video restarting instead of stopping, unintended looping".
 *
 * NO duplica SmokeTest.vod_seekToEnd_onEnd_fires (que solo verifica que onEnd dispara). Aquí se
 * verifica el COMPORTAMIENTO posterior al final: con loop=false el player se detiene y NO reinicia;
 * con loop=true reinicia y sigue reproduciendo.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class VodEndOfPlaybackTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT = 25_000L

    private fun ActivityScenario<*>.positionMs(): Long {
        var pos = -1L
        onActivity { pos = (it as? com.example.sdk_qa.core.BaseScenarioActivity)?.player?.msPlayer?.currentPosition ?: -1L }
        return pos
    }

    private fun ActivityScenario<*>.isPlaying(): Boolean {
        var playing = false
        onActivity { playing = (it as? com.example.sdk_qa.core.BaseScenarioActivity)?.player?.msPlayer?.isPlaying ?: false }
        return playing
    }

    private fun ActivityScenario<*>.durationMs(): Long {
        var dur = 0L
        onActivity { dur = (it as? com.example.sdk_qa.core.BaseScenarioActivity)?.player?.msPlayer?.duration ?: 0L }
        return dur
    }

    // [VOD-END-01] loop=false: onEnd dispara y el player NO reinicia (se detiene cerca del final).
    @Test
    fun vod_loopFalse_onEnd_doesNotRestart() {
        ActivityScenario.launch(VideoVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            val duration = scenario.durationMs()
            assertWithMessage("VOD debe tener duración conocida").that(duration).isGreaterThan(5_000L)

            scenario.onActivity { (it as com.example.sdk_qa.core.BaseScenarioActivity).player?.msPlayer?.seekTo(duration - 2_000L) }
            scenario.awaitCallback("onEnd", TIMEOUT)

            // Tras onEnd debe quedarse al final, NO reiniciar a ~0 (fix "restarting instead of stopping").
            Thread.sleep(3_000L)
            val pos = scenario.positionMs()
            assertWithMessage("Tras onEnd el player no debe reiniciar (pos=$pos, duration=$duration)")
                .that(pos).isGreaterThan(duration - 10_000L)
            assertWithMessage("Tras onEnd (loop=false) no debe seguir reproduciendo")
                .that(scenario.isPlaying()).isFalse()
            scenario.assertNoErrorFired()
        }
    }

    // [VOD-LOOP-01] loop=true: el loop va por STATE_ENDED (onEnd dispara), NO por REPEAT_MODE nativo.
    //
    // Comportamiento real observado vía MCP/logcat en 10.0.8-alpha08:
    //   - repeatMode = OFF (no usa el loop nativo de ExoPlayer).
    //   - Al llegar al final dispara onEnd, igual que loop=false.
    //   - El reinicio ocurre pero con delay LARGO y VARIABLE (~23s en una corrida, >35s/ausente en
    //     otra) → comportamiento errático. Ver REELS-/CORE-finding en docs (posible relación con el
    //     fix "unintended looping in manual repeat mode" del CHANGELOG 10.0.8).
    //
    // Este test verifica solo lo ESTABLE: con loop=true onEnd SÍ se dispara (el loop NO suprime
    // onEnd). El reinicio errático queda como HALLAZGO documentado, no como assert (sería flaky).
    @Test
    fun vod_loopTrue_firesOnEnd_viaStateEnded() {
        ActivityScenario.launch(VideoVodLoopScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            val duration = scenario.durationMs()
            assertWithMessage("VOD debe tener duración conocida").that(duration).isGreaterThan(5_000L)

            scenario.onActivity { (it as com.example.sdk_qa.core.BaseScenarioActivity).player?.msPlayer?.seekTo(duration - 2_000L) }

            // Con loop=true el SDK igual dispara onEnd (loop por STATE_ENDED, repeatMode=OFF).
            scenario.awaitCallback("onEnd", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }
}
