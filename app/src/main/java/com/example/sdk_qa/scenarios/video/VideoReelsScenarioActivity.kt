package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import com.example.sdk_qa.core.FullScreenScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [10] Reels — Contenido corto vertical, pantalla completa.
 *
 * Extiende FullScreenScenarioActivity: sin chrome de UI, sin debug panel.
 * Todos los eventos van a Logcat con tag SDK_QA/VideoReels.
 *
 * playerId es obligatorio para reels — apunta al player configurado en plataforma.
 */
class VideoReelsScenarioActivity : FullScreenScenarioActivity() {

    override fun getScenarioTitle() = "VideoReels"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Reels.MEDIA_INITIAL
        playerId = TestContent.Reels.PLAYER_ID
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        loop = false
        isDebug = true
        appHandlesWindowInsets = true
        pauseOnScreenClick = MediastreamPlayerConfig.FlagStatus.DISABLE
        showDismissButton = true
        trackEnable = false
    }
}
