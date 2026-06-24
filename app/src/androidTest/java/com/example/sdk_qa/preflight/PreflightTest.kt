package com.example.sdk_qa.preflight

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.example.sdk_qa.integration.DirectHlsActivity
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preflight gate — sonda de salud del entorno ANTES de correr la batería completa.
 *
 * Problema que resuelve: toda la suite VOD cuelga de una misma cadena de dependencias
 * viva (device → red → backend Mediastream → init del SDK). Si un eslabón se cae, 50
 * tests se ponen rojos con el MISMO síntoma (`onReady` nunca llegó) y se pierde la
 * capacidad de distinguir regresión real de ruido de entorno.
 *
 * Este único test ejercita esa cadena con la ruta feliz canónica (VOD real por backend,
 * el mismo path que usan VodIntegration/Smoke/Callback/etc.). `run-tests.sh` lo corre
 * primero y, si falla, ABORTA la suite con un código de salida distinto (entorno no
 * disponible) en vez de gastar ~28 min produciendo 50 falsos rojos.
 *
 * Diagnóstico por capas en el mensaje de fallo:
 *   - 0 callbacks            → RED/INIT: el device no alcanza el backend o el SDK no arrancó.
 *   - onError / *Errors      → BACKEND/CONTENIDO: el backend rechazó el VOD o el ID está caído.
 *   - llegaron otros pero no
 *     onReady en el timeout   → BACKEND LENTO / stream degradado.
 *
 * @SmallTest para que NO entre en los filtros `-e size medium|large` de la suite —
 * solo se ejecuta cuando `run-tests.sh` lo invoca explícitamente por clase.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PreflightTest {

    // Timeout generoso: mayor que cualquier timeout de la suite (máx 25s) para no
    // abortar por un backend simplemente lento que la suite sí toleraría.
    private val PROBE_TIMEOUT = 30_000L

    @Test
    fun environment_isHealthy_backendResolvesToOnReady() {
        ActivityScenario.launch(DirectHlsActivity::class.java).use { scenario ->
            val captor = scenario.getCallbackCaptor()
            val ready = captor.awaitEvent("onReady", PROBE_TIMEOUT)

            if (ready) return@use

            val fired = captor.allEvents()
            val diagnosis = when {
                captor.hasEvent("onError") ||
                    captor.hasEvent("onPlaybackErrors") ||
                    captor.hasEvent("onEmbedErrors") ->
                    "BACKEND/CONTENIDO — el SDK reportó error al resolver el VOD. " +
                        "El backend de Mediastream o el ID de prueba están caídos."

                fired.isEmpty() ->
                    "RED/INIT — 0 callbacks en ${PROBE_TIMEOUT}ms. El device no alcanza el " +
                        "backend (sin red / DNS / firewall) o el SDK no inicializó."

                else ->
                    "BACKEND LENTO — llegaron callbacks pero no onReady en ${PROBE_TIMEOUT}ms. " +
                        "Backend degradado o stream lento."
            }

            Log.e("SDK_QA_FAIL", "╔═══════════════════════════════════════════════")
            Log.e("SDK_QA_FAIL", "║ PREFLIGHT FALLÓ — ENTORNO NO DISPONIBLE")
            Log.e("SDK_QA_FAIL", "║ $diagnosis")
            Log.e("SDK_QA_FAIL", "║ Recibidos: ${fired.ifEmpty { setOf("ninguno") }.joinToString()}")
            Log.e("SDK_QA_FAIL", "╚═══════════════════════════════════════════════")

            assertWithMessage(
                "PREFLIGHT — entorno no disponible, suite NO ejecutada.\n" +
                    "  $diagnosis\n" +
                    "  Recibidos: ${fired.ifEmpty { setOf("ninguno") }}"
            ).that(ready).isTrue()
        }
    }
}
