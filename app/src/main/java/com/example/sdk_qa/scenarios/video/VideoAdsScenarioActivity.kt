package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/** [06] VOD con Ads (IMA Preroll) — onAdEvents, error fallback al contenido */
class VideoAdsScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video + Ads (IMA)"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_WITH_ADS
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
        addButton(container, "Audio + Ads") {
            // Cambia a AOD con publicidad sin destruir el player
            val audioCfg = buildConfig().apply {
                id = TestContent.Audio.VOD_WITH_ADS
                type = MediastreamPlayerConfig.VideoTypes.VOD
                playerType = MediastreamPlayerConfig.PlayerType.AUDIO
            }
            player?.reloadPlayer(audioCfg)
        }
        addButton(container, "Skip Ad") {
            // ExoPlayer no expone skipAd() directamente.
            // Si el SDK lo soporta, estará en player?.skipAd() — verificar en runtime.
            log("Skip Ad — verificar si SDK expone este método", category = com.example.sdk_qa.core.LogCategory.AD)
        }
    }
}
