package com.example.sdk_qa.debug

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import org.json.JSONObject
import java.io.IOException
import java.util.Collections

/**
 * Capa C — grabador de eventos discretos del player (SOLO debug).
 *
 * Un [AnalyticsListener] SEPARADO del [com.example.sdk_qa.core.PlaybackMetrics] de producción: se
 * engancha en paralelo al mismo ExoPlayer y registra cada evento relevante (cambio de bitrate,
 * inicio/fin de buffering, error de carga, error fatal, frame inicial, dropped frames) con su
 * instante monotónico. A diferencia de PlaybackMetrics —que AGREGA en contadores— esto GRABA el
 * stream cronológico, para reconstruir/diffear la sesión.
 *
 * Garantías de seguridad (no debe afectar reproducción ni tests):
 *  - Vive en `src/debug` → ausente en release.
 *  - NO toca PlaybackMetrics ni el SDK; solo añade un listener read-only.
 *  - Cada override está envuelto en runCatching → un bug aquí nunca propaga a ExoPlayer.
 *  - Lista acotada ([maxEvents]) → sin crecimiento ilimitado en sesiones largas (live).
 *
 * Reloj: `SystemClock.elapsedRealtime()` absoluto (monotónico). El [SessionExporter] fusiona estos
 * eventos con los callbacks del SDK ([com.example.sdk_qa.core.CallbackCaptor]) usando ese mismo
 * reloj y relativiza a un único origen.
 */
@UnstableApi
class SessionRecorder : AnalyticsListener {

    /** Evento crudo: instante monotónico + JSON ya con source/type/thread + payload. */
    data class Event(val elapsedRealtimeMs: Long, val json: JSONObject)

    private val events = Collections.synchronizedList(mutableListOf<Event>())
    @Volatile private var attached: ExoPlayer? = null
    @Volatile var truncated = false
        private set

    private val maxEvents = 2000
    private var lastBitrate = -1
    private var firstFrame = false
    private var bufferingStartMs = -1L

    /** Engancha al ExoPlayer interno. Idempotente; re-engancha si la instancia cambió (Cast). */
    fun attachTo(player: Player?): Boolean {
        val exo = player as? ExoPlayer
        if (exo === attached) return attached != null
        attached?.let { runCatching { it.removeAnalyticsListener(this) } }
        attached = exo
        if (exo == null) return false
        runCatching { exo.addAnalyticsListener(this) }
        return true
    }

    fun detach() {
        attached?.let { runCatching { it.removeAnalyticsListener(this) } }
        attached = null
    }

    /** Snapshot inmutable de los eventos grabados, en orden de llegada. */
    fun snapshot(): List<Event> = synchronized(events) { events.toList() }

    fun reset() {
        synchronized(events) { events.clear() }
        truncated = false
        lastBitrate = -1
        firstFrame = false
        bufferingStartMs = -1L
        // No detach aquí: el ciclo de attach/detach lo maneja quien lo posee.
    }

    // ------------------------------------------------------------------------
    // Registro acotado y a prueba de fallos
    // ------------------------------------------------------------------------

    private inline fun safe(block: () -> Unit) { runCatching { block() } }

    private fun record(type: String, build: JSONObject.() -> Unit = {}) {
        if (events.size >= maxEvents) { truncated = true; return }
        val o = JSONObject()
            .put("source", "exo")
            .put("type", type)
            .put("thread", if (Looper.myLooper() == Looper.getMainLooper()) "main" else "background")
        o.build()
        events.add(Event(SystemClock.elapsedRealtime(), o))
    }

    // ------------------------------------------------------------------------
    // AnalyticsListener — solo los eventos que importan para comparar versiones
    // (se omite onBandwidthEstimate a propósito: dispara por chunk → ruido).
    // ------------------------------------------------------------------------

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long
    ) = safe {
        if (!firstFrame) { firstFrame = true; record("first_frame") }
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
    ) = safe {
        val br = if (format.bitrate != Format.NO_VALUE) format.bitrate else -1
        val res = if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE)
            "${format.width}x${format.height}" else null
        val codec = format.codecs?.substringBefore('.') ?: format.sampleMimeType?.substringAfter('/')
        val isSwitch = lastBitrate != -1 && br != lastBitrate
        record("video_format") {
            put("bitrateBps", br)
            res?.let { put("resolution", it) }
            codec?.let { put("codec", it) }
            put("abrSwitch", isSwitch)
        }
        if (br != -1) lastBitrate = br
    }

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime, state: Int
    ) = safe {
        when (state) {
            Player.STATE_BUFFERING -> {
                bufferingStartMs = SystemClock.elapsedRealtime()
                // initial=true si aún no se vio el primer frame (buffering de arranque, no rebuffer).
                record("buffering_start") { put("initial", !firstFrame) }
            }
            Player.STATE_READY, Player.STATE_ENDED -> {
                if (bufferingStartMs >= 0) {
                    val dur = SystemClock.elapsedRealtime() - bufferingStartMs
                    record("buffering_end") { put("durMs", dur) }
                    bufferingStartMs = -1L
                }
            }
        }
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long
    ) = safe {
        record("dropped_frames") { put("count", droppedFrames) }
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) = safe {
        if (wasCanceled) return@safe
        record("load_error") {
            put("error", "${error.javaClass.simpleName}: ${error.message?.take(80) ?: ""}")
            // HTTP status (gold para el bug de errores 404 silenciados) — sin la URL (tokens).
            (error as? HttpDataSource.InvalidResponseCodeException)?.let { put("httpStatus", it.responseCode) }
        }
    }

    override fun onPlayerError(
        eventTime: AnalyticsListener.EventTime, error: PlaybackException
    ) = safe {
        record("player_error") {
            put("code", error.errorCodeName)
            put("message", error.message?.take(80) ?: "")
        }
    }
}
