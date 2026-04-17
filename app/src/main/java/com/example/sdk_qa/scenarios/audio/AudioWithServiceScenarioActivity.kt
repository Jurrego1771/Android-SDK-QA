package com.example.sdk_qa.scenarios.audio

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.LogCategory
import com.example.sdk_qa.core.TestContent

/**
 * [15] Audio + Service — Radio con notificación persistente y background playback
 *
 * Verifica: notificación de media aparece, controles Next en notificación,
 * reproducción continúa con pantalla apagada.
 *
 * NOTA v11: notificationTitle / notificationDescription / notificationHasPrev
 * no existen en la config — el SDK toma los metadatos del contenido.
 */
class AudioWithServiceScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Audio + Service"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Audio.LIVE
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        playerType = MediastreamPlayerConfig.PlayerType.AUDIO
        environment = TestContent.ENV
        notificationHasNext = true
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "→ VOD") {
            val cfg = buildConfig().apply {
                id = TestContent.Audio.VOD
                type = MediastreamPlayerConfig.VideoTypes.VOD
            }
            player?.reloadPlayer(cfg)
            log("Switch a Audio VOD", category = LogCategory.NAVIGATION)
        }
        addButton(container, "→ Episode") {
            val cfg = buildConfig().apply {
                id = TestContent.Audio.EPISODE_1
                type = MediastreamPlayerConfig.VideoTypes.EPISODE
            }
            player?.reloadPlayer(cfg)
            log("Switch a Audio Episode", category = LogCategory.NAVIGATION)
        }
        addButton(container, "Ir a bg") {
            log("Presiona Home para verificar bg playback", category = LogCategory.SYSTEM)
        }
    }
}
