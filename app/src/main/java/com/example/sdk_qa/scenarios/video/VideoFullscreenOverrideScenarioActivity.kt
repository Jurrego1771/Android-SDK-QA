package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Escenario para testear los overrides de los botones de fullscreen (feat/allow-override-rotation).
 *
 * Parámetros via intent extras:
 *   EXTRA_ENABLE_ON_OVERRIDE  (Boolean, default false) — instala onFullscreenOnClick
 *   EXTRA_ENABLE_OFF_OVERRIDE (Boolean, default false) — instala onFullscreenOffClick
 *
 * Contadores expuestos para assertions en tests:
 *   onFullscreenOnCallCount  — veces que el override de "enter fullscreen" fue invocado
 *   onFullscreenOffCallCount — veces que el override de "exit fullscreen" fue invocado
 *
 * Usa SRC_DIRECT_HLS (sin API call) para que onReady llegue en ~3s.
 */
class VideoFullscreenOverrideScenarioActivity : BaseScenarioActivity() {

    companion object {
        const val EXTRA_ENABLE_ON_OVERRIDE  = "enable_on_override"
        const val EXTRA_ENABLE_OFF_OVERRIDE = "enable_off_override"

        fun intentWithOverrides(
            context: Context,
            enableOn: Boolean = false,
            enableOff: Boolean = false
        ): Intent = Intent(context, VideoFullscreenOverrideScenarioActivity::class.java).apply {
            putExtra(EXTRA_ENABLE_ON_OVERRIDE,  enableOn)
            putExtra(EXTRA_ENABLE_OFF_OVERRIDE, enableOff)
        }
    }

    val onFullscreenOnCallCount  = AtomicInteger(0)
    val onFullscreenOffCallCount = AtomicInteger(0)

    override fun getScenarioTitle() = "Fullscreen Override"

    override fun buildConfig(): MediastreamPlayerConfig {
        val enableOn  = intent.getBooleanExtra(EXTRA_ENABLE_ON_OVERRIDE,  false)
        val enableOff = intent.getBooleanExtra(EXTRA_ENABLE_OFF_OVERRIDE, false)

        return MediastreamPlayerConfig().apply {
            id        = TestContent.Video.VOD_SHORT
            accountID = TestContent.ACCOUNT_ID
            type      = MediastreamPlayerConfig.VideoTypes.VOD
            environment = TestContent.ENV
            autoplay  = true
            isDebug   = true

            if (enableOn) {
                onFullscreenOnClick = java.util.function.Consumer { _ ->
                    onFullscreenOnCallCount.incrementAndGet()
                }
            }
            if (enableOff) {
                onFullscreenOffClick = java.util.function.Consumer { _ ->
                    onFullscreenOffCallCount.incrementAndGet()
                }
            }
        }
    }

    /**
     * Simula un click programático en el botón "enter fullscreen" del SDK.
     * Debe llamarse desde el main thread (via scenario.onActivity).
     * Funciona independientemente de si los controles están visibles en pantalla.
     */
    fun simulateFullscreenOnClick() = clickSdkButton("btn_fullscreen_on")

    /**
     * Simula un click programático en el botón "exit fullscreen" del SDK.
     * Debe llamarse desde el main thread (via scenario.onActivity).
     * Funciona independientemente de si los controles están visibles en pantalla.
     */
    fun simulateFullscreenOffClick() = clickSdkButton("btn_fullscreen_off")

    private fun clickSdkButton(name: String) {
        val sdkPkg = "am.mediastre.mediastreamplatformsdkandroid"
        val resId = resources.getIdentifier(name, "id", sdkPkg)
        check(resId != 0) { "Resource $sdkPkg:id/$name not found — SDK version may not support this" }
        val btn: View? = binding.playerView.findViewById(resId)
        checkNotNull(btn) { "Button $name not in PlayerView hierarchy — Customizer may not have run yet" }
        btn.performClick()
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "FS On Count") {
            log("onFullscreenOnClick count=${onFullscreenOnCallCount.get()}")
        }
        addButton(container, "FS Off Count") {
            log("onFullscreenOffClick count=${onFullscreenOffCallCount.get()}")
        }
    }
}
