package com.example.atv.domain.model

/**
 * Runtime state for the media player (not persisted).
 * 
 * @param status Current playback status
 * @param currentChannel Currently playing channel
 * @param errorMessage Error message if status is Error
 * @param bufferingProgress 0-100 for buffering indicator
 */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val currentChannel: Channel? = null,
    val errorMessage: String? = null,
    val bufferingProgress: Int = 0
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.Playing
    val isLoading: Boolean get() = status == PlaybackStatus.Loading || status == PlaybackStatus.Buffering
    val hasError: Boolean get() = status == PlaybackStatus.Error
}

/**
 * Playback status states.
 * 
 * State transitions:
 * - Idle → Loading (select channel)
 * - Loading → Buffering | Playing (prepared) or Error (failed)
 * - Buffering → Playing (buffered)
 * - Playing → Buffering (buffer empty) or Loading (change channel) or Error
 * - Error → Loading (retry/change channel)
 */
enum class PlaybackStatus {
    /** No channel selected */
    Idle,
    /** Preparing media */
    Loading,
    /** Stream buffering */
    Buffering,
    /** Active playback */
    Playing,
    /** Playback failed */
    Error
}
