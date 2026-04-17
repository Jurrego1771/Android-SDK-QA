package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.LogCategory
import com.example.sdk_qa.core.TestContent

/**
 * [11] Content Switcher — Cambio de contenido sin destruir el player
 *
 * Permite cambiar entre VOD / Live / Episode / Audio usando reloadPlayer()
 * sin recrear la Activity. Ideal para detectar fugas de estado entre contenidos.
 */
class VideoContentSwitcherActivity : BaseScenarioActivity() {

    private var currentLabel = "VOD"

    override fun getScenarioTitle() = "Content Switcher — $currentLabel"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "VOD") { switchTo("VOD", vodConfig()) }
        addButton(container, "Live") { switchTo("Live", liveConfig()) }
        addButton(container, "DVR") { switchTo("DVR", dvrConfig()) }
        addButton(container, "Episode") { switchTo("Episode", episodeConfig()) }
        addButton(container, "Audio Live") { switchTo("Audio Live", audioLiveConfig()) }
        addButton(container, "Audio VOD") { switchTo("Audio VOD", audioVodConfig()) }
    }

    private fun switchTo(label: String, config: MediastreamPlayerConfig) {
        currentLabel = label
        log("→ Switch a $label", category = LogCategory.NAVIGATION)
        player?.reloadPlayer(config)
        log("→ $label", category = LogCategory.NAVIGATION)
    }

    private fun vodConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT; accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD; environment = TestContent.ENV; autoplay = true; isDebug = true
    }

    private fun liveConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.LIVE; accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE; environment = TestContent.ENV; dvr = false; autoplay = true; isDebug = true
    }

    private fun dvrConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.LIVE_DVR; accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE; environment = TestContent.ENV; dvr = true; autoplay = true; isDebug = true
    }

    private fun episodeConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.EPISODE_WITH_NEXT_API; accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.EPISODE; environment = TestContent.ENV; autoplay = true; isDebug = true
    }

    private fun audioLiveConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Audio.LIVE; accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        playerType = MediastreamPlayerConfig.PlayerType.AUDIO
        environment = TestContent.ENV; autoplay = true; isDebug = true
    }

    private fun audioVodConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Audio.VOD; accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        playerType = MediastreamPlayerConfig.PlayerType.AUDIO
        environment = TestContent.ENV; autoplay = true; isDebug = true
    }
}
