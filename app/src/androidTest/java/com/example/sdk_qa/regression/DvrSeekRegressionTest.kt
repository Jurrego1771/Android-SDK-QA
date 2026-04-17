package com.example.sdk_qa.regression

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.scenarios.video.VideoLiveDvrScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertInDvrMode
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.awaitDvrWindow
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión: Bug crítico de seek en DVR.
 *
 * COMPORTAMIENTO INCORRECTO (bug):
 *   seekBackward(X) → la barra de progreso regresa al inicio del buffer (posición ≈ 0)
 *   en lugar de quedarse en la posición seleccionada.
 *
 * COMPORTAMIENTO ESPERADO:
 *   seekBackward(X) activa DVR mode, carga URL DVR, y posiciona el player X ms antes
 *   del live edge. getCurrentDvrPosition() debe reflejar esa posición correctamente.
 *
 * API correcta (SDK v11 — fixes DVR commits 41d740a0, e1bfc267):
 *   player.seekBackward(ms)          — seek atrás; activa DVR mode si está en LIVE
 *   player.seekForward(ms)           — seek adelante; vuelve a LIVE si supera live edge
 *   player.seekInDvr(positionMs)     — seek absoluto dentro de la ventana DVR activa
 *   player.switchToLive()            — vuelve a LIVE desde DVR
 *   player.isInDvrMode()             — verifica estado DVR
 *   player.getCurrentDvrPosition()   — posición actual en la ventana DVR (ms)
 *   player.getDvrDuration()          — duración de la ventana DVR cargada (ms)
 *
 * NO usar msPlayer.seekTo() para DVR — bypasea la lógica de estado del SDK.
 * NO usar msPlayer.duration antes del seek — en modo live solo refleja buffer HLS (~36s).
 *
 * Tolerancia de posición: ±15s (margen para alineación de keyframes HLS).
 * Zona muerta del bug: posición < 30s después de seek deliberado = snap bug.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DvrSeekRegressionTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT        = 45_000L // DVR carga URL distinta — red del emulador es lenta
    private val SEEK_TOLERANCE = 15_000L // ±15s tolerancia para alineación de keyframes HLS
    private val BUG_DEAD_ZONE  = 30_000L // posición < 30s post-seek = snap bug confirmado

    // -------------------------------------------------------------------------
    // [DVR-SEEK-01] seekBackward respeta la posición seleccionada
    //
    // seekBackward() activa DVR mode internamente (carga URL DVR con dvrOffset).
    // La posición final debe estar cerca de liveEdge - 2min, NUNCA cerca de 0.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_backward_respectsSelectedPosition() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            // seekBackward activa DVR mode: carga URL DVR y dispara onReady al estar listo
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekBackward(2 * 60 * 1000L)
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            // Con DVR activo getDvrDuration() refleja la ventana real (no el buffer HLS)
            val dvrDuration = scenario.awaitDvrWindow()
            scenario.assertInDvrMode()

            var finalPosition = 0L
            scenario.onActivity { activity ->
                finalPosition = activity.player?.getCurrentDvrPosition() ?: 0L
            }

            val seekTarget = (dvrDuration - 2 * 60 * 1000L).coerceAtLeast(0L)

            assertWithMessage(
                "BUG DE REGRESIÓN: seekBackward(2min) debería respetar la posición.\n" +
                "  DVR duration  : ${dvrDuration / 1000}s\n" +
                "  Seek objetivo : ${seekTarget / 1000}s\n" +
                "  Posición final: ${finalPosition / 1000}s"
            ).that(finalPosition).isGreaterThan(seekTarget - SEEK_TOLERANCE)

            assertWithMessage(
                "BUG CONFIRMADO: player en ${finalPosition / 1000}s — snap al inicio del buffer"
            ).that(finalPosition).isGreaterThan(BUG_DEAD_ZONE)

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-02] La posición no hace snap al buffer start después del seek
    //
    // Verifica el síntoma exacto del bug: "reinicia la barra enviándola al inicio".
    // Verifica 3 veces post-seek que la posición no rebota a 0.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_backward_doesNotSnapToBufferStart() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekBackward(5 * 60 * 1000L)
            }
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitDvrWindow()
            scenario.assertInDvrMode()

            repeat(3) { attempt ->
                Thread.sleep(1_000)

                var position = 0L
                scenario.onActivity { activity ->
                    position = activity.player?.getCurrentDvrPosition() ?: 0L
                }

                assertWithMessage(
                    "Verificación ${attempt + 1}/3: posición ${position / 1000}s en zona de bug. " +
                    "El player hizo snap al inicio del buffer tras seekBackward(5min)."
                ).that(position).isGreaterThan(BUG_DEAD_ZONE)
            }

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-03] Múltiples seeks, cada uno respeta su posición
    //
    // Seek #1: seekBackward(8min) — entra a DVR mode
    // Seek #2: seekInDvr(5min antes del edge) — seek absoluto dentro de DVR
    // Seek #3: seekInDvr(2min antes del edge) — seek absoluto dentro de DVR
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_multipleSeeks_eachRespectedIndependently() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            // Seek #1 — entra a DVR mode
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekBackward(8 * 60 * 1000L)
            }
            scenario.awaitCallback("onReady", TIMEOUT)
            val dvrDuration = scenario.awaitDvrWindow(minDurationMs = 10 * 60 * 1000L)
            scenario.assertInDvrMode()

            var finalPos = 0L
            scenario.onActivity { activity ->
                finalPos = activity.player?.getCurrentDvrPosition() ?: 0L
            }
            assertWithMessage("Seek #1: snap bug — ${finalPos / 1000}s indica regreso al inicio")
                .that(finalPos).isGreaterThan(BUG_DEAD_ZONE)

            // Seek #2 — absoluto: 5min antes del live edge
            val target2 = (dvrDuration - 5 * 60 * 1000L).coerceAtLeast(0L)
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekInDvr(target2)
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            scenario.onActivity { activity ->
                finalPos = activity.player?.getCurrentDvrPosition() ?: 0L
            }
            assertWithMessage(
                "Seek #2 a ${target2 / 1000}s → posición ${finalPos / 1000}s fuera de tolerancia"
            ).that(finalPos).isGreaterThan(target2 - SEEK_TOLERANCE)
            assertWithMessage("Seek #2: snap bug en ${finalPos / 1000}s")
                .that(finalPos).isGreaterThan(BUG_DEAD_ZONE)

            // Seek #3 — absoluto: 2min antes del live edge
            val target3 = (dvrDuration - 2 * 60 * 1000L).coerceAtLeast(0L)
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekInDvr(target3)
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            scenario.onActivity { activity ->
                finalPos = activity.player?.getCurrentDvrPosition() ?: 0L
            }
            assertWithMessage(
                "Seek #3 a ${target3 / 1000}s → posición ${finalPos / 1000}s fuera de tolerancia"
            ).that(finalPos).isGreaterThan(target3 - SEEK_TOLERANCE)
            assertWithMessage("Seek #3: snap bug en ${finalPos / 1000}s")
                .that(finalPos).isGreaterThan(BUG_DEAD_ZONE)

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-04] El playback avanza desde la posición del seek, no desde 0
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_playbackAdvancesFromSeekPosition_notFromBufferStart() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekBackward(3 * 60 * 1000L)
            }
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitDvrWindow()
            scenario.assertInDvrMode()

            var positionAtSeek = 0L
            scenario.onActivity { activity ->
                positionAtSeek = activity.player?.getCurrentDvrPosition() ?: 0L
            }

            assertWithMessage(
                "El player empezó desde el inicio del buffer (bug), no desde el seek target.\n" +
                "  Posición post-seek: ${positionAtSeek / 1000}s"
            ).that(positionAtSeek).isGreaterThan(BUG_DEAD_ZONE)

            Thread.sleep(5_000)

            var positionAfterPlay = 0L
            scenario.onActivity { activity ->
                positionAfterPlay = activity.player?.getCurrentDvrPosition() ?: 0L
            }

            assertWithMessage(
                "El playback debe avanzar desde ${positionAtSeek / 1000}s (posición post-seek).\n" +
                "  Posición post-seek: ${positionAtSeek / 1000}s\n" +
                "  Posición tras 5s  : ${positionAfterPlay / 1000}s"
            ).that(positionAfterPlay).isGreaterThan(positionAtSeek + 2_000L)
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-05] seekForward pasando el live edge vuelve a LIVE
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_forwardPastLiveEdge_returnsToLive() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            // Entrar a DVR mode
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekBackward(2 * 60 * 1000L)
            }
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitDvrWindow()
            scenario.assertInDvrMode()

            // seekForward sobrepasando el live edge → SDK vuelve a LIVE automáticamente
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.seekForward(3 * 60 * 1000L)
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            var inDvr = true
            scenario.onActivity { activity ->
                inDvr = activity.player?.isInDvrMode() ?: true
            }

            assertWithMessage(
                "Después de seekForward pasando el live edge, el player debe estar en LIVE mode"
            ).that(inDvr).isFalse()

            scenario.assertNoErrorFired()
        }
    }
}
