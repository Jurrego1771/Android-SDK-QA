package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

/**
 * Activity auxiliar para tests de error.
 *
 * Acepta un content ID via Intent extra. Pasar un ID inexistente
 * hace que la API de Mediastream devuelva 404 → SDK dispara onError.
 *
 * Para tests de recovery: pasar primero un ID inválido, luego llamar
 * reloadPlayer() con un config válido.
 */
class MockServerActivity : BaseScenarioActivity() {

    private var contentId: String = INVALID_ID

    override fun getScenarioTitle() = "Error Test"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = contentId
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        contentId = intent.getStringExtra(EXTRA_ID) ?: INVALID_ID
        super.onCreate(savedInstanceState)
    }

    companion object {
        private const val EXTRA_ID = "content_id"

        /** ID inexistente que hace que la API devuelva 404 → onError */
        const val INVALID_ID = "000000000000000000000000"

        fun createIntent(context: Context, contentId: String = INVALID_ID): Intent =
            Intent(context, MockServerActivity::class.java)
                .putExtra(EXTRA_ID, contentId)
    }
}
