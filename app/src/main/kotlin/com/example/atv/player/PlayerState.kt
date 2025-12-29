package com.example.atv.player

import com.example.atv.domain.model.Channel

/**
 * Sealed class representing the player state.
 */
sealed class PlayerState {
    /** Player is idle, no media loaded */
    data object Idle : PlayerState()
    
    /** Player is loading/preparing media */
    data class Loading(val channel: Channel) : PlayerState()
    
    /** Player is buffering */
    data class Buffering(
        val channel: Channel,
        val progress: Int = 0
    ) : PlayerState()
    
    /** Player is actively playing */
    data class Playing(val channel: Channel) : PlayerState()
    
    /** Player encountered an error */
    data class Error(
        val channel: Channel?,
        val message: String,
        val exception: Throwable? = null
    ) : PlayerState()
    
    val currentChannel: Channel?
        get() = when (this) {
            is Idle -> null
            is Loading -> channel
            is Buffering -> channel
            is Playing -> channel
            is Error -> channel
        }
    
    val isPlaying: Boolean
        get() = this is Playing
    
    val isLoading: Boolean
        get() = this is Loading || this is Buffering
    
    val hasError: Boolean
        get() = this is Error
}
