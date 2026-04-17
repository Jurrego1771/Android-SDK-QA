package com.example.sdk_qa.scenarios.audio

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [13] Audio VOD — Podcast / audio bajo demanda
 *
 * Verifica: seek en audio, progress bar, duración reportada en callbacks,
 * onEnd al finalizar, reproducción sin video.
 */
class AudioVodScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Audio VOD"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Audio.VOD
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        playerType = MediastreamPlayerConfig.PlayerType.AUDIO
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Seek +30s") {
            val pos = (player?.msPlayer?.currentPosition ?: 0L) + 30_000L
            player?.msPlayer?.seekTo(pos)
            log("Seek → ${pos / 1000}s")
        }
        addButton(container, "Seek -30s") {
            val pos = ((player?.msPlayer?.currentPosition ?: 0L) - 30_000L).coerceAtLeast(0L)
            player?.msPlayer?.seekTo(pos)
            log("Seek → ${pos / 1000}s")
        }
        addButton(container, "Ir al final") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 10_000L) {
                player?.msPlayer?.seekTo(dur - 8_000L)
                log("Seek → final (~${dur / 1000}s)")
            }
        }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
