package com.example.sdk_qa.debug

import android.content.Context
import android.util.Log
import com.example.sdk_qa.core.BaseScenarioActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeSet

/**
 * Capa C — Session Export (SOLO debug).
 *
 * Al cerrar una Activity de escenario, escribe un JSON con el timeline completo de la sesión a
 * `getExternalFilesDir("sessions")/<scenario>-sdk<version>-<timestamp>.json`. Pensado para
 * **diff entre versiones del SDK** (10.0.7 vs 10.0.8): el JSON está **normalizado** para que el
 * diff resalte el comportamiento del SDK, no el ruido de sesión.
 *
 * Normalización (lo que cambia en cada sesión NO entra, o entra relativizado):
 *  - Tiempos → `offsetMs` relativo al primer evento (no timestamps absolutos).
 *  - Thread → "main" / "background" (no el nombre exacto del hilo, que varía por corrida).
 *  - REDACTADO/omitido: session IDs (pbId/sId/uId), CDN URL con tokens, ancho de banda y bitrate
 *    instantáneos (volátiles según red).
 *  - Claves de objeto serializadas en orden alfabético estable → diffs por línea limpios.
 *
 * Recuperar del device:  `adb pull /sdcard/Android/data/com.example.sdk_qa/files/sessions`
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object SessionExporter {

    private const val TAG = "SDK_QA"
    private const val SCHEMA = "sdkqa.session.v2"

    /** Grabador de eventos discretos del player; se fusiona con los callbacks del SDK al exportar. */
    private val recorder = SessionRecorder()

    /** Datos legibles solo mientras el player vive; se capturan en onPause (antes del release). */
    @Volatile private var liveVersion: String? = null
    @Volatile private var liveFormat: String? = null

    /** Engancha el recorder al ExoPlayer del escenario. Idempotente; true cuando quedó enganchado. */
    fun attachRecorder(activity: BaseScenarioActivity): Boolean =
        recorder.attachTo(activity.player?.msPlayer)

    /** Captura señales que requieren un player vivo (se pierden tras releasePlayer en onDestroy). */
    fun captureLive(activity: BaseScenarioActivity) {
        val p = activity.player ?: return
        runCatching { p.getVersion() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { liveVersion = it }
        runCatching { p.getCurrentVideoPlayingFormat() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }?.let { liveFormat = it }
    }

    /** Construye el JSON normalizado y lo escribe a disco (off-thread). Llamar en onDestroy. */
    fun export(context: Context, activity: BaseScenarioActivity) {
        val json = runCatching { buildJson(activity) }.getOrElse {
            Log.w(TAG, "SessionExport: no se pudo construir el JSON: ${it.message}")
            return
        }
        val scenario = runCatching { activity.getScenarioTitle() }.getOrDefault("scenario")
        val version = liveVersion ?: "unknown"
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "${StableJson.sanitizeForFilename(scenario)}-sdk${StableJson.sanitizeForFilename(version)}-$ts.json"
        val body = StableJson.pretty(json)

        // Reset para que la próxima sesión no herede señales de esta. El snapshot del recorder
        // ya quedó dentro de `json`, así que es seguro soltarlo aquí.
        liveVersion = null; liveFormat = null
        runCatching { recorder.detach(); recorder.reset() }

        val dir = context.getExternalFilesDir("sessions") ?: run {
            Log.w(TAG, "SessionExport: getExternalFilesDir nulo"); return
        }
        // Escritura SÍNCRONA: estamos en onDestroy y el proceso puede reciclarse justo después;
        // un Thread de fondo podría no alcanzar a escribir. El archivo es de pocos KB → el bloqueo
        // es despreciable y garantiza que el volcado se complete.
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, name)
            f.writeText(body)
            Log.d(TAG, "SessionExport → ${f.absolutePath}")
        }.onFailure { Log.w(TAG, "SessionExport: fallo al escribir: ${it.message}") }
    }

    private fun buildJson(activity: BaseScenarioActivity): JSONObject {
        // --- Fusión de las dos fuentes en un solo timeline, ordenado por el reloj compartido ---
        // (elapsedRealtime). Cada entrada: instante absoluto + JSON (source/type/thread/payload).
        // El offset se calcula tras ordenar, relativo al primer evento global.
        data class Entry(val t: Long, val json: JSONObject)
        val merged = ArrayList<Entry>()

        // Fuente 1 — callbacks del SDK (CallbackCaptor, ya timestampeados).
        for (e in activity.callbackCaptor.timelineSnapshot()) {
            merged.add(Entry(e.elapsedRealtimeMs, JSONObject()
                .put("source", "sdk")
                .put("type", e.name)
                .put("thread", if (e.onMainThread) "main" else "background")))
        }
        // Fuente 2 — eventos discretos de ExoPlayer (SessionRecorder).
        for (e in recorder.snapshot()) merged.add(Entry(e.elapsedRealtimeMs, e.json))

        merged.sortBy { it.t }
        val origin = merged.firstOrNull()?.t ?: 0L

        val timeline = JSONArray()
        val offMain = TreeSet<String>()
        for (e in merged) {
            e.json.put("offsetMs", e.t - origin)
            timeline.put(e.json)
            if (e.json.optString("thread") == "background") offMain.add(e.json.optString("type"))
        }

        val sdk = JSONObject().put("version", liveVersion ?: JSONObject.NULL)

        val content = JSONObject()
        runCatching {
            val cfg = activity.buildConfig()
            content.put("id", cfg.id ?: JSONObject.NULL)
                .put("type", cfg.type?.toString() ?: JSONObject.NULL)
                .put("environment", cfg.environment?.toString() ?: JSONObject.NULL)
        }

        val playback = JSONObject().put("format", liveFormat ?: JSONObject.NULL)

        // Métricas: contadores de COMPORTAMIENTO agregados (resumen estable, complementa el timeline).
        // Se omiten BW/bitrate instantáneos y ratio (dependen de red/duración de la corrida).
        val m = activity.playbackMetrics.snapshot(null)
        val metrics = JSONObject()
            .put("ttffMs", m.ttffMs)
            .put("rebufferCount", m.rebufferCount)
            .put("rebufferMs", m.rebufferMs)
            .put("bitrateSwitches", m.bitrateSwitches)
            .put("droppedFrames", m.droppedFrames)
            .put("loadErrorCount", m.loadErrorCount)
            .put("resolution", m.resolution)
            .put("videoCodec", m.videoCodec)

        return JSONObject()
            .put("schema", SCHEMA)
            .put("scenario", runCatching { activity.getScenarioTitle() }.getOrDefault(""))
            .put("sdk", sdk)
            .put("content", content)
            .put("playback", playback)
            .put("eventCount", timeline.length())
            .put("truncated", recorder.truncated)
            .put("timeline", timeline)
            .put("offMainThread", JSONArray(offMain.toList()))
            .put("metrics", metrics)
    }
}
