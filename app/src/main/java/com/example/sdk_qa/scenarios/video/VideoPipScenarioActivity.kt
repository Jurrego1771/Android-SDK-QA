package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig.FlagStatus
import android.os.Build
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * [09] PiP — Picture-in-Picture
 *
 * Permite comparar los tres modos de PiP disponibles en v11:
 *   Modo A: pip = ENABLE  (comportamiento clásico, pantalla completa → PiP)
 *   Modo B: pipReplaceActivityContentWithPlayer = true (small container → solo el video en PiP)
 *   Modo C: pipExpandToFullscreenFirst = true (fullscreen primero, luego PiP)
 *
 * Al presionar Home se intenta entrar en PiP automáticamente.
 */
class VideoPipScenarioActivity : BaseScenarioActivity() {

    private var currentPipMode = PipMode.CLASSIC

    enum class PipMode { CLASSIC, REPLACE_CONTENT, EXPAND_FIRST }

    override fun getScenarioTitle() = "PiP — ${currentPipMode.name}"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        pip = FlagStatus.ENABLE
        autoplay = true
        isDebug = true
        // Nota: pipReplaceActivityContentWithPlayer y pipExpandToFullscreenFirst
        // son propiedades de v11 — agregar aquí cuando estén disponibles en la clase
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Modo: Clásico") {
            currentPipMode = PipMode.CLASSIC
            player?.reloadPlayer(buildConfig())
        }
        addButton(container, "Modo: Replace") {
            currentPipMode = PipMode.REPLACE_CONTENT
            player?.reloadPlayer(buildConfig())
        }
        addButton(container, "Modo: ExpandFirst") {
            currentPipMode = PipMode.EXPAND_FIRST
            player?.reloadPlayer(buildConfig())
        }
        addButton(container, "Iniciar PiP") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                player?.startPiP()
            }
        }
        addButton(container, "pip=DISABLE") {
            val cfg = buildConfig().also { it.pip = FlagStatus.DISABLE }
            player?.reloadPlayer(cfg)
        }
    }
}
