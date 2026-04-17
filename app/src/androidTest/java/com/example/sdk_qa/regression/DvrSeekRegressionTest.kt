package com.example.sdk_qa.regression

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.scenarios.video.VideoLiveDvrScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión: Bug crítico de seek en DVR.
 *
 * COMPORTAMIENTO INCORRECTO (bug):
 *   seekTo(posición_detrás_del_live) → la barra de progreso regresa al inicio
 *   del buffer (posición ≈ 0) en lugar de quedarse en la posición seleccionada.
 *
 * COMPORTAMIENTO ESPERADO:
 *   seekTo(X) → currentPosition se mantiene cerca de X (±tolerancia de keyframe)
 *   Nunca debe regresar a 0 ni a una posición menor al 5% del buffer.
 *
 * Contexto técnico:
 *   En LIVE+DVR, Media3 expone un "ventana deslizante":
 *     - posición 0         = inicio del buffer DVR disponible
 *     - posición duration  = live edge (ahora mismo)
 *   seekTo(X) debe posicionar en X dentro de esa ventana.
 *   El bug causa que el SDK ignore X y regrese al extremo izquierdo del buffer.
 *
 * Tolerancia de posición: ±15 segundos (margen para alineación de keyframes HLS).
 * Zona muerta del bug: posición < 30s después de un seek de >60s = fallo.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DvrSeekRegressionTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT         = 20_000L // Live streams tardan más en prepararse
    private val SEEK_SETTLE_MS  = 3_000L  // Tiempo para que el seek se estabilice
    private val SEEK_TOLERANCE  = 15_000L // ±15s de tolerancia en la posición final
    private val BUG_DEAD_ZONE   = 30_000L // Posición < 30s después de un seek deliberado = bug

    // -------------------------------------------------------------------------
    // [DVR-SEEK-01] seekTo respeta la posición seleccionada
    //
    // Seek principal: 2 minutos atrás desde el live edge.
    // La posición final debe estar cerca del objetivo, NUNCA cerca de 0.
    //
    // Este es el test de regresión directo del bug reportado.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_backward_respectsSelectedPosition() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            var liveEdge   = 0L
            var seekTarget = 0L

            scenario.onActivity { activity ->
                liveEdge = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("El stream DVR debe reportar duración > 0 para poder hacer seek")
                .that(liveEdge).isGreaterThan(0L)

            // Seek 2 minutos antes del live edge
            seekTarget = (liveEdge - 2 * 60 * 1000L).coerceAtLeast(0L)

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.msPlayer?.seekTo(seekTarget)
            }

            // Esperar que el seek complete (buffering → ready)
            scenario.awaitCallback("onReady", TIMEOUT)

            var finalPosition = 0L
            scenario.onActivity { activity ->
                finalPosition = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            // ── Assertion principal: posición respetada ──────────────────────
            assertWithMessage(
                "BUG DE REGRESIÓN: seekTo(${seekTarget / 1000}s) debería respetar la posición.\n" +
                "  Seek objetivo : ${seekTarget / 1000}s\n" +
                "  Posición final: ${finalPosition / 1000}s\n" +
                "  El player regresó al inicio del buffer (bug)"
            ).that(finalPosition)
                .isGreaterThan(seekTarget - SEEK_TOLERANCE)

            // ── Assertion del síntoma: NO debe estar en el inicio ───────────
            assertWithMessage(
                "BUG CONFIRMADO: después de seekTo(${seekTarget / 1000}s), " +
                "el player está en ${finalPosition / 1000}s — cerca del inicio del buffer (snap bug)"
            ).that(finalPosition)
                .isGreaterThan(BUG_DEAD_ZONE)
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-02] La posición no regresa al buffer start después del seek
    //
    // Verificación explícita del síntoma reportado:
    // "reinicia la barra de progreso enviándola de vuelta al inicio del buffer"
    //
    // Tras seekTo, esperamos la estabilización y verificamos varias veces
    // que la posición no "rebota" de vuelta a cero.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_backward_doesNotSnapToBufferStart() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            var liveEdge = 0L
            scenario.onActivity { activity ->
                liveEdge = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("DVR stream debe tener duración conocida")
                .that(liveEdge).isGreaterThan(60_000L)

            val seekTarget = (liveEdge - 5 * 60 * 1000L).coerceAtLeast(0L)

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.msPlayer?.seekTo(seekTarget)
            }

            scenario.awaitCallback("onReady", TIMEOUT)

            // Verificar posición 3 veces en intervalos — si hay snap, sucede aquí
            repeat(3) { attempt ->
                Thread.sleep(1_000)

                var position = 0L
                scenario.onActivity { activity ->
                    position = activity.player?.msPlayer?.currentPosition ?: 0L
                }

                assertWithMessage(
                    "Verificación ${ attempt + 1}/3: posición ${position / 1000}s está en la " +
                    "zona de bug (buffer start). El player hizo snap de ${seekTarget / 1000}s → 0."
                ).that(position)
                    .isGreaterThan(BUG_DEAD_ZONE)
            }
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-03] Múltiples seeks hacia atrás, cada uno respeta su posición
    //
    // El bug podría manifestarse solo en el primer seek o acumularse con
    // seeks consecutivos. Este test verifica que cada seek es correcto.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_multipleBackwardSeeks_eachRespectedIndependently() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            var liveEdge = 0L
            scenario.onActivity { activity ->
                liveEdge = activity.player?.msPlayer?.duration ?: 0L
            }

            assertWithMessage("DVR stream debe tener buffer > 10 minutos para este test")
                .that(liveEdge).isGreaterThan(10 * 60 * 1000L)

            // 3 seeks a posiciones distintas (8 min, 5 min, 2 min antes del live)
            val seekTargets = listOf(
                liveEdge - 8 * 60 * 1000L,
                liveEdge - 5 * 60 * 1000L,
                liveEdge - 2 * 60 * 1000L
            ).map { it.coerceAtLeast(0L) }

            seekTargets.forEachIndexed { index, target ->
                scenario.onActivity { activity ->
                    activity.callbackCaptor.reset()
                    activity.player?.msPlayer?.seekTo(target)
                }

                scenario.awaitCallback("onReady", TIMEOUT)

                var finalPos = 0L
                scenario.onActivity { activity ->
                    finalPos = activity.player?.msPlayer?.currentPosition ?: 0L
                }

                assertWithMessage(
                    "Seek #${index + 1} a ${target / 1000}s → posición final ${finalPos / 1000}s " +
                    "está fuera de tolerancia"
                ).that(finalPos).isGreaterThan(target - SEEK_TOLERANCE)

                assertWithMessage(
                    "Seek #${index + 1}: posición ${finalPos / 1000}s indica snap al inicio del buffer"
                ).that(finalPos).isGreaterThan(BUG_DEAD_ZONE)
            }
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-04] El playback continúa avanzando desde la posición del seek
    //
    // Verifica que, después del seek, el progreso avanza hacia adelante
    // desde la posición correcta — no desde el inicio del buffer.
    //
    // Si el bug existe: el player avanza desde 0, no desde el seek target.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_playbackAdvancesFromSeekPosition_notFromBufferStart() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            var liveEdge = 0L
            scenario.onActivity { activity ->
                liveEdge = activity.player?.msPlayer?.duration ?: 0L
            }

            val seekTarget = (liveEdge - 3 * 60 * 1000L).coerceAtLeast(0L)

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.msPlayer?.seekTo(seekTarget)
            }

            scenario.awaitCallback("onReady", TIMEOUT)

            // Capturar posición inicial post-seek
            var positionAtSeek = 0L
            scenario.onActivity { activity ->
                positionAtSeek = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            // Dejar reproducir 5 segundos
            Thread.sleep(5_000)

            // Capturar posición después de reproducir
            var positionAfterPlay = 0L
            scenario.onActivity { activity ->
                positionAfterPlay = activity.player?.msPlayer?.currentPosition ?: 0L
            }

            // El playback debe haber avanzado desde la posición del seek
            assertWithMessage(
                "El playback debe avanzar desde ${positionAtSeek / 1000}s (posición post-seek).\n" +
                "  Posición post-seek  : ${positionAtSeek / 1000}s\n" +
                "  Posición tras 5s    : ${positionAfterPlay / 1000}s\n" +
                "  Si está cerca de 5s, el player empezó desde 0 (bug de snap)"
            ).that(positionAfterPlay).isGreaterThan(positionAtSeek + 2_000L)

            // El punto de partida del playback no debería ser el inicio del buffer
            assertWithMessage(
                "El playback empezó desde el inicio del buffer (bug), no desde el seek target"
            ).that(positionAtSeek).isGreaterThan(BUG_DEAD_ZONE)
        }
    }

    // -------------------------------------------------------------------------
    // [DVR-SEEK-05] Seek al live edge funciona correctamente
    //
    // Verifica que el caso opuesto también funciona: después de ir atrás,
    // ir al live edge regresa a una posición cercana a duration.
    // -------------------------------------------------------------------------
    @Test
    fun dvrSeek_toLiveEdge_returnsToEnd() {
        ActivityScenario.launch(VideoLiveDvrScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()

            var liveEdge = 0L
            scenario.onActivity { activity ->
                liveEdge = activity.player?.msPlayer?.duration ?: 0L
            }

            // Primero ir atrás
            val backTarget = (liveEdge - 2 * 60 * 1000L).coerceAtLeast(0L)
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.msPlayer?.seekTo(backTarget)
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            // Luego ir al live edge
            scenario.onActivity { activity ->
                val currentEdge = activity.player?.msPlayer?.duration ?: 0L
                activity.callbackCaptor.reset()
                activity.player?.msPlayer?.seekTo(currentEdge)
            }
            scenario.awaitCallback("onReady", TIMEOUT)

            var finalPos = 0L
            var currentEdge = 0L
            scenario.onActivity { activity ->
                finalPos     = activity.player?.msPlayer?.currentPosition ?: 0L
                currentEdge  = activity.player?.msPlayer?.duration ?: 0L
            }

            // Debe estar cerca del live edge (dentro de 30 segundos)
            assertWithMessage(
                "Después de seek al live edge, la posición ${finalPos / 1000}s " +
                "debería estar cerca de ${currentEdge / 1000}s"
            ).that(finalPos).isGreaterThan(currentEdge - 30_000L)

            scenario.assertNoErrorFired()
        }
    }
}
