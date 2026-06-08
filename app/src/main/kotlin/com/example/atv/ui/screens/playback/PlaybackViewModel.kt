package com.example.atv.ui.screens.playback

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.EpgProvider
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.SwitchChannelUseCase
import com.example.atv.player.AtvPlayer
import com.example.atv.player.PlayerState
import com.example.atv.ui.components.SnackBarManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject

/**
 * ViewModel for the playback screen.
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val application: Application,
    private val atvPlayer: AtvPlayer,
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository,
    private val switchChannelUseCase: SwitchChannelUseCase,
    private val epgProvider: EpgProvider,
    private val clock: Clock
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()
    
    /**
     * Get the player instance for PlayerView.
     */
    val player get() = atvPlayer.player
    
    private var channelInfoHideJob: Job? = null
    private var overlayAutoHideJob: Job? = null
    
    companion object {
        private const val CHANNEL_INFO_AUTO_HIDE_MS = 3000L
        private const val OVERLAY_AUTO_HIDE_MS = 10000L
        private const val SETTINGS_AUTO_HIDE_MS = 30000L
    }
    
    init {
        atvPlayer.initialize()
        observeChannels()
        observePlayerState()
        observeEpgFlags()
    }
    
    private fun observeChannels() {
        viewModelScope.launch {
            combine(
                channelRepository.getAllChannels(),
                preferencesRepository.getLastChannelNumber()
            ) { channels, lastNumber ->
                Pair(channels, lastNumber)
            }.collect { (channels, lastNumber) ->
                val channelIndex = channels.indexOfFirst { it.number == lastNumber }
                    .coerceAtLeast(0)
                
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        channels = channels,
                        currentChannelIndex = channelIndex
                    )
                }
                
                // Auto-play first channel if we have channels and not already playing
                if (channels.isNotEmpty() && atvPlayer.playerState.value is PlayerState.Idle) {
                    val channel = channels.getOrNull(channelIndex) ?: channels.first()
                    playChannel(channel)
                }
            }
        }
    }
    
    private fun observePlayerState() {
        viewModelScope.launch {
            atvPlayer.playerState.collect { playerState ->
                _uiState.update { state ->
                    state.copy(
                        playerState = playerState,
                        showError = playerState is PlayerState.Error,
                        errorMessage = (playerState as? PlayerState.Error)?.message
                    )
                }
            }
        }
    }

    private fun observeEpgFlags() {
        viewModelScope.launch {
            var wasShowing = false
            combine(
                preferencesRepository.getUserPreferences().map { it.epgEnabled },
                epgProvider.isConfigured
            ) { epgEnabled, epgConfigured -> epgEnabled to epgConfigured }
                .collect { (epgEnabled, epgConfigured) ->
                    val show = epgEnabled && epgConfigured
                    _uiState.update { state ->
                        if (show) {
                            state.copy(epgEnabled = epgEnabled, epgConfigured = epgConfigured)
                        } else {
                            // Toggle off OR provider unconfigured: clear all EPG-derived state.
                            state.copy(
                                epgEnabled = epgEnabled,
                                epgConfigured = epgConfigured,
                                currentProgram = null,
                                nextProgram = null,
                                epgPanel = EpgPanelState()
                            )
                        }
                    }
                    // FR-025: when EPG transitions from hidden to shown, trigger a fetch
                    // for the currently-active channel so the user sees data immediately.
                    // (Practically inert in 004 because epgConfigured is permanently false,
                    // but the transition logic is exercised in PlaybackViewModelEpgTest
                    // when the test flips both flags.)
                    if (!wasShowing && show) {
                        _uiState.value.currentChannel?.let { loadBannerEpgFor(it) }
                    }
                    wasShowing = show
                }
        }
    }

    /**
     * Banner EPG loader stub for Task 11 — Task 12 fills in the real body.
     * Kept as a no-op here so [observeEpgFlags] compiles before the channelCode
     * extension and the provider call wiring land.
     */
    private fun loadBannerEpgFor(channel: Channel) {
        // TODO(Task 12): fetch programs and compute current/next.
        @Suppress("UNUSED_PARAMETER")
        channel
    }
    
    /**
     * Play a specific channel.
     */
    fun playChannel(channel: Channel) {
        Timber.d("Playing channel: ${channel.number} - ${channel.name}")
        
        val index = _uiState.value.channels.indexOfFirst { it.number == channel.number }
        _uiState.update { it.copy(currentChannelIndex = index.coerceAtLeast(0)) }
        
        atvPlayer.playChannel(channel)
        showChannelInfo()
        
        // Save last channel
        viewModelScope.launch {
            preferencesRepository.setLastChannelNumber(channel.number)
        }
    }
    
    /**
     * Switch to the next channel.
     */
    fun nextChannel() {
        viewModelScope.launch {
            val currentChannel = _uiState.value.currentChannel
            val nextChannel = switchChannelUseCase.nextChannel(currentChannel)
            nextChannel?.let { playChannel(it) }
        }
    }
    
    /**
     * Switch to the previous channel.
     */
    fun previousChannel() {
        viewModelScope.launch {
            val currentChannel = _uiState.value.currentChannel
            val prevChannel = switchChannelUseCase.previousChannel(currentChannel)
            prevChannel?.let { playChannel(it) }
        }
    }
    
    /**
     * Switch to a channel by number.
     */
    fun switchToChannel(number: Int) {
        viewModelScope.launch {
            val channel = switchChannelUseCase.switchToChannel(number)
            if (channel != null) {
                playChannel(channel)
                hideNumberPad()
            } else {
                // Show error for invalid channel number via Snack bar
                val errorMessage = application.getString(R.string.error_channel_not_found, number)
                SnackBarManager.show(errorMessage)
            }
        }
    }
    
    /**
     * Select a channel from the channel list.
     */
    fun selectChannelFromList(channel: Channel) {
        playChannel(channel)
        hideChannelList()
    }
    
    // ==================== Channel Info Overlay ====================
    
    fun showChannelInfo() {
        _uiState.update { it.copy(showChannelInfo = true) }
        startChannelInfoAutoHide()
    }
    
    private fun hideChannelInfo() {
        channelInfoHideJob?.cancel()
        _uiState.update { it.copy(showChannelInfo = false) }
    }
    
    private fun startChannelInfoAutoHide() {
        channelInfoHideJob?.cancel()
        channelInfoHideJob = viewModelScope.launch {
            delay(CHANNEL_INFO_AUTO_HIDE_MS)
            hideChannelInfo()
        }
    }
    
    // ==================== Channel List Overlay ====================
    
    fun showChannelList() {
        _uiState.update { it.copy(showChannelList = true, showChannelInfo = false) }
        startOverlayAutoHide(OVERLAY_AUTO_HIDE_MS) { hideChannelList() }
    }
    
    fun resetChannelListAutoHide() {
        startOverlayAutoHide(OVERLAY_AUTO_HIDE_MS) { hideChannelList() }
    }
    
    fun hideChannelList() {
        overlayAutoHideJob?.cancel()
        _uiState.update { it.copy(showChannelList = false) }
    }
    
    // ==================== Number Pad Overlay ====================
    
    fun showNumberPad() {
        _uiState.update { it.copy(showNumberPad = true, showChannelInfo = false, numberPadInput = "") }
        startOverlayAutoHide(OVERLAY_AUTO_HIDE_MS) { hideNumberPad() }
    }
    
    fun resetNumberPadAutoHide() {
        startOverlayAutoHide(OVERLAY_AUTO_HIDE_MS) { hideNumberPad() }
    }
    
    fun hideNumberPad() {
        overlayAutoHideJob?.cancel()
        _uiState.update { it.copy(showNumberPad = false, numberPadInput = "") }
    }
    
    fun appendNumberPadDigit(digit: String) {
        val currentInput = _uiState.value.numberPadInput
        
        // Ignore leading 0 (channels start from 1)
        if (digit == "0" && currentInput.isEmpty()) {
            return
        }
        
        val newInput = (currentInput + digit).take(3)
        _uiState.update { it.copy(numberPadInput = newInput) }
        // Reset auto-hide timer
        startOverlayAutoHide(OVERLAY_AUTO_HIDE_MS) { hideNumberPad() }
    }
    
    fun clearNumberPadInput() {
        _uiState.update { it.copy(numberPadInput = "") }
    }
    
    fun backspaceNumberPadDigit() {
        _uiState.update { state ->
            val newInput = state.numberPadInput.dropLast(1)
            state.copy(numberPadInput = newInput)
        }
        // Reset auto-hide timer
        startOverlayAutoHide(OVERLAY_AUTO_HIDE_MS) { hideNumberPad() }
    }
    
    fun confirmNumberPadInput() {
        val input = _uiState.value.numberPadInput
        if (input.isNotEmpty()) {
            val number = input.toIntOrNull()
            if (number != null) {
                val channelCount = _uiState.value.channelCount
                if (number !in 1..channelCount) {
                    // Show snack bar error and clear input
                    showSnackBar(application.getString(R.string.error_channel_not_found, number))
                    _uiState.update { it.copy(numberPadInput = "") }
                } else {
                    switchToChannel(number)
                }
            }
        }
    }
    
    private fun showSnackBar(message: String) {
        SnackBarManager.show(message)
    }
    
    // ==================== Settings Overlay ====================
    
    fun showSettings() {
        _uiState.update { it.copy(showSettings = true, showChannelInfo = false) }
        startOverlayAutoHide(SETTINGS_AUTO_HIDE_MS) { hideSettings() }
    }
    
    fun hideSettings() {
        overlayAutoHideJob?.cancel()
        _uiState.update { it.copy(showSettings = false) }
    }
    
    // ==================== Error Handling ====================
    
    fun retry() {
        _uiState.update { it.copy(showError = false, errorMessage = null) }
        atvPlayer.retry()
    }
    
    fun dismissError() {
        _uiState.update { it.copy(showError = false) }
    }
    
    // ==================== Utility ====================
    
    private fun startOverlayAutoHide(delayMs: Long, hideAction: () -> Unit) {
        overlayAutoHideJob?.cancel()
        overlayAutoHideJob = viewModelScope.launch {
            delay(delayMs)
            hideAction()
        }
    }
    
    fun dismissActiveOverlay(): Boolean {
        return when {
            _uiState.value.showNumberPad -> {
                hideNumberPad()
                true
            }
            _uiState.value.showChannelList -> {
                hideChannelList()
                true
            }
            _uiState.value.showSettings -> {
                hideSettings()
                true
            }
            _uiState.value.showError -> {
                dismissError()
                true
            }
            _uiState.value.showChannelInfo -> {
                hideChannelInfo()
                true
            }
            else -> false
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        atvPlayer.release()
    }
}
