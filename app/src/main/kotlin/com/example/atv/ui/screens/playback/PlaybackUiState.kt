package com.example.atv.ui.screens.playback

import com.example.atv.domain.model.Channel
import com.example.atv.player.PlayerState

/**
 * UI state for the playback screen.
 */
data class PlaybackUiState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val currentChannelIndex: Int = 0,
    val playerState: PlayerState = PlayerState.Idle,
    
    // Overlay visibility
    val showChannelInfo: Boolean = false,
    val showChannelList: Boolean = false,
    val showNumberPad: Boolean = false,
    val showSettings: Boolean = false,
    val showError: Boolean = false,
    
    // Number pad input
    val numberPadInput: String = "",
    
    // Error state
    val errorMessage: String? = null
) {
    val currentChannel: Channel?
        get() = channels.getOrNull(currentChannelIndex)
    
    val hasChannels: Boolean
        get() = channels.isNotEmpty()
    
    val channelCount: Int
        get() = channels.size
    
    val hasActiveOverlay: Boolean
        get() = showChannelInfo || showChannelList || showNumberPad || showSettings || showError
}
