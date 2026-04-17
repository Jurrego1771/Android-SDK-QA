package com.example.sdk_qa.core

import android.graphics.Color

data class LogEntry(
    val timestamp: String,
    val event: String,
    val detail: String?,
    val category: LogCategory
)

enum class LogCategory(val color: Int) {
    /** onPlay, onPause, onReady, onEnd, onBuffering, playerViewReady, onPlayerClosed, onPlayerReload */
    LIFECYCLE(Color.parseColor("#4CAF50")),

    /** onError, onEmbedErrors, onPlaybackErrors */
    ERROR(Color.parseColor("#F44336")),

    /** onAdEvents, onAdErrorEvent */
    AD(Color.parseColor("#FF9800")),

    /** onCast* */
    CAST(Color.parseColor("#2196F3")),

    /** onNext, onPrevious, nextEpisodeIncoming, onNewSourceAdded, onLocalSourceAdded */
    NAVIGATION(Color.parseColor("#9C27B0")),

    /** onFullscreen, offFullscreen, onDismissButton, onConfigChange, onLiveAudioCurrentSongChanged */
    SYSTEM(Color.parseColor("#607D8B"))
}
