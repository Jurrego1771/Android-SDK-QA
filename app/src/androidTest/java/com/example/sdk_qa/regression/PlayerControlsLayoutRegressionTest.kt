package com.example.sdk_qa.regression

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoEpisodeApiScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoFullscreenOverrideScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveDvrScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoLiveScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoSubtitleScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import com.example.sdk_qa.utils.Orient
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.collectLayoutViolations
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.captureLayoutBaseline
import com.example.sdk_qa.utils.revealAndReadAnchors
import com.example.sdk_qa.utils.setOrientation
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Batería de regresión de LAYOUT de los controles del player del SDK.
 *
 * Detecta regresiones de POSICIÓN de los controles entre versiones del SDK (bug: con
 * 10.0.8-alpha06 los controles salen "más arriba" — sutil en portrait, pronunciado en LANDSCAPE).
 * Gate = bounds numéricos normalizados (ver [com.example.sdk_qa.debug.LayoutExporter]); los
 * screenshots de evidencia los captura [com.example.sdk_qa.utils.SdkEvidenceRule] al fallar.
 *
 * Mide en PORTRAIT y LANDSCAPE (la baseline lleva sufijo por orientación). Dos modos
 * (instrumentation arg `captureBaseline`):
 *  - `-e captureBaseline true` → CAPTURA la baseline al device. Bootstrap con la versión buena (10.0.7).
 *  - default → ASSERT contra `androidTest/assets/layout-baselines/<scenario>-<orient>.layout.json`.
 *
 * @LargeTest → no entra en `--size medium`; se corre con `--size large` o `--class`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PlayerControlsLayoutRegressionTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT_READY = 25_000L

    private val captureMode: Boolean
        get() = InstrumentationRegistry.getArguments().getString("captureBaseline") == "true"

    private fun <T : BaseScenarioActivity> runScenario(scenarioKey: String, clazz: Class<T>) {
        ActivityScenario.launch(clazz).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT_READY)
            val allViolations = mutableListOf<String>()
            for (orient in listOf(Orient.PORT, Orient.LAND)) {
                scenario.setOrientation(orient)
                val anchors = scenario.revealAndReadAnchors()
                if (captureMode) {
                    scenario.captureLayoutBaseline(scenarioKey, orient, anchors)
                } else {
                    allViolations += collectLayoutViolations(scenarioKey, orient, anchors)
                }
            }
            // Assert al final → reporta port Y land juntas (no aborta en la primera).
            if (!captureMode) {
                assertWithMessage(
                    "[LAYOUT-REG] $scenarioKey: ${allViolations.size} desviación(es) (tol=0.01)\n" +
                        allViolations.joinToString("\n").ifEmpty { "(ninguna)" }
                ).that(allViolations).isEmpty()
            }
        }
    }

    @Test fun layout_vod()        = runScenario("vod", VideoVodScenarioActivity::class.java)
    @Test fun layout_live()       = runScenario("live", VideoLiveScenarioActivity::class.java)
    @Test fun layout_livedvr()    = runScenario("livedvr", VideoLiveDvrScenarioActivity::class.java)
    @Test fun layout_lived()      = runScenario("lived", VideoLiveDScenarioActivity::class.java)
    @Test fun layout_episode()    = runScenario("episode", VideoEpisodeApiScenarioActivity::class.java)
    @Test fun layout_subtitles()  = runScenario("subtitles", VideoSubtitleScenarioActivity::class.java)
    @Test fun layout_fullscreen() = runScenario("fullscreen", VideoFullscreenOverrideScenarioActivity::class.java)
}
