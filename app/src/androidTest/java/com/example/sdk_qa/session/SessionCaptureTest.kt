package com.example.sdk_qa.session

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoAdsScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeApiScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDvrScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoSubtitleScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captura de SESIÓN (Capa C) para diff entre versiones del SDK (10.0.7 vs 10.0.8).
 *
 * NO es un gate: abre cada escenario, deja reproducir un soak (para que el timeline capture
 * bitrate/buffering/etc.) y lo cierra. Al cerrarse, `SessionExporter.export` (vía
 * ObservabilityProvider.onActivityDestroyed) escribe el JSON normalizado a
 * `getExternalFilesDir("sessions")/<scenario>-sdk<version>-<ts>.json`.
 *
 * Tolerante a fallos: si un escenario no alcanza onReady (p.ej. stream live caído), igual cierra
 * y exporta el timeline parcial — el test NUNCA falla (es captura, no verificación).
 *
 * IMPORTANTE: correr vía `am instrument` DIRECTO (sin Orchestrator). El Orchestrator usa
 * `clearPackageData`, que borra getExternalFilesDir tras cada test → los JSON se perderían. El paso
 * `--capture-sessions` de scripts/run-tests.sh lo corre así y hace `adb pull` de los JSON.
 *
 * Soak configurable: `-e sessionSoakMs <ms>` (default 8000).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SessionCaptureTest {

    private val readyTimeoutMs = 25_000L
    private val soakMs: Long
        get() = InstrumentationRegistry.getArguments().getString("sessionSoakMs")?.toLongOrNull() ?: 8_000L

    private fun <T : BaseScenarioActivity> capture(clazz: Class<T>) {
        // .use{} cierra al final → finish → onDestroy → SessionExporter.export.
        ActivityScenario.launch(clazz).use { scenario ->
            // Esperar onReady sin assert (tolerante): si no llega, igual exportamos lo que haya.
            runCatching {
                var captorReady = false
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < readyTimeoutMs && !captorReady) {
                    scenario.onActivity { captorReady = it.callbackCaptor.hasEvent("onReady") }
                    if (!captorReady) Thread.sleep(500)
                }
            }
            // Soak: dejar correr para que el timeline capture eventos de reproducción reales.
            Thread.sleep(soakMs)
        }
        // Pequeña pausa para que onDestroy + escritura síncrona del export terminen.
        Thread.sleep(500)
    }

    @Test fun capture_vod()       = capture(VideoVodScenarioActivity::class.java)
    @Test fun capture_live()      = capture(VideoLiveScenarioActivity::class.java)
    @Test fun capture_livedvr()   = capture(VideoLiveDvrScenarioActivity::class.java)
    @Test fun capture_lived()     = capture(VideoLiveDScenarioActivity::class.java)
    @Test fun capture_episode()   = capture(VideoEpisodeApiScenarioActivity::class.java)
    @Test fun capture_ads()       = capture(VideoAdsScenarioActivity::class.java)
    @Test fun capture_subtitles() = capture(VideoSubtitleScenarioActivity::class.java)
}
