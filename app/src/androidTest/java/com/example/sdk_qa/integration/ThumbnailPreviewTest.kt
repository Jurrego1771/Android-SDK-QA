package com.example.sdk_qa.integration

import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Trick-play / thumbnail preview — feature NUEVO del CHANGELOG 10.0.8 (MediastreamTimeBar,
 * ThumbnailPreviewLoader, PreviewDelegate) + su fix de layout shift.
 *
 * El VOD de prueba (TestContent.Video.VOD_LONG) tiene preview.jpg+vtt en su config de plataforma
 * (verificado vía API), así que el SDK activa el trick-play: `setupTrickPlay` pone el
 * `ms_preview_container` en VISIBLE y mantiene `ms_preview_frame_layout` en GONE en reposo (fix
 * "controles desplazados arriba": el frame solo se hace visible durante el scrub).
 *
 * Estos tests leen las vistas internas del SDK desde el view-tree (mismo patrón que LayoutExporter),
 * sin depender de scrub por UiAutomator (frágil con video). NO duplican PlayerControlsLayoutRegressionTest
 * (que mide POSICIÓN de controles); aquí se valida la EXISTENCIA/visibilidad del aparato de preview.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class ThumbnailPreviewTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT = 25_000L

    /** Todas las vistas con [name], con su visibilidad legible. Puede haber varias (layout base + activa). */
    private fun ActivityScenario<VideoVodScenarioActivity>.findAll(name: String): List<Pair<View, String>> {
        val out = mutableListOf<Pair<View, String>>()
        onActivity { activity ->
            val res = activity.resources
            val root = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
            fun vis(v: View) = when (v.visibility) {
                View.VISIBLE -> "VISIBLE"; View.INVISIBLE -> "INVISIBLE"; else -> "GONE"
            }
            fun walk(v: View) {
                if (v.id != View.NO_ID &&
                    runCatching { res.getResourceEntryName(v.id) }.getOrNull() == name) out.add(v to vis(v))
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(root)
        }
        return out
    }

    // [PREVIEW-01] Con preview configurado, el SDK monta el contenedor de trick-play (VISIBLE).
    @Test
    fun preview_container_isVisible_whenContentHasPreview() {
        ActivityScenario.launch(VideoVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            Thread.sleep(2_000L)   // setupTrickPlay corre tras la carga del media

            val containers = scenario.findAll("ms_preview_container")
            assertWithMessage("ms_preview_container debe existir cuando el VOD tiene preview.jpg")
                .that(containers).isNotEmpty()
            assertWithMessage("alguna ms_preview_container debe estar VISIBLE (trick-play activado)\n  encontradas: ${containers.map { it.second }}")
                .that(containers.any { it.second == "VISIBLE" }).isTrue()
            scenario.assertNoErrorFired()
        }
    }

    // [PREVIEW-02] FIX layout-shift: el frame de preview NO está VISIBLE en reposo (no reserva espacio).
    @Test
    fun preview_frame_notVisible_atRest() {
        ActivityScenario.launch(VideoVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            Thread.sleep(2_000L)

            val frames = scenario.findAll("ms_preview_frame_layout")
            assertWithMessage("ms_preview_frame_layout debe existir con preview configurado")
                .that(frames).isNotEmpty()
            // El fix fuerza GONE tras attachPreviewView (visible solo durante scrub). En reposo:
            // ninguna instancia debe estar VISIBLE (GONE o INVISIBLE = no reserva/no muestra).
            assertWithMessage("ninguna ms_preview_frame_layout debe estar VISIBLE en reposo (fix anti layout-shift)\n  encontradas: ${frames.map { it.second }}")
                .that(frames.none { it.second == "VISIBLE" }).isTrue()
            scenario.assertNoErrorFired()
        }
    }
}
