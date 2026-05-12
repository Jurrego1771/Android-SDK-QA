package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/** [17] VOD con subtítulos — verifica config showSubtitles y selección de track */
class VideoSubtitleScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video + Subtítulos"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_WITH_SUBTITLES
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
        showSubtitles = MediastreamPlayerConfig.FlagStatus.ENABLE
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Subtítulos ON") {
            player?.reloadPlayer(buildConfig().apply {
                showSubtitles = MediastreamPlayerConfig.FlagStatus.ENABLE
            })
        }
        addButton(container, "Subtítulos OFF") {
            player?.reloadPlayer(buildConfig().apply {
                showSubtitles = MediastreamPlayerConfig.FlagStatus.DISABLE
            })
        }
        addButton(container, "Menú tracks") {
            player?.showSubtitleAudioMenuForTV()
        }
    }
}
