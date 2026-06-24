package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [VOD loop] VOD con `loop = true` para validar el comportamiento de fin-de-reproducción.
 *
 * Complementa VideoVodScenarioActivity (loop=false). El CHANGELOG 10.0.8 corrige bugs de
 * end-of-playback ("video restarting instead of stopping", "unintended looping in manual repeat
 * mode"); este escenario permite verificar el camino loop=true sin tocar el VOD básico.
 */
class VideoVodLoopScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "VOD Loop"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_LONG
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        loop = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Seek Final -3s") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 5_000L) player?.msPlayer?.seekTo(dur - 3_000L)
        }
    }
}
