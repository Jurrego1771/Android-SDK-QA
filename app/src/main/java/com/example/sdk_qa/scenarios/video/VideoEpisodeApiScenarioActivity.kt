package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [04] Episode — Siguiente episodio modo API
 *
 * El SDK detecta el siguiente desde la plataforma.
 * El overlay aparece automáticamente sin intervención del cliente.
 * Verificar: timing del overlay, botones "Ver créditos" y "Siguiente".
 */
class VideoEpisodeApiScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Episode — API Mode"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.EPISODE_WITH_NEXT_API
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.EPISODE
        environment = TestContent.ENV
        nextEpisodeTime = 15   // overlay 15s antes del final
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Seek Final -20s") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 20_000L) player?.msPlayer?.seekTo(dur - 20_000L)
        }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
