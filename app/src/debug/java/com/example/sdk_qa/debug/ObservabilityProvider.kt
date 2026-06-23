package com.example.sdk_qa.debug

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.sdk_qa.core.BaseScenarioActivity
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Observability bridge — SOLO debug.
 *
 * Expone el estado interno del SDK (que hoy solo es legible in-process por tests)
 * al harness de testing exploratorio externo, vía:
 *
 *   adb shell content query --uri content://com.example.sdk_qa.debug.observability/session
 *
 * Devuelve UNA fila con columna "json":
 *   {
 *     "activity": "VideoVodScenarioActivity",
 *     "scenarioTitle": "Video VOD",
 *     "events": ["playerViewReady","onReady","onPlay", ...],   // orden de llegada
 *     "offMainThread": [],                                       // eventos fuera del main
 *     "player": { "positionMs": 9000, "durationMs": 298000, "isPlaying": true, "playbackState": 3 },
 *     "config": { "id": "696b...", "type": "VOD", "environment": "PRODUCTION" }
 *   }
 *
 * Sin secretos: no expone tokens DRM, headers de licencia ni access tokens.
 *
 * Rastrea la BaseScenarioActivity en foreground vía ActivityLifecycleCallbacks
 * registrados en onCreate() — sin tocar BaseScenarioActivity (que ya es público:
 * `player`, `callbackCaptor`, `getScenarioTitle()`, `buildConfig()`).
 *
 * Vive en app/src/debug → ausente en release builds.
 */
class ObservabilityProvider : ContentProvider() {

    private var current = WeakReference<BaseScenarioActivity>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is BaseScenarioActivity) current = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                // Captura señales que solo viven con el player activo (format, versión del SDK)
                // antes de que onDestroy haga releasePlayer(). Capa C.
                if (activity is BaseScenarioActivity) SessionExporter.captureLive(activity)
                if (current.get() === activity) current = WeakReference<BaseScenarioActivity>(null)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                // Capa C — al cerrar el escenario, vuelca el timeline normalizado a un JSON diffable.
                val ctx = context
                if (activity is BaseScenarioActivity && ctx != null) {
                    SessionExporter.export(ctx, activity)
                }
            }
        })
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("json"))
        cursor.addRow(arrayOf(buildSessionJson().toString()))
        return cursor
    }

    private fun buildSessionJson(): JSONObject {
        val json = JSONObject()
        val activity = current.get()
        if (activity == null) {
            return json.put("activity", JSONObject.NULL)
                .put("note", "No hay BaseScenarioActivity en foreground")
        }

        json.put("activity", activity.javaClass.simpleName)
        runCatching { json.put("scenarioTitle", activity.getScenarioTitle()) }

        // Eventos (thread-safe, lectura directa)
        val captor = activity.callbackCaptor
        val events = captor.eventOrderSnapshot()
        json.put("events", JSONArray(events))
        json.put("offMainThread", JSONArray(
            captor.allEvents().filter { !captor.firedOnMainThread(it) }
        ))

        // Player state — DEBE leerse en el main thread
        json.put("player", readPlayerStateOnMain(activity))

        // Config declarada (id/type/env) — sin secretos
        runCatching {
            val cfg = activity.buildConfig()
            json.put("config", JSONObject()
                .put("id", cfg.id ?: JSONObject.NULL)
                .put("type", cfg.type?.toString() ?: JSONObject.NULL)
                .put("environment", cfg.environment?.toString() ?: JSONObject.NULL))
        }
        return json
    }

    /** Lee msPlayer en el main thread con un latch (timeout 1s). */
    private fun readPlayerStateOnMain(activity: BaseScenarioActivity): JSONObject {
        val out = JSONObject()
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val p = activity.player?.msPlayer
                if (p == null) {
                    out.put("available", false)
                } else {
                    out.put("available", true)
                        .put("positionMs", p.currentPosition)
                        .put("durationMs", p.duration)
                        .put("isPlaying", p.isPlaying)
                        .put("playbackState", p.playbackState)
                }
            } catch (e: Exception) {
                out.put("available", false).put("error", e.message ?: "exception")
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(1, TimeUnit.SECONDS)) out.put("available", false).put("error", "timeout")
        return out
    }

    // ContentProvider read-only — el resto no aplica.
    override fun getType(uri: Uri): String = "vnd.android.cursor.item/sdkqa-session"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
