package com.example.sdk_qa.core

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Colector de métricas de calidad de reproducción (QoE) — el "debajo del capó" del SDK
 * que ni los callbacks del SDK ni el árbol de vistas de Android exponen.
 *
 * Se engancha al [ExoPlayer] interno (`player.msPlayer`) vía [AnalyticsListener] y acumula
 * las métricas que las empresas referentes de streaming (Mux, Brightcove, THEOplayer) miden
 * para certificar un player:
 *
 *  - **TTFF** (Time To First Frame): ms desde que se adjunta el colector (≈ inicio de carga)
 *    hasta el primer frame renderizado. Estándar industria: < 2s VOD, < 4s Live.
 *  - **Rebuffering**: número de stalls + tiempo total + ratio (rebufferMs / watchMs).
 *    Estándar industria: ratio < 0.5%.
 *  - **Bitrate actual** y **switches de bitrate** (cambios ABR). Estándar: < 3 switches/min.
 *  - **Dropped frames**: frames de video descartados (indicador de stress de decodificación).
 *  - **Ancho de banda medido** por el estimador de ExoPlayer.
 *  - **Buffer health**: segundos de contenido buffereados por delante (polled).
 *  - **Errores de carga** no fatales (segmentos/manifest que el SDK reintenta en silencio).
 *
 * Threading: los callbacks de [AnalyticsListener] llegan en el thread de aplicación de ExoPlayer
 * (normalmente el main thread), pero los acumuladores usan tipos atómicos/volatile para que
 * [snapshot] pueda leerse desde el statusRunnable o desde un test sin condiciones de carrera.
 *
 * Reutilizable: [attachTo] re-engancha de forma segura cuando la instancia interna cambia
 * (p. ej. al activarse Chromecast, donde `msPlayer` pasa a ser un CastPlayer no-ExoPlayer).
 */
@UnstableApi
class PlaybackMetrics : AnalyticsListener {

    /** Instantánea inmutable de las métricas — segura para pasar a la UI o a un test. */
    data class Snapshot(
        val ttffMs: Long,                 // -1 si aún no se renderizó el primer frame
        val rebufferCount: Int,
        val rebufferMs: Long,
        val rebufferRatio: Double,        // rebufferMs / (rebufferMs + watchMs)
        val currentBitrateBps: Int,       // -1 si desconocido
        val bitrateSwitches: Int,
        val measuredBandwidthBps: Long,   // -1 si aún no hay muestra
        val droppedFrames: Int,
        val resolution: String,           // "1280x720" o "—"
        val videoCodec: String,           // "h264" o "—"
        val bufferHealthMs: Long,         // contenido buffereado por delante de la posición
        val loadErrorCount: Int,
        val lastLoadError: String?
    )

    // --- Acumuladores (escritos por AnalyticsListener, leídos por snapshot) ---
    @Volatile private var attachTimeMs: Long = -1L
    @Volatile private var firstFrameMs: Long = -1L
    @Volatile private var currentBitrate: Int = -1
    @Volatile private var bitrateSwitchCount: Int = 0
    @Volatile private var lastVideoFormatBitrate: Int = -1
    private val droppedFrameCount = AtomicLong(0)
    @Volatile private var measuredBandwidth: Long = -1L
    @Volatile private var resolution: String = "—"
    @Volatile private var videoCodec: String = "—"
    @Volatile private var loadErrors: Int = 0
    @Volatile private var lastError: String? = null

    // Rebuffering: contamos stalls SOLO después del primer frame (el buffering inicial
    // es startup, no rebuffer). watchMs acumula tiempo de reproducción real.
    @Volatile private var rebufferCount: Int = 0
    @Volatile private var rebufferMs: Long = 0L
    @Volatile private var rebufferStartMs: Long = -1L
    @Volatile private var watchMs: Long = 0L
    @Volatile private var playStartMs: Long = -1L

    private var attached: ExoPlayer? = null

    /**
     * Engancha el colector al ExoPlayer interno del SDK. Idempotente y seguro frente a swaps:
     * si la instancia es la misma, no hace nada; si cambió (Cast), desengancha la anterior.
     * Si [player] no es un ExoPlayer (CastPlayer durante Chromecast), simplemente lo ignora.
     *
     * @return true si quedó enganchado a un ExoPlayer.
     */
    fun attachTo(player: Player?): Boolean {
        val exo = player as? ExoPlayer
        if (exo === attached) return attached != null
        attached?.let { runCatching { it.removeAnalyticsListener(this) } }
        attached = exo
        if (exo == null) return false
        exo.addAnalyticsListener(this)
        if (attachTimeMs < 0) attachTimeMs = nowMs()
        return true
    }

    /** Desengancha y resetea — llamar en onDestroy. */
    fun detach() {
        attached?.let { runCatching { it.removeAnalyticsListener(this) } }
        attached = null
    }

    /** Lee el estado instantáneo del player y lo combina con los acumuladores. */
    fun snapshot(player: Player?): Snapshot {
        val bufferHealth = player?.let {
            (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L)
        } ?: 0L
        val watch = currentWatchMs()
        val ratio = if (watch + rebufferMs > 0) rebufferMs.toDouble() / (watch + rebufferMs) else 0.0
        return Snapshot(
            ttffMs = if (firstFrameMs >= 0 && attachTimeMs >= 0) firstFrameMs - attachTimeMs else -1L,
            rebufferCount = rebufferCount,
            rebufferMs = rebufferMs,
            rebufferRatio = ratio,
            currentBitrateBps = currentBitrate,
            bitrateSwitches = bitrateSwitchCount,
            measuredBandwidthBps = measuredBandwidth,
            droppedFrames = droppedFrameCount.get().toInt(),
            resolution = resolution,
            videoCodec = videoCodec,
            bufferHealthMs = bufferHealth,
            loadErrorCount = loadErrors,
            lastLoadError = lastError
        )
    }

    /** Limpia todos los acumuladores (entre reloads o entre tests). */
    fun reset() {
        attachTimeMs = nowMs()
        firstFrameMs = -1L
        currentBitrate = -1
        bitrateSwitchCount = 0
        lastVideoFormatBitrate = -1
        droppedFrameCount.set(0)
        measuredBandwidth = -1L
        resolution = "—"
        videoCodec = "—"
        loadErrors = 0
        lastError = null
        rebufferCount = 0
        rebufferMs = 0L
        rebufferStartMs = -1L
        watchMs = 0L
        playStartMs = -1L
    }

    // ----------------------------------------------------------------------------
    // AnalyticsListener
    // ----------------------------------------------------------------------------

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        if (firstFrameMs < 0) firstFrameMs = nowMs()
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
    ) {
        if (format.bitrate != Format.NO_VALUE) {
            currentBitrate = format.bitrate
            // Cada cambio real de bitrate (tras el primero) cuenta como un switch ABR.
            if (lastVideoFormatBitrate != -1 && format.bitrate != lastVideoFormatBitrate) {
                bitrateSwitchCount++
            }
            lastVideoFormatBitrate = format.bitrate
        }
        if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE) {
            resolution = "${format.width}x${format.height}"
        }
        format.codecs?.let { videoCodec = it.substringBefore('.') }
            ?: format.sampleMimeType?.let { videoCodec = it.substringAfter('/') }
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        droppedFrameCount.addAndGet(droppedFrames.toLong())
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long
    ) {
        measuredBandwidth = bitrateEstimate
    }

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int
    ) {
        when (state) {
            Player.STATE_BUFFERING -> {
                // Solo es rebuffer si ya empezó a verse contenido (post primer frame).
                if (firstFrameMs >= 0 && rebufferStartMs < 0) {
                    rebufferStartMs = nowMs()
                    rebufferCount++
                }
            }
            Player.STATE_READY, Player.STATE_ENDED -> {
                if (rebufferStartMs >= 0) {
                    rebufferMs += nowMs() - rebufferStartMs
                    rebufferStartMs = -1L
                }
            }
        }
    }

    override fun onIsPlayingChanged(
        eventTime: AnalyticsListener.EventTime,
        isPlaying: Boolean
    ) {
        if (isPlaying) {
            playStartMs = nowMs()
        } else if (playStartMs >= 0) {
            watchMs += nowMs() - playStartMs
            playStartMs = -1L
        }
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        if (wasCanceled) return
        loadErrors++
        lastError = "${error.javaClass.simpleName}: ${error.message?.take(80)}"
    }

    // ----------------------------------------------------------------------------

    /** watchMs incluyendo el segmento de reproducción en curso. */
    private fun currentWatchMs(): Long =
        if (playStartMs >= 0) watchMs + (nowMs() - playStartMs) else watchMs

    private fun nowMs() = android.os.SystemClock.elapsedRealtime()

    companion object {
        /** Formatea bps a una etiqueta legible: "2.4 Mbps", "640 kbps", "—". */
        fun formatBitrate(bps: Int): String = formatBitrate(bps.toLong())

        fun formatBitrate(bps: Long): String = when {
            bps < 0 -> "—"
            bps >= 1_000_000 -> "${(bps / 100_000L) / 10.0} Mbps"
            bps >= 1_000 -> "${(bps / 1_000L)} kbps"
            else -> "$bps bps"
        }

        /** Formatea un ratio [0,1] a porcentaje con 2 decimales: "0.42%". */
        fun formatRatio(ratio: Double): String =
            "${(ratio * 10_000).roundToInt() / 100.0}%"
    }
}
