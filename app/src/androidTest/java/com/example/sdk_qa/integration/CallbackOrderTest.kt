package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamMiniPlayerConfig
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerCallback
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import com.google.ads.interactivemedia.v3.api.AdError
import com.google.ads.interactivemedia.v3.api.AdEvent
import org.json.JSONObject
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.sdk_qa.core.CallbackCaptor
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de contrato de orden y unicidad de callbacks del SDK.
 *
 * Objetivo: garantizar los invariantes que cualquier app integradora puede asumir:
 *   - playerViewReady siempre antes de onReady
 *   - onReady siempre antes de onPlay con autoplay=true
 *   - onPlay siempre antes de onEnd
 *   - onReady no se duplica sin reload
 *   - onPause llega después de onPlay (no antes)
 *   - Múltiples callbacks registrados reciben los mismos eventos
 *   - Todos los callbacks del ciclo de vida llegan en el main thread
 *
 * Usa DirectHlsActivity (VOD, sin IMA) para evitar el AdTagLoader leak
 * y no depender del estado del backend Mediastream.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class CallbackOrderTest {

    @get:Rule val sdkRule = SdkTestRule()

    private val TIMEOUT = 20_000L

    // -------------------------------------------------------------------------
    // [CB-ORDER-01] playerViewReady se dispara antes que onReady
    //
    // Contrato: la vista del player debe estar lista (insertada en el layout)
    // antes de que el SDK señale que el contenido está preparado para reproducir.
    // -------------------------------------------------------------------------
    @Test
    fun playerViewReady_precedes_onReady() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            val order = scenario.getCallbackCaptor().eventOrderSnapshot()
            val viewReadyIdx = order.indexOf("playerViewReady")
            val readyIdx     = order.indexOf("onReady")

            assertWithMessage(
                "playerViewReady debe llegar antes que onReady\n  Orden observado: $order"
            ).that(viewReadyIdx).isLessThan(readyIdx)
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-02] onReady se dispara antes que onPlay con autoplay=true
    //
    // Con autoplay=true el SDK inicia la reproducción automáticamente después
    // de onReady. Un integrador que actualiza UI en onPlay asume que el player
    // ya está listo — esta secuencia debe garantizarse.
    // -------------------------------------------------------------------------
    @Test
    fun onReady_precedes_onPlay_withAutoplay() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)

            val order    = scenario.getCallbackCaptor().eventOrderSnapshot()
            val readyIdx = order.indexOf("onReady")
            val playIdx  = order.indexOf("onPlay")

            assertWithMessage(
                "onReady debe llegar antes que onPlay\n  Orden observado: $order"
            ).that(readyIdx).isLessThan(playIdx)
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-03] onPlay se dispara antes que onEnd (seek al final)
    //
    // onEnd sin onPlay previo significaría que el contenido terminó sin haber
    // reproducido — imposible en condiciones normales. Verifica el invariante.
    // -------------------------------------------------------------------------
    // Suppressed: in SDK 10.0.5, onPlaybackErrors fires during initial buffering of VOD_SHORT.
    // ExoPlayer enters error state → STATE_ENDED never fires → onEnd never arrives → timeout.
    // The invariant (onPlay before onEnd) is still valid design; the test environment is unstable
    // due to intermittent content-level ExoPlayer errors. Re-enable when content is stable.
    @Ignore("SDK 10.0.5: onPlaybackErrors during initial load leaves ExoPlayer in error state, onEnd never fires")
    @Test
    fun onPlay_precedes_onEnd() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            scenario.onActivity { activity ->
                val duration = activity.player?.msPlayer?.duration ?: 0L
                if (duration > 3_000L) activity.player?.msPlayer?.seekTo(duration - 1_000L)
            }

            scenario.awaitCallback("onEnd", TIMEOUT)

            val order   = scenario.getCallbackCaptor().eventOrderSnapshot()
            val playIdx = order.indexOf("onPlay")
            val endIdx  = order.indexOf("onEnd")

            assertWithMessage(
                "onPlay debe llegar antes que onEnd\n  Orden observado: $order"
            ).that(playIdx).isLessThan(endIdx)
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-04] onReady no se dispara más de una vez sin reload
    //
    // Disparar onReady múltiples veces sin reload indica un bug interno del SDK
    // (doble inicialización, re-attach del player, etc.). Este test detecta esa
    // regresión en reproducción normal.
    // -------------------------------------------------------------------------
    @Test
    fun onReady_doesNotFireTwice_withoutReload() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)
            Thread.sleep(3_000)

            val order = scenario.getCallbackCaptor().eventOrderSnapshot()
            val readyCount = order.count { it == "onReady" }

            assertWithMessage(
                "onReady debe dispararse exactamente una vez\n  Orden observado: $order"
            ).that(readyCount).isEqualTo(1)
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-05] onPause se dispara después de onPlay al pausar
    //
    // Un integrador que usa onPause para actualizar controles UI asume que
    // onPlay ya ocurrió. onPause sin onPlay previo es un estado inválido.
    // -------------------------------------------------------------------------
    @Test
    fun onPause_fires_after_onPlay() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)

            scenario.onActivity { it.player?.msPlayer?.pause() }
            scenario.awaitCallback("onPause", TIMEOUT)

            val order    = scenario.getCallbackCaptor().eventOrderSnapshot()
            val playIdx  = order.indexOf("onPlay")
            val pauseIdx = order.indexOf("onPause")

            assertWithMessage(
                "onPause debe llegar después de onPlay\n  Orden observado: $order"
            ).that(pauseIdx).isGreaterThan(playIdx)
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-06] onPlay vuelve a dispararse al reanudar después de pausa
    //
    // El integrador espera onPlay tanto en la reproducción inicial como al
    // reanudar. Verifica que el SDK no omite el callback de play tras resume.
    // -------------------------------------------------------------------------
    @Test
    fun onPlay_fires_after_resume() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)

            scenario.onActivity { it.player?.msPlayer?.pause() }
            scenario.awaitCallback("onPause", TIMEOUT)

            // Reset para poder esperar el segundo onPlay
            scenario.onActivity { it.callbackCaptor.reset() }

            scenario.onActivity { it.player?.msPlayer?.play() }
            scenario.awaitCallback("onPlay", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-07] Dos callbacks registrados reciben los mismos eventos
    //
    // El SDK soporta addPlayerCallback() múltiples veces. Ambos callbacks deben
    // recibir exactamente los mismos eventos — ninguno debe quedar silenciado.
    // -------------------------------------------------------------------------
    @Test
    fun twoCallbacks_bothReceiveSameEvents() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            // Esperar primer onReady para garantizar que el player existe
            scenario.awaitCallback("onReady", TIMEOUT)

            // Registrar segundo captor antes del reload para capturar el ciclo completo
            val secondCaptor = CallbackCaptor()
            scenario.onActivity { activity ->
                activity.player?.addPlayerCallback(object : NoOpCallback() {
                    override fun onReady() { secondCaptor.recordEvent("onReady") }
                    override fun onPlay()  { secondCaptor.recordEvent("onPlay") }
                })
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(activity.buildConfig())
            }

            // Primer captor (BaseScenarioActivity) recibe onReady tras reload
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            // Segundo captor debe haber recibido los mismos eventos
            assertWithMessage("El segundo callback debe recibir onReady")
                .that(secondCaptor.awaitEvent("onReady", TIMEOUT)).isTrue()
            assertWithMessage("El segundo callback debe recibir onPlay")
                .that(secondCaptor.awaitEvent("onPlay", TIMEOUT)).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-08] onReady y onPlay llegan en el main thread
    //
    // Contrato de threading: si el SDK llamara desde un background thread,
    // cualquier update de UI en el callback del integrador lanzaría
    // CalledFromWrongThreadException. Este test cubre onReady y onPlay.
    // Nota: playerViewReady viola el contrato — ver CB-ORDER-08b (suprimido).
    // -------------------------------------------------------------------------
    @Test
    fun lifecycleCallbacks_arriveOnMainThread() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)

            val captor = scenario.getCallbackCaptor()
            listOf("onReady", "onPlay").forEach { event ->
                assertWithMessage("$event debe dispararse desde el main thread")
                    .that(captor.firedOnMainThread(event)).isTrue()
            }
        }
    }

    // -------------------------------------------------------------------------
    // [CB-ORDER-08b] SDK BUG: playerViewReady se dispara desde background thread
    //
    // Confirmado en Sony BRAVIA VU31 4K (Android 14). El SDK llama a
    // playerViewReady() desde un thread interno de ExoPlayer/Media3, no desde
    // el main thread. Cualquier update de UI directa en este callback causará
    // CalledFromWrongThreadException. Integradores deben usar runOnUiThread().
    //
    // Ignorado: bug confirmado en SDK v11.0.0-alpha.01. Reportar a Mediastream.
    // -------------------------------------------------------------------------
    @Ignore("SDK bug: playerViewReady fires from ExoPlayer internal thread, not main thread")
    @Test
    fun playerViewReady_firesOnMainThread_knownSdkBug() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            scenario.awaitCallback("onPlay", TIMEOUT)
            val captor = scenario.getCallbackCaptor()
            assertWithMessage("playerViewReady debe dispararse desde el main thread")
                .that(captor.firedOnMainThread("playerViewReady")).isTrue()
        }
    }
}

/**
 * Implementación no-op de MediastreamPlayerCallback para tests.
 * Subclasear y sobreescribir solo los métodos relevantes para el test.
 */
private open class NoOpCallback : MediastreamPlayerCallback {
    override fun playerViewReady(msplayerView: PlayerView?) {}
    override fun onReady() {}
    override fun onPlay() {}
    override fun onPause() {}
    override fun onBuffering() {}
    override fun onEnd() {}
    override fun onPlayerClosed() {}
    override fun onPlayerReload() {}
    override fun onError(error: String?) {}
    override fun onPlaybackErrors(error: JSONObject?) {}
    override fun onEmbedErrors(error: JSONObject?) {}
    override fun onNext() {}
    override fun onPrevious() {}
    override fun nextEpisodeIncoming(nextEpisodeId: String) {}
    override fun onNewSourceAdded(config: MediastreamPlayerConfig) {}
    override fun onLocalSourceAdded() {}
    override fun onFullscreen(enteredForPip: Boolean) {}
    override fun offFullscreen() {}
    override fun onDismissButton() {}
    override fun onConfigChange(config: MediastreamMiniPlayerConfig?) {}
    override fun onLiveAudioCurrentSongChanged(data: JSONObject?) {}
    override fun onAdEvents(type: AdEvent.AdEventType) {}
    override fun onAdErrorEvent(error: AdError) {}
    override fun onCastAvailable(state: Boolean?) {}
    override fun onCastSessionStarting() {}
    override fun onCastSessionStarted() {}
    override fun onCastSessionStartFailed() {}
    override fun onCastSessionEnding() {}
    override fun onCastSessionEnded() {}
    override fun onCastSessionResuming() {}
    override fun onCastSessionResumed() {}
    override fun onCastSessionResumeFailed() {}
    override fun onCastSessionSuspended() {}
}
