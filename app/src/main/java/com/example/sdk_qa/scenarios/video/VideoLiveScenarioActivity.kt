package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/** [02] Live con DRM Widevine — token expirado intencional para validar error de expiración */
class VideoLiveScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video Live DRM"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.LIVE
        //id = TestContent.Drm.LIVE_ID
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        environment = TestContent.ENV
        // environment = TestContent.Drm.ENV
        dvr = false
        //videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH
        autoplay = true
        isDebug = true
      //  drmData = MediastreamPlayerConfig.DrmData(
      //      TestContent.Drm.LICENSE_URL,
      //      mapOf("X-AxDRM-Message" to TestContent.Drm.ACCESS_TOKEN)
      //  )
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
