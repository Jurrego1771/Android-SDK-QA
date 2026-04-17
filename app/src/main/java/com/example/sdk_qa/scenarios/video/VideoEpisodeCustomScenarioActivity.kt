package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.LogCategory
import com.example.sdk_qa.core.TestContent

/**
 * [05] Episode — Siguiente episodio modo Custom (manual)
 *
 * El cliente controla la lista de episodios y confirma el siguiente
 * via [MediastreamPlayer.updateNextEpisode()].
 *
 * Flujo:
 * 1. SDK dispara nextEpisodeIncoming(id) — 3s antes del overlay
 * 2. Esta Activity llama updateNextEpisode(nextConfig)
 * 3. El SDK muestra el overlay
 */
class VideoEpisodeCustomScenarioActivity : BaseScenarioActivity() {

    private val episodeIds = listOf(
        TestContent.Video.EPISODE_CUSTOM_1,
        TestContent.Video.EPISODE_CUSTOM_2,
        TestContent.Video.EPISODE_CUSTOM_3
    )
    private var currentIndex = 0

    override fun getScenarioTitle() = "Episode — Custom Mode"

    override fun buildConfig() = buildConfigForIndex(currentIndex)

    override fun onNextEpisodeIncoming(nextEpisodeId: String) {
        val nextIndex = currentIndex + 1
        if (nextIndex < episodeIds.size) {
            val nextConfig = buildConfigForIndex(nextIndex)
            player?.updateNextEpisode(nextConfig)
            log(
                "→ updateNextEpisode() llamado",
                detail = "index=$nextIndex id=${episodeIds[nextIndex]}",
                category = LogCategory.NAVIGATION
            )
        } else {
            log("→ Último episodio — no se llama updateNextEpisode()", category = LogCategory.NAVIGATION)
        }
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Seek Final -20s") {
            val dur = player?.msPlayer?.duration ?: 0L
            if (dur > 20_000L) player?.msPlayer?.seekTo(dur - 20_000L)
        }
        addButton(container, "Ep. 1") {
            currentIndex = 0
            player?.reloadPlayer(buildConfigForIndex(0))
        }
        addButton(container, "Ep. 2") {
            currentIndex = 1
            player?.reloadPlayer(buildConfigForIndex(1))
        }
        addButton(container, "Ep. 3 (último)") {
            currentIndex = 2
            player?.reloadPlayer(buildConfigForIndex(2))
        }
    }

    private fun buildConfigForIndex(index: Int) = MediastreamPlayerConfig().apply {
        id = episodeIds[index]
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.EPISODE
        environment = TestContent.ENV
        // Señal para activar modo manual: nextEpisodeId != null
        nextEpisodeId = if (index < episodeIds.lastIndex) "has_next" else null
        nextEpisodeTime = 15
        autoplay = true
        isDebug = true
    }
}
