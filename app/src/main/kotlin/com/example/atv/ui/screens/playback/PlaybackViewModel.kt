package com.example.atv.ui.screens.playback

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.EpgProvider
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.ResolveStreamUrlUseCase
import com.example.atv.domain.usecase.SwitchChannelUseCase
import com.example.atv.player.AtvPlayerController
import com.example.atv.player.PlayerState
import com.example.atv.ui.components.SnackBarManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
@Suppress("TooManyFunctions", "LongParameterList") // Central playback ViewModel: many UI actions + injected deps
class PlaybackViewModel @Inject constructor(
    private val application: Application,
    private val atvPlayer: AtvPlayerController,
    private val channelRepository: ChannelRepository,
    private val channelSourceSettingsStore: ChannelSourceSettingsStore,
    private val preferencesRepository: PreferencesRepository,
    private val switchChannelUseCase: SwitchChannelUseCase,
    private val resolveStreamUrl: ResolveStreamUrlUseCase,
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
    private var bannerEpgJob: Job? = null
    private var panelEpgJob: Job? = null

    private val focusedChannelFlow = MutableSharedFlow<Pair<Channel, String>>(
        replay = 1,
        extraBufferCapacity = 8
    )
    private val dateOffsetFlow = MutableStateFlow(0)

    companion object {
        private const val CHANNEL_INFO_AUTO_HIDE_MS = 3000L
        private const val OVERLAY_AUTO_HIDE_MS = 10000L
        private const val MAX_CHANNEL_DIGITS = 3
        private const val SETTINGS_AUTO_HIDE_MS = 30000L

        /**
         * Debounce window for EPG-panel focus changes (FR-026).
         *
         * Set to 250ms: long enough to coalesce D-pad auto-repeat (Android TV
         * remotes typically repeat every 100–200 ms while a button is held),
         * so scrolling past channels does NOT fire one fetch per channel; short
         * enough that the EPG appears "immediately" after the user stops on a
         * channel they care about — half a second feels noticeably sluggish for
         * a banner that shows up next to a focused row.
         *
         * The user behavior the question raised ("if we switch within this time
         * we won't load programs for this channel") is exactly the intent:
         * channels you scroll PAST should not trigger network calls, only the
         * channel you LAND ON should.
         */
        private const val PANEL_DEBOUNCE_MS = 250L
    }

    init {
        atvPlayer.initialize()
        observeChannels()
        observePlayerState()
        observeEpgFlags()
        observePanelEpg()
        observeUdpxyProxy()
        observeSourceMode()
    }

    // Latest udpxy proxy from preferences, passed to AtvPlayer.playChannel so
    // igmp:// channel URLs are relayed over HTTP. Seeded with the default so the
    // startup auto-play (which races observeUdpxyProxy) never uses a null proxy.
    // Volatile: written from a collector coroutine, read on the playback call path.
    @Volatile
    private var udpxyProxy: String? = UserPreferences.DEFAULT_UDPXY_PROXY

    @Volatile
    private var sourceMode: ChannelSourceMode = ChannelSourceMode.DIRECT_CTC

    private fun observeUdpxyProxy() {
        viewModelScope.launch {
            preferencesRepository.getUserPreferences()
                .map { it.udpxyProxy }
                .collect { udpxyProxy = it }
        }
    }

    private fun observeSourceMode() {
        viewModelScope.launch {
            channelSourceSettingsStore.observeMode().collect { sourceMode = it }
        }
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

    private fun loadBannerEpgFor(channel: Channel) {
        val code = channel.channelCode
        // TODO(005): when Channel.channelCode is a real nullable field on the data class,
        // delete this comment but keep the early-return behavior identical.
        if (!_uiState.value.showEpgSurfaces || code == null) {
            bannerEpgJob?.cancel()
            bannerEpgJob = null
            _uiState.update { it.copy(currentProgram = null, nextProgram = null) }
            return
        }
        loadBannerEpgForCode(channel, code)
    }

    /**
     * Test seam: drives the banner EPG fetch with an explicit channel code, bypassing
     * the always-null `Channel.channelCode` extension that ships in 004. Production
     * code paths always go through `loadBannerEpgFor(channel)` instead.
     *
     * The provider already dispatches to IO internally (see [CtcEpgProvider.fetchPrograms]),
     * so this wrapper does not impose an additional withContext — that would only slow
     * down tests using StandardTestDispatcher without changing real-world behavior.
     */
    internal fun loadBannerEpgForCode(channel: Channel, channelCode: String) {
        bannerEpgJob?.cancel()
        bannerEpgJob = viewModelScope.launch {
            val result = epgProvider.fetchPrograms(channelCode, dateOffset = 0)
            result.fold(
                onSuccess = { programs ->
                    val now = clock.instant()
                    val current = programs.find { it.airsAt(now) }
                    val next = programs.firstOrNull { it.start.isAfter(now) }
                    _uiState.update { it.copy(currentProgram = current, nextProgram = next) }
                },
                onFailure = { t ->
                    Timber.w(t, "Banner EPG fetch failed for channel ${channel.number}")
                    _uiState.update { it.copy(currentProgram = null, nextProgram = null) }
                }
            )
        }
    }

    @OptIn(FlowPreview::class)
    private fun observePanelEpg() {
        viewModelScope.launch {
            focusedChannelFlow
                .combine(dateOffsetFlow) { (channel, code), offset -> Triple(channel, code, offset) }
                .debounce(PANEL_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (channel, code, offset) -> loadPanelEpg(channel, code, offset) }
        }
    }

    /**
     * Production entry point — looks up the channel's `channelCode` extension.
     * In 004 alone this is always null, so the no-op early-return path is exercised.
     */
    fun onChannelFocused(channel: Channel) {
        // Moving to a different channel resets the EPG day back to Today — a
        // Yesterday/Tomorrow selection is per-channel and must not carry over (same
        // rule as reopening the overlay, FR-009).
        if (_uiState.value.epgPanel.focusedChannel?.number != channel.number) {
            dateOffsetFlow.value = 0
            _uiState.update { it.copy(epgPanel = it.epgPanel.copy(dateOffset = 0)) }
        }
        val code = channel.channelCode ?: run {
            _uiState.update {
                it.copy(epgPanel = it.epgPanel.copy(focusedChannel = channel, isLoading = false))
            }
            return
        }
        onChannelFocusedWithCode(channel, code)
    }

    /**
     * Test seam: drives the panel focus flow with an explicit channel code,
     * bypassing the always-null `Channel.channelCode` extension that ships in 004.
     */
    internal fun onChannelFocusedWithCode(channel: Channel, channelCode: String) {
        focusedChannelFlow.tryEmit(channel to channelCode)
    }

    fun setEpgDateOffset(offset: Int) {
        require(offset in -1..1) { "EPG date offset must be in -1..1, got $offset" }
        dateOffsetFlow.value = offset
    }

    private fun loadPanelEpg(channel: Channel, channelCode: String, dateOffset: Int) {
        // FR-019 + FR-030: if EPG surfaces are hidden (toggle off OR provider unconfigured),
        // do NOT issue a fetch and do NOT render an error. The panel won't be shown anyway.
        // This guard protects against debounced focus events fired while the toggle was on
        // but settling AFTER the toggle flipped off — without it, the user could see a
        // brief "Unable to load programs" flash.
        if (!_uiState.value.showEpgSurfaces) return

        panelEpgJob?.cancel()
        _uiState.update {
            it.copy(
                epgPanel = it.epgPanel.copy(
                    focusedChannel = channel,
                    dateOffset = dateOffset,
                    isLoading = true,
                    errorMessage = null
                )
            )
        }
        panelEpgJob = viewModelScope.launch {
            val result = epgProvider.fetchPrograms(channelCode, dateOffset)
            result.fold(
                onSuccess = { programs ->
                    _uiState.update {
                        it.copy(
                            epgPanel = it.epgPanel.copy(
                                programs = programs,
                                isLoading = false,
                                errorMessage = null
                            )
                        )
                    }
                },
                onFailure = { t ->
                    Timber.w(t, "Panel EPG fetch failed for $channelCode offset=$dateOffset")
                    _uiState.update {
                        it.copy(
                            epgPanel = it.epgPanel.copy(
                                programs = emptyList(),
                                isLoading = false,
                                errorMessage = application.getString(R.string.epg_load_error)
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Play a specific channel.
     */
    fun playChannel(channel: Channel) {
        Timber.d("Playing channel: ${channel.number} - ${channel.name}")

        val index = _uiState.value.channels.indexOfFirst { it.number == channel.number }
        _uiState.update { it.copy(currentChannelIndex = index.coerceAtLeast(0)) }

        val playableUrl = resolveStreamUrl(channel.streamUrl, sourceMode, udpxyProxy)
        atvPlayer.playChannel(channel, playableUrl)
        showChannelInfo()
        loadBannerEpgFor(channel)

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
        // FR-009: always reset to Today when reopening the channel list. The user picking
        // Yesterday/Tomorrow is a within-session affordance and MUST NOT persist.
        dateOffsetFlow.value = 0
        _uiState.update {
            it.copy(
                showChannelList = true,
                showChannelInfo = false,
                epgPanel = it.epgPanel.copy(dateOffset = 0)
            )
        }
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
        
        val newInput = (currentInput + digit).take(MAX_CHANNEL_DIGITS)
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
