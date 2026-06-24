package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [Cast] Episode con Chromecast habilitado.
 *
 * Episode en modo API (siguiente automatico desde la plataforma) con `castAvailable = true`.
 * Objetivo: validar que la **sesion de cast se mantiene al cambiar de episodio** — al auto-avanzar
 * (onNext → onNewSourceAdded) la reproduccion en el receptor no debe cortarse ni requerir reconectar.
 *
 * El overlay de "siguiente episodio" aparece `nextEpisodeTime` segundos antes del final; el boton
 * "Seek Final -20s" permite forzar la transicion rapido para probar el cast.
 */
class VideoCastEpisodeScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Cast Episode"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.EPISODE_WITH_NEXT_API
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.EPISODE
        environment = TestContent.ENV
        nextEpisodeTime = 15   // overlay 15s antes del final
        autoplay = true
        isDebug = true
        castAvailable = true   // habilita Chromecast: MediaRouteButton + cast context
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Seek Final -20s") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 20_000L) player?.msPlayer?.seekTo(dur - 20_000L)
        }
        addButton(container, "Play/Pause") {
            player?.msPlayer?.let { exo -> if (exo.isPlaying) exo.pause() else exo.play() }
        }
        addButton(container, "Recargar") { player?.reloadPlayer(buildConfig()) }
    }
}
