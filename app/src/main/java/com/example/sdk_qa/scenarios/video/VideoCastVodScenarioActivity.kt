package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [Cast] VOD con Chromecast habilitado.
 *
 * Identico al VOD basico pero con `castAvailable = true`: el SDK inyecta el MediaRouteButton
 * en el player view e inicializa el contexto de cast (solo en movil, no TV). Sirve para probar
 * el flujo de casting: descubrir receptor, iniciar/terminar sesion, transferir reproduccion.
 *
 * Los callbacks de cast (onCastAvailable, onCastSession*) se registran en el log del Debug Panel
 * (categoria CAST). Al castear, msPlayer pasa de ExoPlayer a CastPlayer — PlaybackMetrics se
 * re-engancha de forma segura (ignora el CastPlayer).
 */
class VideoCastVodScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Cast VOD"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_LONG
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
        castAvailable = true   // habilita Chromecast: MediaRouteButton + cast context
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Play/Pause") {
            player?.msPlayer?.let { exo -> if (exo.isPlaying) exo.pause() else exo.play() }
        }
        addButton(container, "Seek 30s") {
            player?.msPlayer?.seekTo(30_000L)
        }
        addButton(container, "Seek Mitad") {
            val half = (player?.msPlayer?.duration ?: 0L) / 2
            if (half > 0) player?.msPlayer?.seekTo(half)
        }
        addButton(container, "Recargar") {
            player?.reloadPlayer(buildConfig())
        }
    }
}
