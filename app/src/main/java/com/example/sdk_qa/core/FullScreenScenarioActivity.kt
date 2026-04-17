package com.example.sdk_qa.core

import am.mediastre.mediastreamplatformsdkandroid.MediastreamMiniPlayerConfig
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayer
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerCallback
import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.AdError
import com.google.ads.interactivemedia.v3.api.AdEvent
import org.json.JSONObject

/**
 * Base para escenarios full-screen puros (Reels, etc.).
 *
 * - Layout 100% programático — sin FAB, sin panel de debug
 * - Player ocupa toda la pantalla incluyendo system bars
 * - Todos los callbacks van a Logcat únicamente
 * - Sistema de barras oculto (modo inmersivo)
 */
abstract class FullScreenScenarioActivity : AppCompatActivity() {

    protected var player: MediastreamPlayer? = null
    private lateinit var playerContainer: FrameLayout
    private lateinit var playerView: PlayerView

    abstract fun buildConfig(): MediastreamPlayerConfig
    open fun getScenarioTitle(): String = javaClass.simpleName
    open fun onPlayerCreated() {}
    open fun onNextEpisodeIncoming(nextEpisodeId: String) {}

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playerContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        playerView = PlayerView(this).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        playerContainer.addView(playerView)
        setContentView(playerContainer)

        initPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.releasePlayer()
    }

    // -------------------------------------------------------------------------
    // Player
    // -------------------------------------------------------------------------

    private fun initPlayer() {
        player = MediastreamPlayer(
            this, buildConfig(), playerContainer, playerView, supportFragmentManager
        )
        player?.addPlayerCallback(buildCallback())
        onPlayerCreated()
    }

    // -------------------------------------------------------------------------
    // Callback — todo a Logcat (block body para retornar Unit siempre)
    // -------------------------------------------------------------------------

    private fun buildCallback() = object : MediastreamPlayerCallback {

        private fun tag() = "SDK_QA/${getScenarioTitle()}"

        override fun playerViewReady(msplayerView: PlayerView?) {
            Log.d(tag(), "playerViewReady")
        }
        override fun onReady() { Log.d(tag(), "onReady") }
        override fun onPlay() { Log.d(tag(), "onPlay") }
        override fun onPause() { Log.d(tag(), "onPause") }
        override fun onBuffering() { Log.d(tag(), "onBuffering") }
        override fun onEnd() { Log.d(tag(), "onEnd") }
        override fun onPlayerClosed() { Log.d(tag(), "onPlayerClosed") }
        override fun onPlayerReload() { Log.d(tag(), "onPlayerReload") }

        override fun onError(error: String?) { Log.e(tag(), "onError: $error") }
        override fun onPlaybackErrors(error: JSONObject?) {
            Log.e(tag(), "onPlaybackErrors: $error")
        }
        override fun onEmbedErrors(error: JSONObject?) {
            Log.e(tag(), "onEmbedErrors: $error")
        }

        override fun onNext() { Log.d(tag(), "onNext") }
        override fun onPrevious() { Log.d(tag(), "onPrevious") }

        override fun nextEpisodeIncoming(nextEpisodeId: String) {
            Log.d(tag(), "nextEpisodeIncoming: $nextEpisodeId")
            onNextEpisodeIncoming(nextEpisodeId)
        }

        override fun onNewSourceAdded(config: MediastreamPlayerConfig) {
            Log.d(tag(), "onNewSourceAdded: id=${config.id}")
        }
        override fun onLocalSourceAdded() { Log.d(tag(), "onLocalSourceAdded") }

        override fun onFullscreen(enteredForPip: Boolean) {
            Log.d(tag(), "onFullscreen pip=$enteredForPip")
        }
        override fun offFullscreen() { Log.d(tag(), "offFullscreen") }
        override fun onDismissButton() { Log.d(tag(), "onDismissButton") }

        override fun onConfigChange(config: MediastreamMiniPlayerConfig?) {
            Log.d(tag(), "onConfigChange")
        }
        override fun onLiveAudioCurrentSongChanged(data: JSONObject?) {
            Log.d(tag(), "onLiveAudioCurrentSongChanged: ${data?.optString("title")}")
        }

        override fun onAdEvents(type: AdEvent.AdEventType) {
            Log.d(tag(), "onAdEvents: ${type.name}")
        }
        override fun onAdErrorEvent(error: AdError) {
            Log.e(tag(), "onAdErrorEvent: ${error.errorCode}: ${error.message}")
        }

        override fun onCastAvailable(state: Boolean?) { Log.d(tag(), "onCastAvailable: $state") }
        override fun onCastSessionStarting() { Log.d(tag(), "onCastSessionStarting") }
        override fun onCastSessionStarted() { Log.d(tag(), "onCastSessionStarted") }
        override fun onCastSessionStartFailed() { Log.d(tag(), "onCastSessionStartFailed") }
        override fun onCastSessionEnding() { Log.d(tag(), "onCastSessionEnding") }
        override fun onCastSessionEnded() { Log.d(tag(), "onCastSessionEnded") }
        override fun onCastSessionResuming() { Log.d(tag(), "onCastSessionResuming") }
        override fun onCastSessionResumed() { Log.d(tag(), "onCastSessionResumed") }
        override fun onCastSessionResumeFailed() { Log.d(tag(), "onCastSessionResumeFailed") }
        override fun onCastSessionSuspended() { Log.d(tag(), "onCastSessionSuspended") }
    }
}
