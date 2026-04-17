package com.example.sdk_qa.utils

import android.util.Log
import androidx.test.core.app.ActivityScenario
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
