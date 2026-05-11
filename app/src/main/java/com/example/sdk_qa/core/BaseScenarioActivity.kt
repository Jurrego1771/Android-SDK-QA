package com.example.sdk_qa.core

import am.mediastre.mediastreamplatformsdkandroid.MediastreamMiniPlayerConfig
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayer
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerCallback
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sdk_qa.databinding.ActivityBaseScenarioBinding
import com.google.ads.interactivemedia.v3.api.AdError
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Base para todos los escenarios QA con debug panel opcional.
 *
 * - Player ocupa toda la pantalla (SDK UI sin obstrucción)
 * - FAB 🐛 semitransparente en esquina superior-derecha
 * - Bottom sheet deslizable con log de callbacks + botones de acción
 * - Por defecto el panel está oculto — tap FAB para abrir/cerrar
 */
abstract class BaseScenarioActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityBaseScenarioBinding
    var player: MediastreamPlayer? = null
        internal set

    /** Captura eventos de callbacks del SDK. Disponible para tests instrumentados. */
    val callbackCaptor = CallbackCaptor()

    private val logAdapter = LogEntryAdapter()
    private lateinit var sheetBehavior: BottomSheetBehavior<*>
    private val statusHandler = Handler(Looper.getMainLooper())
    private val timestampFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val statusRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            statusHandler.postDelayed(this, 500)
        }
    }

    // -------------------------------------------------------------------------
    // API abstracta para subclases
    // -------------------------------------------------------------------------

    abstract fun getScenarioTitle(): String
    abstract fun buildConfig(): MediastreamPlayerConfig
    open fun setupActionButtons(container: LinearLayout) {}
    open fun onPlayerCreated() {}

    /** Hook: se llama cuando llega nextEpisodeIncoming — sobreescribir en Episode Custom */
    open fun onNextEpisodeIncoming(nextEpisodeId: String) {}

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityBaseScenarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDebugSheet()
        setupFab()
        initPlayer()
        setupActionButtons(binding.actionButtons)
    }

    override fun onDestroy() {
        super.onDestroy()
        statusHandler.removeCallbacks(statusRunnable)
        player?.releasePlayer()
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && player?.handleTVKeyEvent(event.keyCode, event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { player?.startPiP() } catch (_: Exception) { /* PiP not supported */ }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        player?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun setupDebugSheet() {
        sheetBehavior = BottomSheetBehavior.from(binding.debugSheet).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            isHideable = true
            skipCollapsed = true
        }

        binding.recyclerLog.apply {
            layoutManager = LinearLayoutManager(this@BaseScenarioActivity).also {
                it.stackFromEnd = true
            }
            adapter = logAdapter
            itemAnimator = null
        }

        binding.btnClearLog.setOnClickListener { logAdapter.clear() }
    }

    private fun setupFab() {
        binding.fabDebug.setOnClickListener {
            sheetBehavior.state = when (sheetBehavior.state) {
                BottomSheetBehavior.STATE_HIDDEN -> BottomSheetBehavior.STATE_EXPANDED
                else -> BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    private fun initPlayer() {
        player = MediastreamPlayer(
            this,
            buildConfig(),
            binding.playerContainer,
            binding.playerView,
            supportFragmentManager
        )
        player?.addPlayerCallback(buildLoggingCallback())
        onPlayerCreated()
        statusHandler.post(statusRunnable)
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    private fun refreshStatus() {
        val exo = player?.msPlayer ?: run {
            binding.tvStatus.text = "⬛  --:-- / --:--"
            return
        }
        val icon = when {
            exo.isPlaying -> "▶"
            exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING -> "⟳"
            exo.playbackState == androidx.media3.common.Player.STATE_ENDED -> "⏹"
            else -> "⏸"
        }
        val pos = exo.currentPosition / 1000
        val dur = if (exo.duration > 0) exo.duration / 1000 else -1L
        val durStr = if (dur > 0) fmt(dur) else "--:--"
        binding.tvStatus.text = "$icon  ${fmt(pos)} / $durStr   ${getScenarioTitle()}"
    }

    private fun fmt(s: Long) = "%02d:%02d".format(s / 60, s % 60)

    // -------------------------------------------------------------------------
    // Helpers para subclases
    // -------------------------------------------------------------------------

    protected fun addButton(container: LinearLayout, label: String, action: () -> Unit) {
        val btn = com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) }
        }
        container.addView(btn)
    }

    protected fun log(
        event: String,
        detail: String? = null,
        category: LogCategory = LogCategory.LIFECYCLE
    ) {
        Log.d("SDK_QA", "[$category] $event${if (detail != null) " — $detail" else ""}")
        runOnUiThread {
            logAdapter.addEntry(
                LogEntry(
                    timestamp = timestampFmt.format(Date()),
                    event = event,
                    detail = detail,
                    category = category
                )
            )
            binding.recyclerLog.scrollToPosition(logAdapter.itemCount - 1)
        }
    }

    // -------------------------------------------------------------------------
    // Logging callback — implementa todos los métodos del SDK
    // -------------------------------------------------------------------------

    private fun buildLoggingCallback() = object : MediastreamPlayerCallback {

        override fun playerViewReady(msplayerView: PlayerView?) {
            callbackCaptor.recordEvent("playerViewReady")
            log("playerViewReady", category = LogCategory.LIFECYCLE)
        }

        override fun onReady() {
            callbackCaptor.recordEvent("onReady")
            log("onReady", category = LogCategory.LIFECYCLE)
        }

        override fun onPlay() {
            callbackCaptor.recordEvent("onPlay")
            log("onPlay", category = LogCategory.LIFECYCLE)
        }

        override fun onPause() {
            callbackCaptor.recordEvent("onPause")
            log("onPause", category = LogCategory.LIFECYCLE)
        }

        override fun onBuffering() {
            callbackCaptor.recordEvent("onBuffering")
            log("onBuffering", category = LogCategory.LIFECYCLE)
        }

        override fun onEnd() {
            callbackCaptor.recordEvent("onEnd")
            log("onEnd", category = LogCategory.LIFECYCLE)
        }

        override fun onPlayerClosed() = log("onPlayerClosed", category = LogCategory.LIFECYCLE)
        override fun onPlayerReload() = log("onPlayerReload", category = LogCategory.LIFECYCLE)

        override fun onError(error: String?) {
            callbackCaptor.recordEvent("onError")
            log("onError", detail = error, category = LogCategory.ERROR)
        }

        override fun onPlaybackErrors(error: JSONObject?) {
            callbackCaptor.recordEvent("onPlaybackErrors")
            log("onPlaybackErrors", detail = error?.toString(), category = LogCategory.ERROR)
        }

        override fun onEmbedErrors(error: JSONObject?) {
            callbackCaptor.recordEvent("onEmbedErrors")
            log("onEmbedErrors", detail = error?.toString(), category = LogCategory.ERROR)
        }

        override fun onNext() {
            callbackCaptor.recordEvent("onNext")
            log("onNext", category = LogCategory.NAVIGATION)
        }

        override fun onPrevious() = log("onPrevious", category = LogCategory.NAVIGATION)

        override fun nextEpisodeIncoming(nextEpisodeId: String) {
            callbackCaptor.recordEvent("nextEpisodeIncoming")
            log("nextEpisodeIncoming", detail = nextEpisodeId, category = LogCategory.NAVIGATION)
            onNextEpisodeIncoming(nextEpisodeId)
        }

        override fun onNewSourceAdded(config: MediastreamPlayerConfig) {
            callbackCaptor.recordEvent("onNewSourceAdded")
            log("onNewSourceAdded", detail = "id=${config.id}", category = LogCategory.NAVIGATION)
        }

        override fun onLocalSourceAdded() =
            log("onLocalSourceAdded", category = LogCategory.NAVIGATION)

        override fun onFullscreen(enteredForPip: Boolean) =
            log("onFullscreen", detail = "pip=$enteredForPip", category = LogCategory.SYSTEM)

        override fun offFullscreen() = log("offFullscreen", category = LogCategory.SYSTEM)
        override fun onDismissButton() = log("onDismissButton", category = LogCategory.SYSTEM)

        override fun onConfigChange(config: MediastreamMiniPlayerConfig?) =
            log("onConfigChange", category = LogCategory.SYSTEM)

        override fun onLiveAudioCurrentSongChanged(data: JSONObject?) =
            log("onLiveAudioCurrentSongChanged",
                detail = data?.optString("title"),
                category = LogCategory.SYSTEM)

        override fun onAdEvents(type: AdEvent.AdEventType) {
            callbackCaptor.recordEvent("onAdEvents")
            callbackCaptor.recordEvent("onAdEvents:${type.name}")
            log("onAdEvents", detail = type.name, category = LogCategory.AD)
        }

        override fun onAdErrorEvent(error: AdError) {
            callbackCaptor.recordEvent("onAdErrorEvent")
            log("onAdErrorEvent",
                detail = "${error.errorCode}: ${error.message}",
                category = LogCategory.AD)
        }

        override fun onCastAvailable(state: Boolean?) =
            log("onCastAvailable", detail = "available=$state", category = LogCategory.CAST)

        override fun onCastSessionStarting() =
            log("onCastSessionStarting", category = LogCategory.CAST)

        override fun onCastSessionStarted() =
            log("onCastSessionStarted", category = LogCategory.CAST)

        override fun onCastSessionStartFailed() =
            log("onCastSessionStartFailed", category = LogCategory.CAST)

        override fun onCastSessionEnding() =
            log("onCastSessionEnding", category = LogCategory.CAST)

        override fun onCastSessionEnded() =
            log("onCastSessionEnded", category = LogCategory.CAST)

        override fun onCastSessionResuming() =
            log("onCastSessionResuming", category = LogCategory.CAST)

        override fun onCastSessionResumed() =
            log("onCastSessionResumed", category = LogCategory.CAST)

        override fun onCastSessionResumeFailed() =
            log("onCastSessionResumeFailed", category = LogCategory.CAST)

        override fun onCastSessionSuspended() =
            log("onCastSessionSuspended", category = LogCategory.CAST)
    }
}
