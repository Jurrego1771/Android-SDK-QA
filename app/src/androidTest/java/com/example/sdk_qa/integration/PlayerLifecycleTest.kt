package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
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
 * Tests de contrato del SDK — verifican garantías que el SDK debe cumplir
 * con cualquier app que lo integre.
 *
 * Diferencia con los otros tests:
 *   - No testean features (VOD play, Live connect)
 *   - Testean el *contrato de integración*: threading, lifecycle, cleanup, error handling
 *
 * Usa [SdkTestRule] que activa LeakCanary + StrictMode en cada test.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class PlayerLifecycleTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT = 20_000L

    // -------------------------------------------------------------------------
    // [LIFECYCLE-01] releasePlayer() no leakea recursos
    //
    // LeakCanary (via SdkTestRule) inspecciona el heap después del test.
    // Si el player retiene la Activity, Context, Views o listeners → falla.
    // -------------------------------------------------------------------------
    @Test
    fun releasePlayer_doesNotLeakActivity() {
        val scenario = ActivityScenario.launch(DirectHlsActivity::class.java)

        scenario.awaitCallback("onReady", TIMEOUT)

        // Llamada explícita a releasePlayer antes de destruir la Activity.
        // El SDK debe limpiar: ExoPlayer, AudioFocus, WakeLock, listeners internos.
        scenario.onActivity { activity ->
            activity.player?.releasePlayer()
        }

        scenario.close()
        // DetectLeaksAfterTestSuccess (en SdkTestRule) valida el heap aquí.
        // Si el SDK tiene un leak, el test falla con un heap dump.
    }

    // -------------------------------------------------------------------------
    // [LIFECYCLE-02] Los callbacks del SDK llegan en el main thread
    //
    // Contrato crítico para cualquier integrador: si el SDK llama callbacks
    // desde un background thread, cualquier update de UI en el callback
    // lanza CalledFromWrongThreadException en la app del cliente.
    // -------------------------------------------------------------------------
    @Test
    fun sdkCallbacks_onReady_arrivesOnMainThread() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            val captor = scenario.getCallbackCaptor()
            assertWithMessage("onReady debe dispararse desde el main thread")
                .that(captor.firedOnMainThread("onReady"))
                .isTrue()
        }
    }

    @Test
    fun sdkCallbacks_onPlay_arrivesOnMainThread() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)

            val captor = scenario.getCallbackCaptor()
            assertWithMessage("onPlay debe dispararse desde el main thread")
                .that(captor.firedOnMainThread("onPlay"))
                .isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [LIFECYCLE-03] ID inexistente dispara onError
    //
    // Un content ID que no existe en la plataforma hace que la API devuelva
    // un error. El SDK debe detectarlo y disparar onError — no silenciarlo.
    // -------------------------------------------------------------------------
    @Test
    fun invalidContentId_triggersOnError() {
        val scenario = ActivityScenario.launch<MockServerActivity>(
            MockServerActivity.createIntent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MockServerActivity.INVALID_ID
            )
        )

        val errorFired = scenario.awaitAnyError(TIMEOUT)
        assertWithMessage("El SDK debe disparar onError/onEmbedErrors ante un content ID inexistente")
            .that(errorFired)
            .isTrue()

        scenario.close()
    }

    // -------------------------------------------------------------------------
    // [LIFECYCLE-05] reloadPlayer limpia estado anterior antes de nueva carga
    //
    // Después de reload, onReady debe disparar de nuevo.
    // Los callbacks del ciclo anterior no deben contaminar el nuevo ciclo.
    // -------------------------------------------------------------------------
    @Test
    fun reloadPlayer_previousCallbackState_isClean() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            // Reset captor y reload
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(DirectHlsActivity.buildDirectConfig())
            }

            // onReady debe volver a dispararse con estado limpio
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            // El reset fue efectivo: no debe haber eventos del ciclo anterior
            val captor = scenario.getCallbackCaptor()
            assertWithMessage("onEnd no debe estar presente en el nuevo ciclo")
                .that(captor.hasEvent("onEnd"))
                .isFalse()
        }
    }
}
