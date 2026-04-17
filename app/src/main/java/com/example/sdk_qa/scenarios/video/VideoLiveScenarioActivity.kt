package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/** [02] Live sin DVR — play, callbacks de live, reconexión */
class VideoLiveScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video Live"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.LIVE
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        environment = TestContent.ENV
        dvr = false
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
