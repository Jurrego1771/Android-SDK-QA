package com.example.sdk_qa.regression

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.sdk_qa.annotation.TvOnly
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión: Bug de pérdida de foco en botones de salto (Android TV / Fire TV).
 *
 * COMPORTAMIENTO INCORRECTO (bug):
 *   Presionar Avance o Retroceso repetidamente → el foco se mueve al botón Play/Pause.
 *   Los presses siguientes del botón de salto no generan seek — en cambio
 *   togglean play/pause o no hacen nada.
 *
 * COMPORTAMIENTO ESPERADO:
 *   Presionar Avance N veces → posición avanza ~N × skipAmount segundos.
 *   El foco debe permanecer en el botón de salto.
 *   El player debe seguir en estado PLAYING después de todos los presses.
 *
 * Estrategia de test — approach comportamental:
 *   En lugar de verificar el foco directamente (frágil, depende de IDs internos
 *   de Media3), verificamos las CONSECUENCIAS del bug:
 *
 *   Si el foco saltó al Play/Pause:
 *     a) La posición NO avanzará en los presses siguientes (fallo medible)
 *     b) Si el foco está en Play y se presiona DPAD_CENTER → el player pausa (fallo medible)
 *
 *   Si el foco se mantuvo en el botón de salto:
 *     a) La posición avanza correctamente con cada press
 *     b) El player sigue en PLAYING
 *
 * Requisito de hardware: dispositivo con D-PAD (Android TV, Fire TV, emulador TV).
 * Marcar tests con @TvOnly para excluirlos en mobile via run-tests.sh --target mobile.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@TvOnly
class TvFocusRegressionTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val TIMEOUT          = 20_000L
    private val PRESS_INTERVAL   = 400L   // ms entre presses (simula usuario rápido)
    private val SETTLE_AFTER_MS  = 1_500L // esperar que ExoPlayer procese todos los seeks
    private val SKIP_AMOUNT_MS   = 10_000L // Media3 default forward skip = 10s

    // -------------------------------------------------------------------------
    // [TV-FOCUS-01] Presses repetidos de Avance no pierden el foco
    //
    // Presionar DPAD_CENTER 5 veces sobre el botón de avance.
    // La posición final debe reflejar ~5 skips.
    // El player NO debe haberse pausado (lo que indicaría que el foco fue al Play).
    // -------------------------------------------------------------------------
    @Test
    fun repeatForwardPress_positionAdvancesCorrectly() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            // Mostrar controles del player presionando DPAD_CENTER sobre el video
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(500)

            // Navegar al botón de avance (DPAD_RIGHT desde el botón central Play)
            // La disposición de Media3 StyledPlayerControlView es:
            // [rew] [prev] [play/pause] [next] [ffwd]
            // Necesitamos 2 presses a la derecha para llegar a ffwd
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            Thread.sleep(200)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            Thread.sleep(200)

            // Capturar posición antes de los presses
            var positionBefore = 0L
            scenario.onActivity { activity ->
                positionBefore = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            val PRESS_COUNT = 5

            // Presionar ffwd 5 veces rápidamente
            repeat(PRESS_COUNT) {
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                Thread.sleep(PRESS_INTERVAL)
            }

            // Esperar que ExoPlayer procese todos los seeks acumulados
            Thread.sleep(SETTLE_AFTER_MS)

            var positionAfter = 0L
            var isPlaying     = false
            scenario.onActivity { activity ->
                positionAfter = activity.player?.msPlayer?.currentPosition ?: 0L
                isPlaying     = activity.player?.msPlayer?.isPlaying ?: false
            }

            val expectedMinAdvance = (PRESS_COUNT - 1).toLong() * SKIP_AMOUNT_MS
            val actualAdvance      = positionAfter - positionBefore

            // ── Assertion 1: posición avanzó lo suficiente ──────────────────
            assertWithMessage(
                "BUG DE FOCO: $PRESS_COUNT presses de avance avanzaron solo ${actualAdvance / 1000}s.\n" +
                "  Se esperaba avance mínimo de ${expectedMinAdvance / 1000}s.\n" +
                "  Si avanzó poco, el foco saltó al Play/Pause y los presses no generaron seek."
            ).that(actualAdvance).isGreaterThan(expectedMinAdvance)

            // ── Assertion 2: player sigue en PLAYING ────────────────────────
            assertWithMessage(
                "BUG DE FOCO: el player está PAUSADO después de los presses.\n" +
                "  El foco saltó al botón Play/Pause y DPAD_CENTER lo toggleó a pause."
            ).that(isPlaying).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [TV-FOCUS-02] Presses repetidos de Retroceso no pierden el foco
    //
    // Similar al test anterior pero con el botón de retroceso (rew).
    // -------------------------------------------------------------------------
    @Test
    fun repeatBackwardPress_positionRewindsCorrectly() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            // Primero avanzar para tener margen hacia atrás
            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(60_000L)
                activity.callbackCaptor.reset()
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            // Mostrar controles
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(500)

            // Navegar al botón de retroceso (DPAD_LEFT desde Play)
            // Disposición: [rew] [prev] [play/pause] [next] [ffwd]
            // 2 presses a la izquierda para llegar a rew
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
            Thread.sleep(200)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
            Thread.sleep(200)

            var positionBefore = 0L
            scenario.onActivity { activity ->
                positionBefore = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            val PRESS_COUNT = 5

            repeat(PRESS_COUNT) {
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                Thread.sleep(PRESS_INTERVAL)
            }

            Thread.sleep(SETTLE_AFTER_MS)

            var positionAfter = 0L
            var isPlaying     = false
            scenario.onActivity { activity ->
                positionAfter = activity.player?.msPlayer?.currentPosition ?: 0L
                isPlaying     = activity.player?.msPlayer?.isPlaying ?: false
            }

            val expectedMaxPosition = positionBefore - (PRESS_COUNT - 1) * SKIP_AMOUNT_MS

            // ── Assertion 1: retrocedió lo suficiente ───────────────────────
            assertWithMessage(
                "BUG DE FOCO: $PRESS_COUNT presses de retroceso no generaron el seek esperado.\n" +
                "  Posición antes : ${positionBefore / 1000}s\n" +
                "  Posición después: ${positionAfter / 1000}s\n" +
                "  El foco puede haber saltado al Play/Pause."
            ).that(positionAfter).isLessThan(expectedMaxPosition + SKIP_AMOUNT_MS)

            // ── Assertion 2: player sigue en PLAYING ────────────────────────
            assertWithMessage(
                "BUG DE FOCO: el player está PAUSADO. El foco fue al Play y lo toggleó."
            ).that(isPlaying).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [TV-FOCUS-03] Alternancia rápida ffwd/rew mantiene el control
    //
    // Simula un usuario que ajusta su posición alternando avance y retroceso.
    // Cada press cambia de botón (DPAD_RIGHT / DPAD_LEFT para navegar entre ellos).
    // El player no debe pausarse en ningún punto del ciclo.
    // -------------------------------------------------------------------------
    @Test
    fun alternatingSkipButtons_playerNeverPauses() {
        ActivityScenario.launch(TvPlayerActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay",  TIMEOUT)

            // Avanzar para tener margen en ambas direcciones
            scenario.onActivity { activity ->
                activity.player?.msPlayer?.seekTo(120_000L) // 2 min
                activity.callbackCaptor.reset()
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
            Thread.sleep(500)

            // Ir al botón ffwd
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            Thread.sleep(200)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            Thread.sleep(200)

            // 4 ciclos: press ffwd → navegar a rew → press rew → navegar a ffwd
            repeat(4) { cycle ->
                // Presionar botón actual (ffwd o rew)
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                Thread.sleep(PRESS_INTERVAL)

                // Navegar al botón opuesto (4 presses en dirección contraria)
                val direction = if (cycle % 2 == 0) KeyEvent.KEYCODE_DPAD_LEFT
                                else                 KeyEvent.KEYCODE_DPAD_RIGHT
                repeat(4) {
                    device.pressKeyCode(direction)
                    Thread.sleep(150)
                }
            }

            Thread.sleep(SETTLE_AFTER_MS)

            var isPlaying = false
            scenario.onActivity { activity ->
                isPlaying = activity.player?.msPlayer?.isPlaying ?: false
            }

            assertWithMessage(
                "BUG DE FOCO: el player está PAUSADO después de alternancia ffwd/rew.\n" +
                "  El foco fue al Play/Pause en algún punto del ciclo."
            ).that(isPlaying).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [TV-FOCUS-04] Presses rápidos con media keys (fallback sin DPAD navigation)
    //
    // Usa KEYCODE_MEDIA_FAST_FORWARD directamente — bypasea la UI del player
    // y envía el media key al SDK. Sirve como baseline:
    //   - Si este test pasa y los tests con DPAD fallan → el bug está en el
    //     manejo de foco del SDK, no en el procesamiento de seek.
    //   - Si ambos fallan → el bug está en el procesamiento de seek en sí.
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

            // Media keys no requieren foco en un botón específico
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

            val expectedMinAdvance = (PRESS_COUNT - 1).toLong() * SKIP_AMOUNT_MS

            assertWithMessage(
                "KEYCODE_MEDIA_FAST_FORWARD no generó avance suficiente.\n" +
                "  Si DvrSeek tests pasan y este falla → bug de procesamiento de media key en TV."
            ).that(positionAfter - positionBefore).isGreaterThan(expectedMinAdvance)

            assertWithMessage("El player no debe pausarse con media fast forward")
                .that(isPlaying).isTrue()
        }
    }
}

