package com.example.sdk_qa.scenarios.audio

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.LogCategory
import com.example.sdk_qa.core.TestContent

/**
 * [14] Audio Episode — Podcast por episodios con navegación entre capítulos
 *
 * Verifica: nextEpisodeIncoming callback en modo audio, navegación manual
 * entre episodios, que updateNextEpisode() funcione con playerType=AUDIO.
 */
class AudioEpisodeScenarioActivity : BaseScenarioActivity() {

    private val episodes = listOf(
        TestContent.Audio.EPISODE_1,
        TestContent.Audio.EPISODE_2,
        TestContent.Audio.EPISODE_3
    )
    private var currentEpisode = 0

    override fun getScenarioTitle() = "Audio Episode [${currentEpisode + 1}/${episodes.size}]"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = episodes[currentEpisode]
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.EPISODE
        playerType = MediastreamPlayerConfig.PlayerType.AUDIO
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "← Anterior") {
            if (currentEpisode > 0) {
                currentEpisode--
                reloadCurrent()
            } else {
                log("Ya en el primer episodio", category = LogCategory.NAVIGATION)
            }
        }
        addButton(container, "Siguiente →") {
            if (currentEpisode < episodes.lastIndex) {
                currentEpisode++
                reloadCurrent()
            } else {
                log("Ya en el último episodio", category = LogCategory.NAVIGATION)
            }
        }
        addButton(container, "Seek final") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 15_000L) {
                player?.msPlayer?.seekTo(dur - 12_000L)
                log("Seek → final para disparar nextEpisodeIncoming", category = LogCategory.NAVIGATION)
            }
        }
        addButton(container, "updateNext") {
            // Simula que se recibió nextEpisodeIncoming y se llama updateNextEpisode()
            if (currentEpisode < episodes.lastIndex) {
                val nextConfig = MediastreamPlayerConfig().apply {
                    id = episodes[currentEpisode + 1]
                    accountID = TestContent.ACCOUNT_ID
                    type = MediastreamPlayerConfig.VideoTypes.EPISODE
                    playerType = MediastreamPlayerConfig.PlayerType.AUDIO
                    environment = TestContent.ENV
                }
                player?.updateNextEpisode(nextConfig)
                log("updateNextEpisode(${episodes[currentEpisode + 1]})", category = LogCategory.NAVIGATION)
            }
        }
    }

    private fun reloadCurrent() {
        log("→ Episodio ${currentEpisode + 1}", category = LogCategory.NAVIGATION)
        player?.reloadPlayer(buildConfig())
    }
}
