package com.example.sdk_qa.integration

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sdk_qa.scenarios.audio.AudioVodScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de manejo de AudioFocus.
 *
 * AudioFocus es el mecanismo de Android para coordinar reproducción de audio
 * entre apps. Un SDK de media que lo ignora es un ciudadano de segunda:
 *   - El usuario recibe una llamada → el SDK debe pausar o bajar el volumen
 *   - Suena una notificación → el SDK debe reducir brevemente el volumen
 *   - El usuario cambia de app de música → el SDK debe ceder el foco
 *
 * Mecanismo del test:
 *   El test solicita AudioFocus como si fuera "otra app". Desde la perspectiva
 *   del SDK, es indistinguible de una app externa tomando el foco. Android
 *   envía AUDIOFOCUS_LOSS al listener del SDK, que debe reaccionar.
 *
 * Usa AudioVodScenarioActivity (audio VOD) — el caso de uso más relevante
 * para audio focus ya que el usuario espera que la música/podcast se pause.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class AudioFocusTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private lateinit var audioManager: AudioManager

    // Listener del test — simula "otra app" que quiere el audio focus
    private val testFocusListener = AudioManager.OnAudioFocusChangeListener { /* no-op */ }

    private val TIMEOUT = 15_000L
    private val REACT_DELAY = 800L // tiempo para que el SDK reaccione al cambio de foco

    @Before
    fun setup() {
        audioManager = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    @After
    fun releaseAudioFocus() {
        // Siempre liberar el foco al terminar para no contaminar otros tests
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(testFocusListener)
    }

    // -------------------------------------------------------------------------
    // [AUDIOFOCUS-01] Pérdida permanente de foco — el SDK debe pausar
    //
    // AUDIOFOCUS_GAIN desde otra fuente envía AUDIOFOCUS_LOSS al SDK.
    // Caso real: el usuario abre Spotify o recibe una llamada larga.
    // El SDK debe pausar la reproducción.
    // -------------------------------------------------------------------------
    @Test
    fun permanentFocusLoss_playerPauses() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            // Verificar que está reproduciendo antes del test
            var wasPlaying = false
            scenario.onActivity { activity ->
                wasPlaying = activity.player?.msPlayer?.isPlaying ?: false
            }
            assertWithMessage("El player debe estar reproduciendo antes del test")
                .that(wasPlaying).isTrue()

            // "Otra app" solicita foco permanente — el SDK debe recibir AUDIOFOCUS_LOSS
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                testFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )

            Thread.sleep(REACT_DELAY)

            // El SDK debe haber pausado o estar en proceso de pausar
            var isPlayingAfterLoss = true
            scenario.onActivity { activity ->
                isPlayingAfterLoss = activity.player?.msPlayer?.isPlaying ?: true
            }

            assertWithMessage(
                "El SDK debe pausar la reproducción ante pérdida permanente de AudioFocus"
            ).that(isPlayingAfterLoss).isFalse()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIOFOCUS-02] Pérdida transitoria de foco — el SDK debe pausar
    //
    // AUDIOFOCUS_GAIN_TRANSIENT envía AUDIOFOCUS_LOSS_TRANSIENT al SDK.
    // Caso real: una notificación de audio, alarma corta, asistente de voz.
    // El SDK debe pausar temporalmente.
    // -------------------------------------------------------------------------
    @Test
    fun transientFocusLoss_playerPauses() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            // Solicitud transitoria — SDK recibe AUDIOFOCUS_LOSS_TRANSIENT
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                testFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )

            Thread.sleep(REACT_DELAY)

            var isPlayingAfterLoss = true
            scenario.onActivity { activity ->
                isPlayingAfterLoss = activity.player?.msPlayer?.isPlaying ?: true
            }

            assertWithMessage(
                "El SDK debe pausar ante pérdida transitoria de AudioFocus"
            ).that(isPlayingAfterLoss).isFalse()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIOFOCUS-03] Recuperación de foco — el SDK puede reanudar
    //
    // Después de perder el foco transitoriamente y recuperarlo,
    // el SDK debe volver a reproducir (o al menos estar en estado reproducible).
    //
    // NOTA: el comportamiento exacto (auto-resume vs requiere play manual)
    // puede variar según la implementación del SDK. Este test verifica
    // que el player NO queda en estado roto — no que auto-reanude.
    // -------------------------------------------------------------------------
    @Test
    fun transientFocusLoss_thenGain_playerIsConsistent() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            // Perder foco transitoriamente
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                testFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            Thread.sleep(REACT_DELAY)

            // Devolver el foco al SDK — el sistema envía AUDIOFOCUS_GAIN
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(testFocusListener)
            Thread.sleep(REACT_DELAY)

            // El player debe estar en un estado válido y responder a play()
            var playbackState = -1
            var commandSucceeded = false
            scenario.onActivity { activity ->
                playbackState = activity.player?.msPlayer?.playbackState ?: -1
                try {
                    activity.player?.msPlayer?.play()
                    commandSucceeded = true
                } catch (e: Exception) {
                    commandSucceeded = false
                }
            }

            assertWithMessage("El playbackState debe ser válido después de recuperar el foco")
                .that(playbackState).isAnyOf(
                    androidx.media3.common.Player.STATE_BUFFERING,
                    androidx.media3.common.Player.STATE_READY
                )

            assertWithMessage("El player debe aceptar play() después de recuperar el foco")
                .that(commandSucceeded).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIOFOCUS-04] Pérdida de foco no causa crash
    //
    // El SDK nunca debe crashear ante cambios de foco, independientemente
    // del estado en que esté (buffering, paused, ended, etc.).
    // -------------------------------------------------------------------------
    @Test
    fun focusLoss_neverCrashes() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Perder y recuperar foco varias veces — ninguna debe crashear
            repeat(3) {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    testFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                Thread.sleep(300)

                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(testFocusListener)
                Thread.sleep(300)
            }

            // Si llegamos aquí sin excepción, el test pasa
            // Verificación adicional: el player sigue existiendo
            var playerAlive = false
            scenario.onActivity { activity ->
                playerAlive = activity.player != null
            }

            assertWithMessage("El player debe seguir vivo después de múltiples cambios de foco")
                .that(playerAlive).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [AUDIOFOCUS-05] Pérdida de foco durante buffering — sin estado roto
    //
    // Si el foco se pierde mientras el player está cargando (buffering),
    // el SDK debe manejar el caso edge sin corromper el estado.
    // -------------------------------------------------------------------------
    @Test
    fun focusLoss_duringBuffering_playerRemainsConsistent() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            // Perder foco inmediatamente después de lanzar (durante la carga inicial)
            // El SDK puede estar en STATE_BUFFERING en este punto
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                testFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )

            // Devolver el foco para que el SDK pueda continuar
            Thread.sleep(500)
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(testFocusListener)

            // El SDK debe recuperarse y llegar a onReady de todas formas
            // (con timeout más generoso ya que hubo interrupción durante carga)
            val recovered = scenario.getCallbackCaptor().awaitEvent("onReady", TIMEOUT * 2)

            assertWithMessage(
                "El SDK debe recuperarse y llegar a onReady después de focus loss durante buffering"
            ).that(recovered).isTrue()
        }
    }
}
