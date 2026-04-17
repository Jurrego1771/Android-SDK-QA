package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [01] Video VOD Básico
 *
 * Verifica: play, pause, seek, onEnd, ciclo de callbacks correcto.
 * Contenido: VOD de duración media desde el entorno DEV.
 */
class VideoVodScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video VOD"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_LONG
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Seek 30s") {
            player?.msPlayer?.seekTo(30_000L)
        }
        addButton(container, "Seek Mitad") {
            val half = (player?.msPlayer?.duration ?: 0L) / 2
            if (half > 0) player?.msPlayer?.seekTo(half)
        }
        addButton(container, "Seek Final") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 5_000L) player?.msPlayer?.seekTo(dur - 5_000L)
        }
        addButton(container, "Play/Pause") {
            player?.msPlayer?.let { exo ->
                if (exo.isPlaying) exo.pause() else exo.play()
            }
        }
        addButton(container, "Recargar") {
            player?.reloadPlayer(buildConfig())
        }
        addButton(container, "URL directa") {
            // Recarga usando src directo sin llamada a la API
            val config = MediastreamPlayerConfig().apply {
                src = TestContent.Video.SRC_DIRECT_HLS
                type = MediastreamPlayerConfig.VideoTypes.VOD
                environment = TestContent.ENV
                autoplay = true
                isDebug = true
            }
            player?.reloadPlayer(config)
        }
    }
}
