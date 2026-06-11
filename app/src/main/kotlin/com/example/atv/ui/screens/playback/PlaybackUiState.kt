package com.example.atv.ui.screens.playback

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.Program
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
    val errorMessage: String? = null,

    // EPG (004)
    val epgEnabled: Boolean = false,
    val epgConfigured: Boolean = false,
    val currentProgram: Program? = null,
    val nextProgram: Program? = null,
    val epgPanel: EpgPanelState = EpgPanelState()
) {
    val currentChannel: Channel?
        get() = channels.getOrNull(currentChannelIndex)

    val hasChannels: Boolean
        get() = channels.isNotEmpty()

    val channelCount: Int
        get() = channels.size

    val hasActiveOverlay: Boolean
        get() = showChannelInfo || showChannelList || showNumberPad || showSettings || showError

    /**
     * True when EPG surfaces should render — the user toggle is on AND a provider is configured.
     * In 004 alone, `epgConfigured` is permanently false, so this always evaluates false in production.
     */
    val showEpgSurfaces: Boolean
        get() = epgEnabled && epgConfigured
}
