package com.example.sdk_qa.utils

import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.debug.LayoutExporter
import com.google.common.truth.Truth.assertWithMessage
import org.json.JSONObject
import java.io.File

/**
 * Extensiones para la batería de regresión de layout de controles del player.
 *
 * Flujo: rotar a la orientación bajo prueba, pausar (surface idle → sin cuelgue de uiautomator),
 * revelar la control bar del SDK con tap y LEER los bounds en el mismo loop (sin gap de auto-hide),
 * y o bien CAPTURAR la baseline o COMPARAR contra la baseline empaquetada como asset de test
 * (`androidTest/assets/layout-baselines/<scenario>-<orient>.layout.json`).
 *
 * Orientación: el bug "controles más arriba" es casi imperceptible en portrait y pronunciado en
 * LANDSCAPE, así que la batería mide por orientación (sufijo `-port` / `-land` en la baseline).
 */

/** Tolerancia en fracción del contenedor (1%). */
const val LAYOUT_TOLERANCE = 0.01

private const val REVEAL_TIMEOUT_MS = 10_000L
private const val POLL_MS = 300L
private const val ROTATE_SETTLE_MS = 1_500L

/** Anchors que deben estar visibles para considerar la barra "revelada y medible". */
private val REVEAL_MARKERS = listOf("exo_progress", "exo_play_pause", "btn_settings")

enum class Orient(val suffix: String, val activityInfo: Int) {
    PORT("port", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LAND("land", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
}

/** Fija la orientación de la Activity y espera a que el layout se estabilice. */
fun <T : BaseScenarioActivity> ActivityScenario<T>.setOrientation(orient: Orient) {
    onActivity { it.requestedOrientation = orient.activityInfo }
    Thread.sleep(ROTATE_SETTLE_MS)
}

/**
 * Pausa el player, revela la control bar con tap y devuelve los anchors medidos **en el mismo
 * instante** en que la barra está visible (sin gap de auto-hide). Reintenta porque el tap togglea:
 * si una medición sale con los marcadores ocultos, vuelve a tap y mide de nuevo. Si nunca logra
 * revelar, devuelve la última medición (el assert lo reportará como violación real).
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.revealAndReadAnchors(): List<LayoutExporter.Anchor> {
    onActivity { runCatching { it.player?.msPlayer?.pause() } }   // surface idle
    val d = uiDevice()
    var last: List<LayoutExporter.Anchor> = emptyList()
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < REVEAL_TIMEOUT_MS) {
        // Medir primero: si ya está revelada (autoplay/anterior), no tapeamos de más.
        last = currentAnchors()
        if (barRevealed(last)) return last
        // No revelada → tap para mostrar la barra y volver a medir.
        d.click(d.displayWidth / 2, d.displayHeight / 2)
        Thread.sleep(POLL_MS)
        last = currentAnchors()
        if (barRevealed(last)) return last
    }
    return last
}

private fun barRevealed(anchors: List<LayoutExporter.Anchor>): Boolean {
    val byName = anchors.associateBy { it.name }
    return REVEAL_MARKERS.all { byName[it]?.visible == true }
}

/** Lee los anchors normalizados en el main thread. */
fun <T : BaseScenarioActivity> ActivityScenario<T>.currentAnchors(): List<LayoutExporter.Anchor> {
    var anchors: List<LayoutExporter.Anchor> = emptyList()
    onActivity { anchors = LayoutExporter.readAnchors(it) }
    return anchors
}

/**
 * Modo CAPTURE: escribe la baseline de layout al device (getExternalFilesDir/layouts) usando los
 * anchors ya medidos con la barra revelada. Naming `<scenario>-<orient>-sdk<version>.layout.json`.
 */
fun <T : BaseScenarioActivity> ActivityScenario<T>.captureLayoutBaseline(
    scenarioKey: String,
    orient: Orient,
    anchors: List<LayoutExporter.Anchor>
): File? {
    val includeDiscovered =
        InstrumentationRegistry.getArguments().getString("discoverViews") == "true"
    var file: File? = null
    onActivity { activity ->
        val discovered = if (includeDiscovered) LayoutExporter.discoverVisibleViews(activity) else emptyList()
        file = LayoutExporter.capture(activity, activity, "$scenarioKey-${orient.suffix}", anchors, discovered)
    }
    return file
}

/**
 * Compara los anchors medidos contra la baseline empaquetada como asset de test y devuelve la lista
 * de desviaciones (vacía = OK). No asserta — el caller acumula varias orientaciones y asserta al
 * final, para reportar port Y land en un solo fallo.
 */
fun collectLayoutViolations(
    scenarioKey: String,
    orient: Orient,
    anchors: List<LayoutExporter.Anchor>
): List<String> {
    val key = "$scenarioKey-${orient.suffix}"
    val baseline = loadBaselineAsset(key)
    val current = anchors.associateBy { it.name }
    val baselineAnchors = baseline.getJSONArray("anchors")
    val violations = mutableListOf<String>()

    for (i in 0 until baselineAnchors.length()) {
        val b = baselineAnchors.getJSONObject(i)
        val name = b.getString("name")
        if (!b.getBoolean("present")) continue

        val c = current[name]
        if (c == null || !c.present) {
            violations.add("[$key] $name: ausente en la versión actual (baseline lo tenía)")
            continue
        }
        if (b.getBoolean("visible") != c.visible) {
            violations.add("[$key] $name: visible baseline=${b.getBoolean("visible")} actual=${c.visible}")
        }
        checkCoord("[$key] $name", "relTop", b, c.relTop)?.let { violations.add(it) }
        checkCoord("[$key] $name", "relBottom", b, c.relBottom)?.let { violations.add(it) }
        checkCoord("[$key] $name", "relLeft", b, c.relLeft)?.let { violations.add(it) }
        checkCoord("[$key] $name", "relRight", b, c.relRight)?.let { violations.add(it) }
    }
    return violations
}

private fun checkCoord(name: String, key: String, baseline: JSONObject, actual: Double): String? {
    if (!baseline.has(key)) return null
    val base = baseline.getDouble(key)
    val delta = actual - base
    if (kotlin.math.abs(delta) <= LAYOUT_TOLERANCE) return null
    val dir = when (key) {
        "relTop", "relBottom" -> if (delta < 0) " — desplazado hacia ARRIBA" else " — desplazado hacia ABAJO"
        else -> ""
    }
    return "$name $key baseline=$base actual=$actual Δ=${"%+.3f".format(delta)} (tol=$LAYOUT_TOLERANCE)$dir"
}

private fun loadBaselineAsset(key: String): JSONObject {
    val ctx = InstrumentationRegistry.getInstrumentation().context // assets del APK de test
    val path = "layout-baselines/$key.layout.json"
    val text = runCatching { ctx.assets.open(path).bufferedReader().use { it.readText() } }
        .getOrElse {
            throw AssertionError(
                "No existe la baseline '$path' en androidTest/assets. " +
                    "Capturar primero con captureBaseline=true."
            )
        }
    return JSONObject(text)
}
