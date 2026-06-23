package com.example.sdk_qa.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.sdk_qa.integration.DirectHlsActivity
import com.example.sdk_qa.scenarios.audio.AudioEpisodeScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioLiveScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioVodScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioWithServiceScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoAdsScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoCastLiveScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoCastVodScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoContentSwitcherActivity
import com.example.sdk_qa.scenarios.video.VideoDrmScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeApiScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeCustomScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoFullscreenOverrideScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDvrScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoPipScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoReelsScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoSubtitleScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoWithServiceScenarioActivity

/**
 * Router de deep links — SOLO debug.
 *
 * Permite lanzar cualquier escenario de forma determinista desde adb / Appium:
 *   adb shell am start -W -a android.intent.action.VIEW -d "sdkqa://scenario/vod"
 *
 * Las Activities de escenario NO están exportadas (SecurityException si se lanzan
 * directo desde adb, uid externo). Este router SÍ está exportado (solo en el
 * source set debug) y lanza la Activity interna desde el mismo proceso/uid, así
 * que no hay restricción de export. Es el mecanismo de navegación del harness de
 * testing exploratorio asistido por IA (ver docs/testing/exploratory-ai.md).
 *
 * Esta clase vive en app/src/debug → ausente en release builds.
 */
class DeepLinkRouterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sdkqa://scenario/<key>  → host="scenario", lastPathSegment="<key>"
        val key = intent?.data?.lastPathSegment?.lowercase()?.trim()
        val target = key?.let { SCENARIOS[it] }

        if (target == null) {
            val msg = "Deep link desconocido: '${intent?.data}'. Claves: ${SCENARIOS.keys.sorted().joinToString()}"
            Log.e(TAG, msg)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.i(TAG, "Deep link '$key' → ${target.simpleName}")
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    companion object {
        private const val TAG = "SDK_QA_DEEPLINK"

        /** Mapa estable clave→escenario. Las claves son el contrato del harness IA. */
        val SCENARIOS: Map<String, Class<out Activity>> = mapOf(
            // Video
            "vod" to VideoVodScenarioActivity::class.java,
            "live" to VideoLiveScenarioActivity::class.java,
            "livedvr" to VideoLiveDvrScenarioActivity::class.java,
            "lived" to VideoLiveDScenarioActivity::class.java,
            "episode" to VideoEpisodeApiScenarioActivity::class.java,
            "episode-custom" to VideoEpisodeCustomScenarioActivity::class.java,
            "ads" to VideoAdsScenarioActivity::class.java,
            "drm" to VideoDrmScenarioActivity::class.java,
            "pip" to VideoPipScenarioActivity::class.java,
            "reels" to VideoReelsScenarioActivity::class.java,
            "switcher" to VideoContentSwitcherActivity::class.java,
            "cast-vod" to VideoCastVodScenarioActivity::class.java,
            "cast-live" to VideoCastLiveScenarioActivity::class.java,
            "subtitles" to VideoSubtitleScenarioActivity::class.java,
            "fullscreen" to VideoFullscreenOverrideScenarioActivity::class.java,
            "service" to VideoWithServiceScenarioActivity::class.java,
            // Audio
            "audio-live" to AudioLiveScenarioActivity::class.java,
            "audio-vod" to AudioVodScenarioActivity::class.java,
            "audio-episode" to AudioEpisodeScenarioActivity::class.java,
            "audio-service" to AudioWithServiceScenarioActivity::class.java,
            // Debug-only
            "direct-hls" to DirectHlsActivity::class.java,
        )
    }
}
