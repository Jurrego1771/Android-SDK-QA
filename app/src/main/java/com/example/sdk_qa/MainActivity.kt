package com.example.sdk_qa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sdk_qa.core.ScenarioAdapter
import com.example.sdk_qa.core.ScenarioListItem
import com.example.sdk_qa.core.ScenarioListItem.Status.*
import com.example.sdk_qa.databinding.ActivityMainBinding
import com.example.sdk_qa.scenarios.audio.AudioEpisodeScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioLiveScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioVodScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioWithServiceScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoAdsScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoContentSwitcherActivity
import com.example.sdk_qa.scenarios.video.VideoDrmScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeApiScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeCustomScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDvrScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoPipScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoReelsScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoWithServiceScenarioActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerScenarios.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ScenarioAdapter(buildScenarioList())
            addItemDecoration(
                DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL)
            )
        }
    }

    /**
     * Lista de escenarios.
     * Actualizar el [ScenarioListItem.Status] a medida que se verifican las features.
     */
    private fun buildScenarioList(): List<ScenarioListItem> = listOf(

        // ---- VIDEO ----
        ScenarioListItem.Header("Video"),

        ScenarioListItem.Scenario(
            title = "VOD Básico",
            description = "Video bajo demanda — play, pause, seek, end",
            status = PENDING,
            activityClass = VideoVodScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Live",
            description = "Stream en vivo sin DVR — reconnection, callbacks",
            status = PENDING,
            activityClass = VideoLiveScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Live + DVR",
            description = "Stream en vivo con DVR — seek, live edge, fictitious timeline",
            status = PENDING,
            activityClass = VideoLiveDvrScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Episode — Next (API mode)",
            description = "Siguiente episodio auto desde la plataforma — overlay, countdown",
            status = PENDING,
            activityClass = VideoEpisodeApiScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Episode — Next (Custom mode)",
            description = "Siguiente episodio via updateNextEpisode() — modo manual",
            status = PENDING,
            activityClass = VideoEpisodeCustomScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Ads (IMA Preroll)",
            description = "VOD con anuncio preroll — callbacks onAdEvents, error fallback",
            status = PENDING,
            activityClass = VideoAdsScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "DRM (Widevine)",
            description = "VOD protegido — licencia, error de DRM",
            status = PENDING,
            activityClass = VideoDrmScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Con Service (notificación)",
            description = "Player con MediastreamPlayerService — background, next/prev notification",
            status = PENDING,
            activityClass = VideoWithServiceScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "PiP",
            description = "Picture-in-Picture — modos: small container, expand to fullscreen first",
            status = PENDING,
            activityClass = VideoPipScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Reels (vertical)",
            description = "Contenido corto en formato vertical — swipe para siguiente",
            status = PENDING,
            activityClass = VideoReelsScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Content Switcher",
            description = "Cambiar tipo de contenido sin destruir el player — VOD/Live/Episode/Audio",
            status = PENDING,
            activityClass = VideoContentSwitcherActivity::class.java
        ),

        // ---- AUDIO ----
        ScenarioListItem.Header("Audio"),

        ScenarioListItem.Scenario(
            title = "Audio Live (Radio)",
            description = "Stream de audio en vivo — Icecast/HLS, metadata SSE",
            status = PENDING,
            activityClass = AudioLiveScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Audio VOD (Podcast)",
            description = "Podcast / audio bajo demanda — ciclo completo",
            status = PENDING,
            activityClass = AudioVodScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Audio Episode",
            description = "Episodio de audio con siguiente — next episode flow",
            status = PENDING,
            activityClass = AudioEpisodeScenarioActivity::class.java
        ),
        ScenarioListItem.Scenario(
            title = "Audio + Service (background)",
            description = "Audio con MediastreamPlayerService — reproducción en background",
            status = PENDING,
            activityClass = AudioWithServiceScenarioActivity::class.java
        )
    )
}
