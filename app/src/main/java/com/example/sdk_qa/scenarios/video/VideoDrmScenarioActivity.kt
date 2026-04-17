package com.example.sdk_qa.scenarios.video

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.widget.LinearLayout
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.LogCategory
import com.example.sdk_qa.core.TestContent

/**
 * [07] Live con DRM Widevine — entorno DEV
 *
 * NOTA v11: MediastreamPlayerConfig.DrmData no existe en esta versión.
 * El SDK resuelve la licencia automáticamente vía la plataforma al cargar el contenido por ID.
 * Axinom License URL: https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense
 *
 * TODO: cuando el SDK exponga la API de DrmData, agregar modos Manual y Error.
 */
class VideoDrmScenarioActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Video DRM Widevine (DEV)"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Drm.LIVE_ID
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.LIVE
        environment = TestContent.Drm.ENV  // DEV — sobreescribe ENV global
        autoplay = true
        isDebug = true
    }

    override fun setupActionButtons(container: LinearLayout) {
        addButton(container, "Recargar") {
            log("DRM auto — SDK resuelve licencia vía plataforma", category = LogCategory.SYSTEM)
            player?.reloadPlayer(buildConfig())
        }
        addButton(container, "Sin DRM (error)") {
            // Carga el mismo stream en PRODUCTION donde no tiene licencia → debe dar onError
            log("Forzando error DRM — env incorrecto", category = LogCategory.ERROR)
            val cfg = buildConfig().apply {
                environment = MediastreamPlayerConfig.Environment.PRODUCTION
            }
            player?.reloadPlayer(cfg)
        }
    }
}
