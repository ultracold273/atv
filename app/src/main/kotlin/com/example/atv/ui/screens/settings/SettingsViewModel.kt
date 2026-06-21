package com.example.atv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.R
import com.example.atv.data.epg.IptvSessionBootstrapper
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Subtitle shown on the "IPTV setup" Settings row, reflecting credential/import state.
 */
sealed class IptvSetupSubtitle {
    object NotConfigured : IptvSetupSubtitle()
    data class Imported(val count: Int) : IptvSetupSubtitle()
    data class SyncFailed(val reason: String) : IptvSetupSubtitle()

    @Composable
    fun resolve(): String = when (this) {
        NotConfigured -> stringResource(R.string.iptv_setup_subtitle_not_configured)
        is Imported -> stringResource(R.string.iptv_setup_subtitle_imported, count)
        is SyncFailed -> stringResource(R.string.iptv_setup_subtitle_sync_failed, reason)
    }
}

/**
 * UI State for the Settings screen.
 */
data class SettingsUiState(
    val channelCount: Int = 0,
    val playlistUri: String? = null,
    val lastPlayedChannelId: String? = null,
    val isLoading: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val showAbout: Boolean = false,
    val epgEnabled: Boolean = false,
    val iptvSetupSubtitle: IptvSetupSubtitle = IptvSetupSubtitle.NotConfigured,
    val udpxyProxy: String = "",
    val message: String? = null
)

/**
 * ViewModel for the Settings screen.
 * Manages playlist operations and user preferences.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository,
    private val iptvCredentialsStore: IptvCredentialsStore,
    private val iptvBootstrapper: IptvSessionBootstrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeBootstrapResult()
    }

    private fun observeBootstrapResult() {
        viewModelScope.launch {
            iptvBootstrapper.lastResult.collect { result ->
                val current = _uiState.value.iptvSetupSubtitle
                val updated = when (result) {
                    is ImportResult.LoginFailure -> IptvSetupSubtitle.SyncFailed(result.reason)
                    is ImportResult.FetchFailure -> IptvSetupSubtitle.SyncFailed(result.reason)
                    ImportResult.NoChannelsReturned -> IptvSetupSubtitle.SyncFailed("no channels")
                    is ImportResult.Success -> IptvSetupSubtitle.Imported(result.importedCount)
                    null -> current
                }
                _uiState.update { it.copy(iptvSetupSubtitle = updated) }
            }
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Get channel count
                val channels = channelRepository.getAllChannels().first()
                
                // Get preferences
                val preferences = preferencesRepository.getUserPreferences().first()

                val creds = iptvCredentialsStore.read()
                val iptvSubtitle = when {
                    creds == null || !creds.isComplete -> IptvSetupSubtitle.NotConfigured
                    channels.isEmpty() -> IptvSetupSubtitle.SyncFailed("no channels imported yet")
                    else -> IptvSetupSubtitle.Imported(channels.size)
                }

                _uiState.update { state ->
                    state.copy(
                        channelCount = channels.size,
                        playlistUri = preferences.playlistFilePath,
                        lastPlayedChannelId = preferences.lastChannelNumber.toString(),
                        epgEnabled = preferences.epgEnabled,
                        iptvSetupSubtitle = iptvSubtitle,
                        udpxyProxy = preferences.udpxyProxy.orEmpty(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        message = "Failed to load settings: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Shows confirmation dialog for clearing all channels.
     */
    fun showClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }
    
    /**
     * Dismisses any open dialog.
     */
    fun dismissDialog() {
        _uiState.update { state ->
            state.copy(
                showClearConfirmation = false,
                showAbout = false
            )
        }
    }
    
    /**
     * Shows the About dialog.
     */
    fun showAbout() {
        _uiState.update { it.copy(showAbout = true) }
    }
    
    /**
     * Clears all channels and resets preferences.
     */
    fun clearAllData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Delete all channels
                channelRepository.clearAll()
                
                // Reset preferences
                preferencesRepository.clear()
                
                _uiState.update { state ->
                    state.copy(
                        channelCount = 0,
                        lastPlayedChannelId = null,
                        showClearConfirmation = false,
                        isLoading = false,
                        message = "All data cleared"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        showClearConfirmation = false,
                        isLoading = false,
                        message = "Failed to clear data: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Clears the current message.
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Toggles the "Show program guide" setting and persists it.
     *
     * Updates UI state immediately for snappy feedback; the persisted
     * value will be reflected on the next `loadSettings` cycle.
     */
    fun setEpgEnabled(enabled: Boolean) {
        _uiState.update { it.copy(epgEnabled = enabled) }
        viewModelScope.launch {
            preferencesRepository.setEpgEnabled(enabled)
        }
    }

    /**
     * Updates and persists the udpxy proxy address. UI updates immediately;
     * the value takes effect on the next channel play.
     */
    fun setUdpxyProxy(value: String) {
        _uiState.update { it.copy(udpxyProxy = value) }
        viewModelScope.launch {
            preferencesRepository.setUdpxyProxy(value)
        }
    }

    /**
     * Refreshes settings data.
     */
    fun refresh() {
        loadSettings()
    }
}
