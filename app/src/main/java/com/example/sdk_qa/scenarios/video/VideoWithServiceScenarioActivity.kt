package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamMiniPlayerConfig
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayer
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [08] Video con MediastreamPlayerService
 *
 * Activa la notificación de media con controles Next/Prev.
 * Verificar: notificación aparece, botones funcionan, background playback.
 */
class VideoWithServiceScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video + Service"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        notificationHasNext = true
        // notificationTitle / notificationDescription no existen en v11 — verificar en próxima versión
        autoplay = true
        isDebug = true
    }

    // El constructor con MiniPlayerConfig activa el service
    override fun onPlayerCreated() {
        // El service se activa al pasar MediastreamMiniPlayerConfig
        // Si el player ya fue creado en BaseScenarioActivity con el constructor estándar,
        // la notificación se gestiona automáticamente por el SDK.
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Ir a bg") {
            // Instrucción visual — presionar Home para ir a background
        }
    }
}
