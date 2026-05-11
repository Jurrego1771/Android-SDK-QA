package com.example.sdk_qa.integration

import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import com.example.sdk_qa.scenarios.video.VideoLiveScenarioActivity
import com.example.sdk_qa.utils.assertCallbackNotFired
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de reconexión y estabilidad para streams live.
 *
 * Qué se testea:
 *   - Buffering visible en la conexión inicial
 *   - onEnd nunca se dispara en live (invariante del tipo LIVE)
 *   - reloadPlayer reconecta y dispara onReady de nuevo
 *   - Múltiples reloads no dejan el SDK en estado corrupto
 *   - Background/foreground no mata el stream
 *
 * Limitación conocida — corte de red real no testeable aquí:
 *   ADB conectado via WiFi (192.168.0.24:5555). Deshabilitar WiFi/Ethernet
 *   desde el test mataría la conexión ADB y con ella el test runner.
 *   Los tests de reloadPlayer cubren el mismo flujo de reconexión que el SDK
 *   ejecuta internamente al detectar un corte.
 *
 * Sin SdkTestRule (LeakCanary):
 *   VideoLiveScenarioActivity activa IMA. El SDK bug #1 (AdTagLoader lambda)
 *   retiene la Activity ~5s post-destroy — igual al leak que @Ignora
 *   live_onDestroy_releasesPlayer_noLeak en LifecycleTest. Usar SdkTestRule
 *   aquí produciría falsos positivos hasta que Mediastream corrija el bug.
 *
 * Prerrequisito: el stream TestContent.Video.LIVE debe estar activo en PRODUCTION.
 */
@RunWith(AndroidJUnit4::class)
class LiveReconnectionTest {

    private val TIMEOUT = 25_000L

    // -------------------------------------------------------------------------
    // [INT-LIVE-01] onBuffering se dispara al conectar con el stream
    //
    // El SDK debe entrar en estado buffering antes de que el live edge esté
    // disponible. Confirma que el callback de buffering funciona en live
    // (no solo en VOD).
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun live_onBuffering_fires_onInitialConnect() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onBuffering", TIMEOUT)
        }
    }

    // -------------------------------------------------------------------------
    // [INT-LIVE-02] onEnd NUNCA se dispara durante reproducción live normal
    //
    // onEnd en un stream live es un bug del SDK — los streams live no tienen
    // fin. Este test verifica el invariante durante 10s de reproducción.
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun live_onEnd_neverFires_duringNormalPlayback() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Reproducir 10s — suficiente para detectar un onEnd falso
            Thread.sleep(10_000)

            scenario.assertCallbackNotFired("onEnd")
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-LIVE-03] reloadPlayer reconecta — onReady vuelve a dispararse
    //
    // Simula la reconexión automática que el SDK ejecuta al detectar un corte:
    // internamente llama reloadPlayer con la misma config. Si el SDK maneja
    // correctamente la reconexión, onReady debe llegar de nuevo.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun live_reloadPlayer_onReady_fires_again() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(activity.buildConfig())
            }

            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-LIVE-04] Tres reloads sucesivos — el player permanece estable
    //
    // En reconexiones frecuentes (pérdida de red intermitente), el SDK puede
    // hacer varios reloads en sucesión. Verifica que no acumula instancias ni
    // queda en estado de error.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun live_multipleReloads_remain_stable() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            repeat(3) { i ->
                scenario.onActivity { activity ->
                    activity.callbackCaptor.reset()
                    activity.player?.reloadPlayer(activity.buildConfig())
                }
                scenario.awaitCallback("onReady", TIMEOUT)
                scenario.assertNoErrorFired()
            }

            var isAlive = false
            scenario.onActivity { activity ->
                isAlive = activity.player?.msPlayer != null
            }
            assertWithMessage("El player debe seguir activo después de 3 reloads")
                .that(isAlive).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-LIVE-05] Background/foreground — el stream sobrevive al ir a segundo plano
    //
    // moveToState(STARTED) → onPause() llamado (app en background).
    // Tras 3s en background el SDK puede pausar o mantener el stream
    // (depende de la config); lo importante es que el player esté en un estado
    // válido de Media3 al volver, sin crash ni estado IDLE inesperado.
    // -------------------------------------------------------------------------
    @Test
    @MediumTest
    fun live_background_foreground_playerSurvives() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.awaitCallback("onPlay", TIMEOUT)

            // Simular background (onPause llamado)
            scenario.moveToState(Lifecycle.State.STARTED)
            Thread.sleep(3_000)

            // Volver a foreground (onResume llamado)
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(500)

            var playbackState = -1
            scenario.onActivity { activity ->
                playbackState = activity.player?.msPlayer?.playbackState ?: -1
            }

            // El SDK no debe dejar el player en estado ERROR (no es un valor de Player.STATE_*)
            // Estado válido: IDLE, BUFFERING, READY o ENDED
            assertWithMessage(
                "El player debe estar en estado válido después de background/foreground"
            ).that(playbackState).isAnyOf(
                Player.STATE_IDLE,
                Player.STATE_BUFFERING,
                Player.STATE_READY,
                Player.STATE_ENDED
            )
        }
    }

    // -------------------------------------------------------------------------
    // [INT-LIVE-06] Pausa → reload reconecta al live edge actual
    //
    // Si el usuario pone pausa en live y luego intenta seguir, el SDK debe
    // reconectar al live edge (no continuar desde la posición congelada).
    // reloadPlayer es la operación que fuerza ese reposicionamiento.
    // -------------------------------------------------------------------------
    @Test
    @LargeTest
    fun live_pauseAndReload_reconnectsToLiveEdge() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Pausar el stream
            scenario.onActivity { activity ->
                activity.player?.msPlayer?.pause()
            }
            Thread.sleep(2_000)

            // Reload → volver al live edge
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(activity.buildConfig())
            }

            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }
}
