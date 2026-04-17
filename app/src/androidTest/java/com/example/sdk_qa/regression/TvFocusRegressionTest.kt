package com.example.sdk_qa.regression

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.sdk_qa.annotation.TvOnly
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión: Bug de pérdida de foco durante seek con D-pad en Android TV.
 *
 * COMPORTAMIENTO INCORRECTO (bug):
 *   Presionar DPAD_RIGHT/LEFT repetidamente en la barra de progreso → el foco
 *   salta a otro elemento (ej. Play/Pause). Los presses siguientes no generan
 *   seek sino que accionan el elemento que capturó el foco.
 *
 * COMPORTAMIENTO ESPERADO:
 *   DPAD_RIGHT/LEFT sobre la timebar N veces → posición avanza/retrocede N pasos.
 *   El foco permanece en la barra de progreso durante toda la secuencia.
 *   El player sigue en PLAYING al terminar.
 *
 * Layout TV del SDK (layout-television/exo_player_control_view.xml):
 *
 *   ┌─────────────────────────────────────────────────┐
 *   │  [pos]  ◀━━━━━━━━━ DvrTimeBar ━━━━━━━━━▶  [dur] │  ← fila superior (seek)
 *   │  [▶] [⏮] [⏪10s] [⏩10s] [⏭]    [CC] [⚙]      │  ← fila inferior (botones)
 *   └─────────────────────────────────────────────────┘
 *
 *   Todos los botones tienen android:nextFocusUp="@id/exo_progress" →
 *   DPAD_UP desde cualquier botón lleva a la DvrTimeBar.
 *
 * Flujo de seek real en TV:
 *   1. DPAD_CENTER → muestra controles (foco en primer botón)
 *   2. DPAD_UP     → foco sube a la barra de progreso
 *   3. DPAD_RIGHT/LEFT → scrub; DefaultTimeBar llama seekTo() en cada keypress
 *
 * Nota: SDK oculta ⏪10s y ⏩10s para TV+VOD (MediastreamPlayerCustomizer líneas 333-336).
 * El seek vía timebar es el mecanismo disponible. TV-FOCUS-04 usa media keys como baseline.
 *
 * DefaultTimeBar avanza duration/20 por keypress (Media3 default).
 * Tolerancia de assert: (PRESS_COUNT - 2) pasos para absorber latencia de foco.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@TvOnly
class TvFocusRegressionTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val TIMEOUT         = 20_000L
    private val PRESS_INTERVAL  = 400L
    private val SETTLE_AFTER_MS = 1_500L
    private val TIMEBAR_STEPS   = 20  // DefaultTimeBar: keyStep = duration / 20

    // -------------------------------------------------------------------------
    // [TV-FOCUS-01] DPAD_RIGHT repetido en la timebar avanza la posición
    //
    // Navega a la barra de progreso con DPAD_UP y presiona DPAD_RIGHT 5 veces.
    // Si el foco sale de la timebar durante los presses, la posición no avanza.
    // El player no debe pausarse (lo que indicaría que el foco fue al Play/Pause).
    // -------------------------------------------------------------------------
    @Test
    fun repeatForwardPress_positionAdvancesCorrectly() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            var duration = 0L
            scenario.onActivity { activity ->
                duration = activity.player?.msPlayer?.duration ?: 0L
            }

            // Mostrar controles y navegar a la barra de progreso
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(500)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
            Thread.sleep(200)

            var positionBefore = 0L
            scenario.onActivity { activity ->
                positionBefore = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            val PRESS_COUNT = 5

            repeat(PRESS_COUNT) {
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
                Thread.sleep(PRESS_INTERVAL)
            }

            // Resetear captor y salir de la timebar: DPAD_DOWN dispara onScrubStop →
            // StyledPlayerControlView llama player.play() → SDK emite "onPlay".
            scenario.onActivity { it.callbackCaptor.reset() }
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.awaitCallback("onPlay", TIMEOUT)

            var positionAfter = 0L
            var isPlaying     = false
            scenario.onActivity { activity ->
                positionAfter = activity.player?.msPlayer?.currentPosition ?: 0L
                isPlaying     = activity.player?.msPlayer?.isPlaying ?: false
            }

            val keyStep           = if (duration > 0) duration / TIMEBAR_STEPS else 10_000L
            val expectedMinAdvance = (PRESS_COUNT - 2).toLong() * keyStep

            assertWithMessage(
                "BUG DE FOCO: $PRESS_COUNT presses DPAD_RIGHT avanzaron solo ${(positionAfter - positionBefore) / 1000}s.\n" +
                "  Avance mínimo esperado: ${expectedMinAdvance / 1000}s (${PRESS_COUNT - 2} × keyStep=${keyStep / 1000}s).\n" +
                "  Si avanzó poco, el foco salió de la timebar durante los presses."
            ).that(positionAfter - positionBefore).isGreaterThan(expectedMinAdvance)

            assertWithMessage(
                "BUG DE FOCO: el player está PAUSADO tras DPAD_DOWN de la timebar. " +
                "StyledPlayerControlView no reanudó el playback en onScrubStop."
            ).that(isPlaying).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [TV-FOCUS-02] DPAD_LEFT repetido en la timebar retrocede la posición
    //
    // Avanza primero a 60s para tener margen de retroceso, luego navega a la
    // timebar con DPAD_UP y presiona DPAD_LEFT 5 veces.
    // -------------------------------------------------------------------------
    @Test
    fun repeatBackwardPress_positionRewindsCorrectly() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            var duration = 0L
            scenario.onActivity { activity ->
                duration = activity.player?.msPlayer?.duration ?: 0L
                activity.player?.msPlayer?.seekTo(60_000L)
                activity.callbackCaptor.reset()
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            // Mostrar controles y navegar a la barra de progreso
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(500)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
            Thread.sleep(200)

            var positionBefore = 0L
            scenario.onActivity { activity ->
                positionBefore = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            val PRESS_COUNT = 5

            repeat(PRESS_COUNT) {
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
                Thread.sleep(PRESS_INTERVAL)
            }

            scenario.onActivity { it.callbackCaptor.reset() }
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.awaitCallback("onPlay", TIMEOUT)

            var positionAfter = 0L
            var isPlaying     = false
            scenario.onActivity { activity ->
                positionAfter = activity.player?.msPlayer?.currentPosition ?: 0L
                isPlaying     = activity.player?.msPlayer?.isPlaying ?: false
            }

            val keyStep          = if (duration > 0) duration / TIMEBAR_STEPS else 10_000L
            val expectedMaxPos   = positionBefore - (PRESS_COUNT - 2) * keyStep

            assertWithMessage(
                "BUG DE FOCO: $PRESS_COUNT presses DPAD_LEFT no retrocedieron lo esperado.\n" +
                "  Posición antes : ${positionBefore / 1000}s\n" +
                "  Posición después: ${positionAfter / 1000}s\n" +
                "  Si retrocedió poco, el foco salió de la timebar durante los presses."
            ).that(positionAfter).isLessThan(expectedMaxPos + keyStep)

            assertWithMessage(
                "BUG DE FOCO: el player está PAUSADO tras DPAD_DOWN de la timebar. " +
                "StyledPlayerControlView no reanudó el playback en onScrubStop."
            ).that(isPlaying).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [TV-FOCUS-03] Alternancia rápida DPAD_RIGHT/LEFT en la timebar
    //
    // 4 ciclos: 3 presses hacia adelante → 3 presses hacia atrás.
    // Simula un usuario ajustando su posición. El player no debe pausarse.
    // -------------------------------------------------------------------------
    @Test
    fun alternatingSkipButtons_playerNeverPauses() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(120_000L)
                activity.callbackCaptor.reset()
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            // Mostrar controles y navegar a la barra de progreso
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(500)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
            Thread.sleep(200)

            // 4 ciclos: avanzar 3 pasos → retroceder 3 pasos
            repeat(4) { cycle ->
                val direction = if (cycle % 2 == 0) KeyEvent.KEYCODE_DPAD_RIGHT
                                else                 KeyEvent.KEYCODE_DPAD_LEFT
                repeat(3) {
                    device.pressKeyCode(direction)
                    Thread.sleep(PRESS_INTERVAL)
                }
            }

            scenario.onActivity { it.callbackCaptor.reset() }
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.awaitCallback("onPlay", TIMEOUT)

            var isPlaying = false
            scenario.onActivity { activity ->
                isPlaying = activity.player?.msPlayer?.isPlaying ?: false
            }

            assertWithMessage(
                "BUG DE FOCO: el player está PAUSADO tras alternancia DPAD_RIGHT/LEFT en la timebar.\n" +
                "  El foco fue al Play/Pause en algún punto del ciclo y lo toggleó."
            ).that(isPlaying).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [TV-FOCUS-04] Media keys como baseline (sin navegación UI)
    //
    // KEYCODE_MEDIA_FAST_FORWARD bypasea la timebar y va directo al SDK.
    // Si este test pasa y TV-FOCUS-01/02/03 fallan → el bug está en el
    // manejo de foco de la timebar, no en el procesamiento de seek.
    // -------------------------------------------------------------------------
    @Test
    fun mediaKeyFastForward_positionAdvancesCorrectly() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            var positionBefore = 0L
            scenario.onActivity { activity ->
                positionBefore = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            val PRESS_COUNT = 5

            repeat(PRESS_COUNT) {
                device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                Thread.sleep(PRESS_INTERVAL)
            }

            Thread.sleep(SETTLE_AFTER_MS)

            var positionAfter = 0L
            var isPlaying     = false
            scenario.onActivity { activity ->
                positionAfter = activity.player?.msPlayer?.currentPosition ?: 0L
                isPlaying     = activity.player?.msPlayer?.isPlaying ?: false
            }

            val expectedMinAdvance = (PRESS_COUNT - 1).toLong() * 10_000L

            assertWithMessage(
                "KEYCODE_MEDIA_FAST_FORWARD no generó avance suficiente.\n" +
                "  Si TV-FOCUS-01/02/03 fallan y este pasa → bug en foco de timebar.\n" +
                "  Si todos fallan → bug en procesamiento de seek en TV."
            ).that(positionAfter - positionBefore).isGreaterThan(expectedMinAdvance)

            assertWithMessage("El player no debe pausarse con media fast forward")
                .that(isPlaying).isTrue()
        }
    }
}
