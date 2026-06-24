package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * Activities auxiliares para AnalyticsTest.
 *
 * Cada clase cubre una variante de configuración de profileId/trackEnable.
 * Solo presentes en debug builds — no en release.
 *
 * NOTA (2026-06-24): estas Activities usan config por `id`+`accountID` (no `src`-directo). El propósito
 * del test es verificar que profileID/trackEnable/isDebug no rompen la reproducción — algo ortogonal a
 * la fuente. El path `src`-directo NO emite callbacks en 10.0.8-alpha08 (ExoPlayer crea+libera con
 * `releasePrerollPlayerForSsai`, 0 callbacks; verificado por MCP) → posible bug del SDK reportado aparte.
 * Usar `id` (como DirectHlsActivity, que sí reproduce) aísla el test del riesgo del path de ads/SSAI.
 */

// ─────────────────────────────────────────────────────────────────────────────
// INT-ANL-01: VOD por id+accountID + profileId configurado + trackEnable=true
// ─────────────────────────────────────────────────────────────────────────────
class AnalyticsWithProfileActivity : BaseScenarioActivity() {
    override fun getScenarioTitle() = "Analytics — profileId configurado"
    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        trackEnable = true
        profileID = "test-profile-android"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INT-ANL-03: VOD por id+accountID + isDebug=true + profileId NO configurado
// El SDK debe auto-asignar profileId="debug-profile-id" en init()
// ─────────────────────────────────────────────────────────────────────────────
class AnalyticsDebugFallbackActivity : BaseScenarioActivity() {
    override fun getScenarioTitle() = "Analytics — debug fallback profileId"
    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = true
        trackEnable = true
        // profileId intencionalmente no configurado — SDK debe asignar "debug-profile-id"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INT-ANL-04: VOD por id+accountID + trackEnable=false + profileId configurado
// ─────────────────────────────────────────────────────────────────────────────
class AnalyticsDisabledActivity : BaseScenarioActivity() {
    override fun getScenarioTitle() = "Analytics — trackEnable deshabilitado"
    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        trackEnable = false
        profileID = "test-profile-android"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INT-ANL-05: VOD con id real + accountID + profileId + trackEnable=true
// Escenario de producción completo
// ─────────────────────────────────────────────────────────────────────────────
class AnalyticsVodApiActivity : BaseScenarioActivity() {
    override fun getScenarioTitle() = "Analytics — VOD via API con profileId"
    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_SHORT
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = MediastreamPlayerConfig.Environment.PRODUCTION
        autoplay = true
        trackEnable = true
        profileID = "test-profile-android"
    }
}
