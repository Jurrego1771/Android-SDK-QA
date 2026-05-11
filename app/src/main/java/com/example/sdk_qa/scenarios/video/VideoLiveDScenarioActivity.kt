package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

class VideoLiveDScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Live D — DRM"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id          = TestContent.Drm.LIVE_ID
        type        = MediastreamPlayerConfig.VideoTypes.LIVE
        environment = TestContent.Drm.ENV
        videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH
        dvr         = true
        autoplay    = true
        isDebug     = true
        drmData     = MediastreamPlayerConfig.DrmData(
            drmUrl     = TestContent.Drm.LICENSE_URL,
            drmHeaders = mapOf("X-AxDRM-Message" to TestContent.Drm.ACCESS_TOKEN)
        )
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
