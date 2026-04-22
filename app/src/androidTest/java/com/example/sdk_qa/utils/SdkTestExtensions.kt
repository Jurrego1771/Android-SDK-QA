package com.example.sdk_qa.utils

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.CallbackCaptor
import com.google.common.truth.Truth.assertWithMessage

/**
 * Extensiones para reducir boilerplate en tests del SDK.
 */

private const val TAG = "SDK_QA_FAIL"

// Orden esperado del ciclo de vida — usado para diagnosticar qué faltó
private val LIFECYCLE_CALLBACKS = listOf(
    "playerViewReady", "onBuffering", "onReady", "onPlay",
    "onPause", "onEnd", "onError", "onPlayerClosed", "onPlayerReload",
    "onNext", "onPrevious", "nextEpisodeIncoming", "onNewSourceAdded",
    "onFullscreen", "offFullscreen", "onDismissButton",
    "onAdEvents", "onAdErrorEvent",
    "onCastAvailable", "onCastSessionStarted", "onCastSessionEnded",
    "onPlaybackErrors", "onEmbedErrors", "onLiveAudioCurrentSongChanged"
)

/**
 * Obtiene el [CallbackCaptor] de la activity bajo test.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.getCallbackCaptor(): CallbackCaptor {
    var captor: CallbackCaptor? = null
    onActivity { captor = it.callbackCaptor }
    return captor!!
}

/**
 * Espera hasta que el evento [eventName] sea registrado, con [timeoutMs] de límite.
 *
 * Si el timeout expira, el mensaje de fallo incluye:
 *   - Qué callbacks SÍ llegaron
 *   - Qué callbacks del ciclo de vida NO llegaron
 *
 * Esto elimina la necesidad de revisar Logcat para entender qué pasó.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.awaitCallback(
    eventName: String,
    timeoutMs: Long = 15_000
) {
    val captor = getCallbackCaptor()
    val received = captor.awaitEvent(eventName, timeoutMs)

    if (!received) {
        val fired    = captor.allEvents()
        val missing  = LIFECYCLE_CALLBACKS.filter { it !in fired }
        val onMain   = fired.filter { captor.firedOnMainThread(it) }
        val offMain  = fired.filter { !captor.firedOnMainThread(it) }

        // Volcar al Logcat para que run-tests.sh lo pueda extraer
        Log.e(TAG, "╔═══════════════════════════════════════════════")
        Log.e(TAG, "║ TIMEOUT: '$eventName' no llegó en ${timeoutMs}ms")
        Log.e(TAG, "║ Recibidos (${fired.size}): ${fired.ifEmpty { setOf("ninguno") }.joinToString()}")
        Log.e(TAG, "║ Faltantes: ${missing.ifEmpty { listOf("ninguno") }.joinToString()}")
        if (offMain.isNotEmpty()) Log.e(TAG, "║ ⚠ Callbacks fuera del Main Thread: $offMain")
        Log.e(TAG, "╚═══════════════════════════════════════════════")

        assertWithMessage(
            "Timeout esperando '$eventName' (${timeoutMs}ms)\n" +
            "  Recibidos : ${fired.ifEmpty { setOf("ninguno") }}\n" +
            "  Faltantes : ${missing.ifEmpty { listOf("ninguno") }}"
        ).that(received).isTrue()
    }
}

/**
 * Verifica que el evento [eventName] NO haya ocurrido hasta ahora.
 * No bloquea — solo lee el estado actual.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.assertCallbackNotFired(eventName: String) {
    val captor = getCallbackCaptor()
    if (captor.hasEvent(eventName)) {
        val fired = captor.allEvents()
        Log.e(TAG, "UNEXPECTED '$eventName'. Todos los recibidos: $fired")
        assertWithMessage(
            "Callback '$eventName' no debería haber ocurrido\n" +
            "  Todos los recibidos: $fired"
        ).that(captor.hasEvent(eventName)).isFalse()
    }
}

/**
 * Verifica que no haya ocurrido ningún error desde que arrancó la activity.
 * Cubre onError, onEmbedErrors y onPlaybackErrors.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.assertNoErrorFired() {
    assertCallbackNotFired("onError")
    assertCallbackNotFired("onEmbedErrors")
    assertCallbackNotFired("onPlaybackErrors")
}

/**
 * Espera a que la ventana DVR esté disponible según el SDK.
 *
 * Llamar DESPUÉS de [seekBackward] + [awaitCallback("onReady")] — solo entonces
 * el SDK carga la URL DVR y [getDvrDuration()] refleja la ventana real (ej. 7 200 000ms).
 * NO usar antes de un seek: [msPlayer.duration] en modo live es ~36s (buffer HLS), no la ventana DVR.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.awaitDvrWindow(
    minDurationMs: Long = 60_000L,
    timeoutMs: Long = 10_000L
): Long {
    val startMs = System.currentTimeMillis()
    var duration = 0L

    while (System.currentTimeMillis() - startMs < timeoutMs) {
        onActivity { activity ->
            duration = activity.player?.getDvrDuration() ?: 0L
        }
        if (duration > minDurationMs) return duration
        Thread.sleep(500)
    }

    assertWithMessage(
        "DVR window no disponible tras ${timeoutMs}ms\n" +
        "  getDvrDuration() observada: ${duration}ms\n" +
        "  mínimo requerido          : ${minDurationMs}ms\n" +
        "  Verificar que seekBackward() + onReady se ejecutaron antes de esta llamada"
    ).that(duration).isGreaterThan(minDurationMs)

    return duration
}

/**
 * Verifica que el player esté en modo DVR activo.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.assertInDvrMode() {
    var inDvr = false
    onActivity { activity -> inDvr = activity.player?.isInDvrMode() ?: false }
    assertWithMessage("El player debería estar en DVR mode").that(inDvr).isTrue()
}

/**
 * Espera a que ocurra cualquier tipo de error del SDK:
 * onError, onEmbedErrors o onPlaybackErrors.
 *
 * Útil para tests donde el tipo exacto de error puede variar
 * según el estado del backend (404 vs timeout vs API error).
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.awaitAnyError(timeoutMs: Long = 20_000): Boolean {
    val captor = getCallbackCaptor()
    // Intentar todos los tipos de error en paralelo via hilo secundario
    val startMs = System.currentTimeMillis()
    while (System.currentTimeMillis() - startMs < timeoutMs) {
        if (captor.hasEvent("onError") ||
            captor.hasEvent("onEmbedErrors") ||
            captor.hasEvent("onPlaybackErrors")) return true
        Thread.sleep(200)
    }
    return false
}

// =============================================================================
// UiAutomator — interacción con overlay del SDK
// =============================================================================

// Textos del overlay de siguiente episodio (SDK v10/v11 en español).
// Si el SDK cambia los labels, actualizar aquí.
internal object OverlayText {
    const val NEXT_EPISODE   = "Siguiente"   // botón "Siguiente episodio"
    const val KEEP_WATCHING  = "Seguir"      // botón "Seguir viendo"
}

/**
 * Obtiene la instancia de UiDevice para interacción con la UI del sistema.
 */
fun uiDevice(): UiDevice =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/**
 * Espera a que aparezca un elemento con texto que contenga [textContains],
 * luego lo clickea. Retorna true si encontró y clickeó el elemento.
 */
fun UiDevice.waitAndClick(textContains: String, timeoutMs: Long = 8_000L): Boolean {
    val selector = By.textContains(textContains)
    val appeared = wait(Until.hasObject(selector), timeoutMs)
    if (!appeared) return false
    findObject(selector)?.click()
    return true
}

/**
 * Verifica que un elemento con texto [textContains] sea visible en pantalla.
 */
fun UiDevice.assertVisible(textContains: String, timeoutMs: Long = 8_000L) {
    val appeared = wait(Until.hasObject(By.textContains(textContains)), timeoutMs)
    assertWithMessage("Elemento con texto '$textContains' no visible tras ${timeoutMs}ms")
        .that(appeared).isTrue()
}

/**
 * Verifica que un elemento con texto [textContains] NO sea visible en pantalla.
 * Útil para confirmar que el overlay desapareció.
 */
fun UiDevice.assertNotVisible(textContains: String, timeoutMs: Long = 3_000L) {
    val stillVisible = wait(Until.hasObject(By.textContains(textContains)), timeoutMs)
    assertWithMessage("Elemento con texto '$textContains' debería haber desaparecido")
        .that(stillVisible).isFalse()
}
