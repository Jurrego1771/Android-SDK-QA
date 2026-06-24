package com.example.sdk_qa.debug

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.example.sdk_qa.core.BaseScenarioActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Batería de regresión de layout — lectura/normalización/serialización de la posición de los
 * controles del player del SDK (SOLO debug).
 *
 * Gemelo de [SessionExporter] pero sobre el LAYOUT (bounds) en vez del timeline de eventos: detecta
 * regresiones de posición de los controles entre versiones del SDK (bug: con 10.0.8-alpha06 los
 * controles salen "ligeramente más arriba").
 *
 * Mecanismo: el SDK infla sus vistas `exo_*` DENTRO del PlayerView que la app le pasa, así que son
 * hijas reales del árbol de vistas de la app y se traversan desde el proceso (sin uiautomator → sin
 * el cuelgue "could not get idle state" con video). Se leen en el MAIN thread con el player pausado
 * y la control bar revelada.
 *
 * Normalización: coordenadas relativas al `player_container` (ya inseteado por applyWindowInsets),
 * cuantizadas a 3 decimales para absorber sub-píxel/densidad. El diff resalta el offset de posición.
 *
 * Recuperar baseline del device:  `adb pull /sdcard/Android/data/com.example.sdk_qa/files/layouts`
 */
object LayoutExporter {

    private const val TAG = "SDK_QA"
    private const val SCHEMA = "sdkqa.layout.v1"
    private const val QUANTIZE = 1000.0   // 3 decimales (~2px en 1080p)

    /**
     * IDs de los controles REALES del SDK Mediastream (descubiertos vía discoverVisibleViews —
     * el SDK usa su propio layout custom, NO la control bar `exo_*` estándar de media3). Best-effort:
     * si un id no existe en un escenario, el anchor queda `present=false`.
     */
    private val ANCHOR_IDS = listOf(
        "exo_content_frame",        // área de video (referencia de encuadre)
        "txt_title",                // título (arriba)
        "btn_back_ten",             // rewind 10s (centro-izq)
        "exo_play_pause",           // play/pause (centro)
        "btn_fwd_ten",              // forward 10s (centro-der)
        "seek_bar_layout",          // contenedor de la barra de progreso
        "dvr_timeline_container",   // timeline DVR (solo livedvr)
        "exo_progress",             // barra de progreso (anchor clave del bug)
        "exo_position", "exo_duration",
        "btn_settings",             // ajustes (abajo-der)
        "btn_fullscreen_on",        // fullscreen (abajo-der)
        "exo_subtitle",             // CC (abajo-izq)
        "media_route_button"        // botón de cast (si castAvailable)
    )

    /** Bounds normalizados de un control (fracciones [0..1] relativas al contenedor base). */
    data class Anchor(
        val name: String,
        val present: Boolean,
        val visible: Boolean,
        val relLeft: Double = -1.0,
        val relTop: Double = -1.0,
        val relRight: Double = -1.0,
        val relBottom: Double = -1.0
    )

    /**
     * Lee y normaliza los bounds de los controles del SDK. **Debe llamarse en el MAIN thread**
     * (accede a vistas), con la control bar ya revelada y el player pausado.
     */
    fun readAnchors(activity: BaseScenarioActivity): List<Anchor> {
        val res = activity.resources
        val root = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView

        // Indexar por entryName. Puede haber VARIAS vistas con el mismo id (una oculta en el
        // layout base, otra activa al revelar la barra): preferimos la VISIBLE con área > 0.
        val byName = HashMap<String, View>()
        walk(root) { v ->
            if (v.id == View.NO_ID) return@walk
            val name = runCatching { res.getResourceEntryName(v.id) }.getOrNull() ?: return@walk
            val cur = byName[name]
            val better = cur == null ||
                (isUsable(v) && !isUsable(cur))   // upgrade: oculta → visible-con-área
            if (better) byName[name] = v
        }

        // Base de normalización: player_container (app) > playerView (SDK interno) > content view.
        val base = byName["player_container"] ?: byName["playerView"] ?: root
        val baseLoc = IntArray(2).also { base.getLocationOnScreen(it) }
        val baseLeft = baseLoc[0]
        val baseTop = baseLoc[1]
        val baseW = base.width.coerceAtLeast(1)
        val baseH = base.height.coerceAtLeast(1)

        fun q(v: Double) = Math.round(v * QUANTIZE) / QUANTIZE

        return ANCHOR_IDS.sorted().map { name ->
            val v = byName[name]
            if (v == null) {
                Anchor(name, present = false, visible = false)
            } else {
                val loc = IntArray(2).also { v.getLocationOnScreen(it) }
                Anchor(
                    name = name,
                    present = true,
                    visible = v.isShown,
                    relLeft = q((loc[0] - baseLeft).toDouble() / baseW),
                    relTop = q((loc[1] - baseTop).toDouble() / baseH),
                    relRight = q((loc[0] + v.width - baseLeft).toDouble() / baseW),
                    relBottom = q((loc[1] + v.height - baseTop).toDouble() / baseH)
                )
            }
        }
    }

    private fun walk(v: View, fn: (View) -> Unit) {
        fn(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), fn)
    }

    /** Vista "usable" para medir: visible en pantalla y con área no nula. */
    private fun isUsable(v: View) = v.isShown && v.width > 0 && v.height > 0

    /**
     * Diagnóstico: TODOS los views con id resuelto, visibles y con área > 0, normalizados igual que
     * los anchors. Sirve para descubrir los resource-ids reales de los controles del SDK (que NO
     * siempre son los `exo_*` estándar). Se incluye en el JSON bajo `discovered`.
     */
    fun discoverVisibleViews(activity: BaseScenarioActivity): List<Anchor> {
        val res = activity.resources
        val root = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
        val base = findBase(activity)
        val baseLoc = IntArray(2).also { base.getLocationOnScreen(it) }
        val baseW = base.width.coerceAtLeast(1); val baseH = base.height.coerceAtLeast(1)
        fun q(v: Double) = Math.round(v * QUANTIZE) / QUANTIZE
        val out = ArrayList<Anchor>()
        walk(root) { v ->
            if (v.id != View.NO_ID && v.isShown && v.width > 0 && v.height > 0) {
                val name = runCatching { res.getResourceEntryName(v.id) }.getOrNull() ?: return@walk
                val loc = IntArray(2).also { v.getLocationOnScreen(it) }
                out.add(Anchor(name, true, true,
                    q((loc[0] - baseLoc[0]).toDouble() / baseW),
                    q((loc[1] - baseLoc[1]).toDouble() / baseH),
                    q((loc[0] + v.width - baseLoc[0]).toDouble() / baseW),
                    q((loc[1] + v.height - baseLoc[1]).toDouble() / baseH)))
            }
        }
        return out.sortedBy { it.relTop }
    }

    private fun findBase(activity: BaseScenarioActivity): View {
        val res = activity.resources
        val root = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
        var base: View? = null
        walk(root) { v ->
            if (base == null && v.id != View.NO_ID &&
                runCatching { res.getResourceEntryName(v.id) }.getOrNull() == "player_container") base = v
        }
        return base ?: root
    }

    private fun anchorsToJson(anchors: List<Anchor>): JSONArray {
        val arr = JSONArray()
        for (a in anchors) {
            val o = JSONObject().put("name", a.name).put("present", a.present).put("visible", a.visible)
            if (a.present) o.put("relLeft", a.relLeft).put("relTop", a.relTop)
                .put("relRight", a.relRight).put("relBottom", a.relBottom)
            arr.put(o)
        }
        return arr
    }

    /** Construye el JSON normalizado de layout (claves ordenadas vía [StableJson]). */
    fun buildJson(
        activity: BaseScenarioActivity,
        scenarioKey: String,
        anchors: List<Anchor>,
        discovered: List<Anchor> = emptyList()
    ): JSONObject {
        val dm = activity.resources.displayMetrics
        val container = JSONObject()
            .put("wPx", dm.widthPixels)
            .put("hPx", dm.heightPixels)
            .put("density", dm.density.toDouble())

        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("scenario", scenarioKey)
            .put("sdk", JSONObject().put("version", readVersion(activity) ?: JSONObject.NULL))
            .put("device", android.os.Build.MODEL)
            .put("container", container)
            .put("anchors", anchorsToJson(anchors))
        if (discovered.isNotEmpty()) json.put("discovered", anchorsToJson(discovered))
        return json
    }

    private fun readVersion(activity: BaseScenarioActivity): String? =
        runCatching { activity.player?.getVersion() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * Modo CAPTURE (baseline): escribe el JSON de layout a `getExternalFilesDir("layouts")`.
     * Naming `<scenario>-sdk<version>.layout.json` (sin timestamp: baseline estable). Devuelve el
     * File escrito, o null si falló.
     */
    fun capture(
        context: Context,
        activity: BaseScenarioActivity,
        scenarioKey: String,
        anchors: List<Anchor>,
        discovered: List<Anchor> = emptyList()
    ): File? {
        val json = runCatching { buildJson(activity, scenarioKey, anchors, discovered) }.getOrElse {
            Log.w(TAG, "LayoutExport: no se pudo construir JSON: ${it.message}"); return null
        }
        val version = readVersion(activity) ?: "unknown"
        val name = "${StableJson.sanitizeForFilename(scenarioKey)}-sdk${StableJson.sanitizeForFilename(version)}.layout.json"
        val dir = context.getExternalFilesDir("layouts") ?: run {
            Log.w(TAG, "LayoutExport: getExternalFilesDir nulo"); return null
        }
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, name)
            f.writeText(StableJson.pretty(json))
            Log.d(TAG, "LayoutExport → ${f.absolutePath}")
            f
        }.getOrElse { Log.w(TAG, "LayoutExport: fallo al escribir: ${it.message}"); null }
    }
}
