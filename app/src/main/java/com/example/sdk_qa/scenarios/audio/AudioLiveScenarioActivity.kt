package com.example.sdk_qa.scenarios.audio

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [12] Audio Live — Reproducción de radio/stream de audio en vivo
 *
 * Verifica: visualizador de audio, controles de volumen, notificación de media,
 * comportamiento en background (no debería haber video).
 */
class AudioLiveScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Audio Live"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Audio.LIVE
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        playerType = MediastreamPlayerConfig.PlayerType.AUDIO
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
        addButton(container, "Vol +") {
            val vol = (player?.msPlayer?.volume ?: 0.5f).coerceAtMost(1.0f - 0.1f) + 0.1f
            player?.msPlayer?.volume = vol
            log("Volumen: ${"%.0f".format(vol * 100)}%")
        }
        addButton(container, "Vol -") {
            val vol = ((player?.msPlayer?.volume ?: 0.5f) - 0.1f).coerceAtLeast(0f)
            player?.msPlayer?.volume = vol
            log("Volumen: ${"%.0f".format(vol * 100)}%")
        }
        addButton(container, "Mute") { player?.msPlayer?.volume = 0f }
    }
}
