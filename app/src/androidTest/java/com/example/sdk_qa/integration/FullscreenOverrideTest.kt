package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.sdk_qa.annotation.MobileOnly
import com.example.sdk_qa.scenarios.video.VideoFullscreenOverrideScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoFullscreenOverrideScenarioActivity.Companion.EXTRA_ENABLE_OFF_OVERRIDE
import com.example.sdk_qa.scenarios.video.VideoFullscreenOverrideScenarioActivity.Companion.EXTRA_ENABLE_ON_OVERRIDE
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertCallbackNotFired
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests para los overrides de botones de fullscreen (feat/allow-override-rotation → SDK 10.0.5-alpha01).
 *
 * CAMBIOS TESTEADOS:
 *   1. Nueva propiedad: MediastreamPlayerConfig.onFullscreenOnClick
 *      - Cuando está configurado, REEMPLAZA la llamada a enterFullscreen()
 *      - Los botones hacen su swap de visibilidad normalmente
 *
 *   2. Cambio de comportamiento: onFullscreenOffClick
 *      - Ahora se invoca AUNQUE isOnFullscreen == false (elimina el guard anterior)
 *      - Antes: solo se llamaba si el player YA estaba en fullscreen
 *      - Ahora: se llama siempre que el usuario presiona el botón de salir
 *      - Necesario para React Native / bridges donde el estado nativo puede desfasarse
 *
 * Arquitectura:
 *   - Tests smoke (sin @MobileOnly): verifican que el player arranca sin crash con overrides
 *   - Tests @MobileOnly: verifican el comportamiento de los clicks en botones
 *     (botones de fullscreen solo son visibles/relevantes en mobile, no en TV)
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class FullscreenOverrideTest {

    @get:Rule val sdkRule = SdkTestRule()

    private val TIMEOUT = 20_000L

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-01] Player arranca normalmente con ambos overrides configurados
    //
    // Smoke test compatible con TV. Verifica que instalar los callbacks no
    // causa crashes ni bloquea la inicialización del player.
    // -------------------------------------------------------------------------
    @Test
    fun playerWithBothOverrides_onReady_fires() {
        val intent = VideoFullscreenOverrideScenarioActivity.intentWithOverrides(
            enableOn = true, enableOff = true
        )
        ActivityScenario.launch<VideoFullscreenOverrideScenarioActivity>(intent).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-02] Player arranca normalmente SIN overrides (comportamiento por defecto)
    //
    // Smoke test — garantiza que el código de override no rompe el path default.
    // -------------------------------------------------------------------------
    @Test
    fun playerWithNoOverrides_onReady_fires() {
        ActivityScenario.launch(VideoFullscreenOverrideScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-03] onFullscreenOnClick se invoca al presionar el botón de entrar
    //
    // Con el override activo, presionar el botón "enter fullscreen" debe llamar al
    // Consumer registrado. El contador debe incrementar de 0 a 1.
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly // fullscreen buttons are hidden on TV (isDeviceTV() check in SDK Customizer)
    fun fullscreenOnOverride_isInvoked_onButtonClick() {
        val intent = VideoFullscreenOverrideScenarioActivity.intentWithOverrides(enableOn = true)
        ActivityScenario.launch<VideoFullscreenOverrideScenarioActivity>(intent).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            var countBefore = 0
            scenario.onActivity { countBefore = it.onFullscreenOnCallCount.get() }

            scenario.onActivity { it.simulateFullscreenOnClick() }

            var countAfter = 0
            scenario.onActivity { countAfter = it.onFullscreenOnCallCount.get() }

            assertWithMessage("onFullscreenOnClick override debe invocarse exactamente una vez")
                .that(countAfter).isEqualTo(countBefore + 1)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-04] Con override activo, enterFullscreen() NO se llama
    //
    // El override REEMPLAZA a enterFullscreen(). Si isOnFullscreen se mantiene
    // false después del click, significa que enterFullscreen() no fue invocado.
    // El callback onFullscreen (del SDK) tampoco debe dispararse.
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly
    fun fullscreenOnOverride_preventsDefaultEnterFullscreen() {
        val intent = VideoFullscreenOverrideScenarioActivity.intentWithOverrides(enableOn = true)
        ActivityScenario.launch<VideoFullscreenOverrideScenarioActivity>(intent).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.onActivity { it.simulateFullscreenOnClick() }

            // Si enterFullscreen() hubiese sido llamado, dispararía el callback onFullscreen
            scenario.assertCallbackNotFired("onFullscreen")

            // isOnFullscreen permanece false porque el override reemplazó enterFullscreen()
            var isInFullscreen = false
            scenario.onActivity { isInFullscreen = it.player?.isOnFullscreen ?: false }
            assertWithMessage("isOnFullscreen debe ser false — override reemplaza enterFullscreen()")
                .that(isInFullscreen).isFalse()
        }
    }

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-05] onFullscreenOffClick se invoca SIN necesidad de isOnFullscreen == true
    //
    // COMPORTAMIENTO CLAVE DEL BRANCH:
    // El override de "exit fullscreen" se invoca aunque isOnFullscreen sea false.
    //
    // Secuencia del test:
    //   1. Instalar onFullscreenOnClick (para que isOnFullscreen NO se ponga true al clickar "on")
    //   2. Clickar "enter fullscreen" → override on dispara, isOnFullscreen permanece false,
    //      btn_fullscreen_off se hace visible (el SDK siempre hace el swap de visibilidad)
    //   3. Clickar "exit fullscreen" → override off debe dispararse AUNQUE isOnFullscreen == false
    //
    // Con el código ANTERIOR, el override off solo se invocaba si isOnFullscreen era true,
    // por lo que en este escenario NO se habría llamado (conteo quedaría en 0).
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly
    fun fullscreenOffOverride_firesWithoutFullscreenGuard() {
        val intent = VideoFullscreenOverrideScenarioActivity.intentWithOverrides(
            enableOn = true,   // evita que isOnFullscreen se ponga true
            enableOff = true
        )
        ActivityScenario.launch<VideoFullscreenOverrideScenarioActivity>(intent).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Paso 1: click "enter fullscreen" → override on dispara, btn_off se hace visible
            scenario.onActivity { it.simulateFullscreenOnClick() }

            // Verificar que isOnFullscreen sigue false (override no llama enterFullscreen)
            var isInFullscreen = true
            scenario.onActivity { isInFullscreen = it.player?.isOnFullscreen ?: true }
            assertWithMessage("Prerequisito: isOnFullscreen debe ser false antes del click off")
                .that(isInFullscreen).isFalse()

            // Paso 2: click "exit fullscreen" — con el nuevo código esto SIEMPRE invoca el override
            scenario.onActivity { it.simulateFullscreenOffClick() }

            var countOff = 0
            scenario.onActivity { countOff = it.onFullscreenOffCallCount.get() }

            assertWithMessage(
                "onFullscreenOffClick debe invocarse aunque isOnFullscreen == false\n" +
                "(comportamiento nuevo en SDK 10.0.5-alpha01 — el guard isOnFullscreen " +
                "fue eliminado del path del override)"
            ).that(countOff).isEqualTo(1)

            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-06] Sin override, el botón "exit" respeta el guard isOnFullscreen
    //
    // Verifica que el path DEFAULT sigue funcionando igual:
    // sin override, exitFullscreen() solo se llama si isOnFullscreen es true.
    // Aquí: isOnFullscreen es false → offFullscreen callback NO dispara.
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly
    fun fullscreenOff_withoutOverride_respectsFullscreenGuard() {
        // Sin overrides → comportamiento por defecto del SDK
        ActivityScenario.launch(VideoFullscreenOverrideScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Intentar salir de fullscreen sin haber entrado → exitFullscreen() no debe llamarse
            scenario.onActivity { it.simulateFullscreenOffClick() }

            // offFullscreen callback no debe dispararse (exitFullscreen no fue llamado)
            scenario.assertCallbackNotFired("offFullscreen")
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [FS-OVERRIDE-07] Swap de visibilidad ocurre correctamente con override activo
    //
    // Verifica que al clickar "enter fullscreen" con override:
    //   - onFullscreenOnCallCount == 1 (override llamado)
    //   - onFullscreenOffCallCount == 0 (off override NO llamado)
    // Doble click: on → off (ambos overrides activos)
    //   - onFullscreenOnCallCount == 1
    //   - onFullscreenOffCallCount == 1
    // -------------------------------------------------------------------------
    @Test
    @MobileOnly
    fun bothOverrides_eachCalledExactlyOnce_onDoubleClick() {
        val intent = VideoFullscreenOverrideScenarioActivity.intentWithOverrides(
            enableOn = true, enableOff = true
        )
        ActivityScenario.launch<VideoFullscreenOverrideScenarioActivity>(intent).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            // Click "enter fullscreen"
            scenario.onActivity { it.simulateFullscreenOnClick() }

            var countOn = 0; var countOff = 0
            scenario.onActivity {
                countOn  = it.onFullscreenOnCallCount.get()
                countOff = it.onFullscreenOffCallCount.get()
            }
            assertWithMessage("Tras click on: onFullscreenOnClick debe haber sido llamado 1 vez")
                .that(countOn).isEqualTo(1)
            assertWithMessage("Tras click on: onFullscreenOffClick NO debe haber sido llamado")
                .that(countOff).isEqualTo(0)

            // Click "exit fullscreen"
            scenario.onActivity { it.simulateFullscreenOffClick() }

            scenario.onActivity {
                countOn  = it.onFullscreenOnCallCount.get()
                countOff = it.onFullscreenOffCallCount.get()
            }
            assertWithMessage("Tras click off: onFullscreenOnClick debe seguir en 1")
                .that(countOn).isEqualTo(1)
            assertWithMessage("Tras click off: onFullscreenOffClick debe haber sido llamado 1 vez")
                .that(countOff).isEqualTo(1)

            scenario.assertNoErrorFired()
        }
    }
}
