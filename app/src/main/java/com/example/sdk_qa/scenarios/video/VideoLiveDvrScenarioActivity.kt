package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/** [03] Live con DVR — seek atrás, live edge, fictitious timeline */
class VideoLiveDvrScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video Live + DVR"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.LIVE_DVR
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        environment = TestContent.ENV
        dvr = true
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Seek -5 min") {
            player?.msPlayer?.let { exo ->
                val newPos = (exo.currentPosition - 5 * 60 * 1000L).coerceAtLeast(0L)
                exo.seekTo(newPos)
            }
        }
        addButton(container, "Seek -30 min") {
            player?.msPlayer?.let { exo ->
                val newPos = (exo.currentPosition - 30 * 60 * 1000L).coerceAtLeast(0L)
                exo.seekTo(newPos)
            }
        }
        addButton(container, "Live Edge") {
            // Ir al borde del live (posición máxima)
            player?.msPlayer?.let { exo ->
                exo.seekTo(exo.duration.coerceAtLeast(0L))
            }
        }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
