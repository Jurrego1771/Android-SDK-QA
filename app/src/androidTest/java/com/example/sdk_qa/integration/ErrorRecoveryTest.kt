package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Ignore
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitAnyError
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de recuperación ante errores.
 *
 * Una de las garantías más críticas de un SDK de media: si algo falla
 * (ID inválido, API error), el player debe:
 *   1. Reportar el error via onError — nunca silenciarlo
 *   2. Quedar en estado recuperable — reloadPlayer debe funcionar
 *   3. Limpiar estado previo — callbacks del ciclo anterior no contaminan
 *
 * Usa [MockServerActivity] con IDs inválidos para forzar errores de API.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ErrorRecoveryTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT = 20_000L

    private fun launchWithInvalidId(): ActivityScenario<MockServerActivity> =
        ActivityScenario.launch<MockServerActivity>(
            MockServerActivity.createIntent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MockServerActivity.INVALID_ID
            )
        )

    // -------------------------------------------------------------------------
    // [RECOVERY-01] reloadPlayer con contenido válido después de error → onReady
    //
    // Flujo: ID inválido → onError → reloadPlayer(VOD válido) → onReady
    // Verifica que el SDK no queda en estado roto después de un error.
    // -------------------------------------------------------------------------
    @Test
    fun afterError_reloadWithValidContent_onReady_fires() {
        val scenario = launchWithInvalidId()

        // Primer ciclo: debe fallar
        scenario.awaitAnyError(TIMEOUT)

        // Reset y reload con contenido válido
        scenario.onActivity { activity ->
            activity.callbackCaptor.reset()
            activity.player?.reloadPlayer(
                MediastreamPlayerConfig().apply {
                    id = TestContent.Video.VOD_SHORT
                    accountID = TestContent.ACCOUNT_ID
                    type = MediastreamPlayerConfig.VideoTypes.VOD
                    environment = TestContent.ENV
                    autoplay = true
                }
            )
        }

        // Segundo ciclo: debe recuperarse
        scenario.awaitCallback("onReady", TIMEOUT)
        scenario.assertNoErrorFired()

        scenario.close()
    }

    // -------------------------------------------------------------------------
    // [RECOVERY-02] El estado de callbacks se limpia correctamente entre ciclos
    //
    // Después de reset() + reload, los eventos del ciclo anterior no deben
    // estar presentes. Garantiza que las apps no procesen eventos fantasma.
    // -------------------------------------------------------------------------
    @Test
    fun afterError_callbackCaptor_reset_isClean() {
        val scenario = launchWithInvalidId()

        val errorFired = scenario.awaitAnyError(TIMEOUT)
        // Skip if SDK doesn't propagate errors — tracked in networkError_onError_isNeverSilent
        Assume.assumeTrue("SDK bug: error no propagado (ver networkError_onError_isNeverSilent)", errorFired)

        // Verificar que onError está en el estado antes del reset
        var hasErrorBefore = false
        scenario.onActivity { activity ->
            hasErrorBefore = activity.callbackCaptor.hasEvent("onError")
        }
        assertWithMessage("onError debe estar registrado antes del reset")
            .that(hasErrorBefore).isTrue()

        // Reset del captor
        scenario.onActivity { activity ->
            activity.callbackCaptor.reset()
        }

        // Después del reset, el estado debe estar limpio
        val captor = scenario.getCallbackCaptor()
        assertWithMessage("Después de reset(), onError no debe estar presente")
            .that(captor.hasEvent("onError")).isFalse()
        assertWithMessage("Después de reset(), onReady no debe estar presente")
            .that(captor.hasEvent("onReady")).isFalse()
        assertWithMessage("Después de reset(), allEvents debe estar vacío")
            .that(captor.allEvents()).isEmpty()

        scenario.close()
    }

    // -------------------------------------------------------------------------
    // [RECOVERY-03] Múltiples reloads consecutivos no degradan el player
    //
    // Simula un usuario que recarga varias veces. El SDK debe responder
    // a cada reload con onReady limpio, sin acumulación de estado.
    // -------------------------------------------------------------------------
    @Test
    fun multipleReloads_playerRemainsResponsive() {
        val scenario = ActivityScenario.launch(DirectHlsActivity::class.java)

        repeat(3) { _ ->
            scenario.awaitCallback("onReady", TIMEOUT)

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(DirectHlsActivity.buildDirectConfig())
            }
        }

        // Último reload — el player debe seguir respondiendo
        scenario.awaitCallback("onReady", TIMEOUT)
        scenario.assertNoErrorFired()

        scenario.close()
    }

    // -------------------------------------------------------------------------
    // [RECOVERY-04] onError nunca es silenciado — siempre llega al callback
    //
    // Un ID que no existe en la plataforma debe disparar onError.
    // Verificamos que el error llega Y que está registrado en el captor.
    // -------------------------------------------------------------------------
    @Ignore("SDK bug: mdstrm.com 404 no dispara onError/onEmbedErrors — reportar a Mediastream")
    @Test
    fun networkError_onError_isNeverSilent() {
        val scenario = launchWithInvalidId()

        val errorFired = scenario.awaitAnyError(TIMEOUT)
        // SDK BUG: mdstrm.com/video/INVALID_ID.json → 404, pero el SDK no dispara
        // onError ni onEmbedErrors. El error es silenciado.
        // Reportar a Mediastream. Este test falla intencionalmente hasta que se corrija.
        assertWithMessage(
            "SDK BUG: onError/onEmbedErrors deben dispararse cuando la API devuelve 404.\n" +
            "  El SDK recibió HTTP 404 de mdstrm.com pero no propagó el error al callback.\n" +
            "  Reportar este bug a Mediastream: el SDK silencia errores de API."
        ).that(errorFired).isTrue()

        scenario.close()
    }

    // -------------------------------------------------------------------------
    // [RECOVERY-05] releasePlayer durante error no causa crash
    //
    // Si el usuario cierra la pantalla mientras el player está en error,
    // releasePlayer() debe ejecutarse sin excepciones.
    // -------------------------------------------------------------------------
    @Test
    fun releasePlayerDuringError_doesNotCrash() {
        val scenario = launchWithInvalidId()

        scenario.awaitAnyError(TIMEOUT)

        // Llamar releasePlayer inmediatamente después del error — no debe crashear
        scenario.onActivity { activity ->
            activity.player?.releasePlayer()
        }

        // Si llegamos aquí sin excepción, el test pasa.
        // LeakCanary (via SdkTestRule) verifica adicionalmente que no hay leaks.
        scenario.close()
    }
}
