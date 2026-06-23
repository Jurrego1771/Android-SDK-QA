package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [Cast] Live con Chromecast habilitado.
 *
 * Stream en vivo (sin DVR) con `castAvailable = true`: el SDK inyecta el MediaRouteButton y monta
 * el contexto de cast (solo en movil). Para probar casting de contenido en vivo: descubrir
 * receptor, iniciar/terminar sesion, y como se comporta el live al transferir al receptor.
 *
 * Los callbacks de cast (onCastAvailable, onCastSession*) se registran en el log del Debug Panel
 * (categoria CAST).
 */
class VideoCastLiveScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Cast Live"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.LIVE
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        environment = TestContent.ENV
        dvr = false
        autoplay = true
        isDebug = true
        castAvailable = true   // habilita Chromecast: MediaRouteButton + cast context
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play") { player?.msPlayer?.play() }
        addButton(container, "Pause") { player?.msPlayer?.pause() }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
