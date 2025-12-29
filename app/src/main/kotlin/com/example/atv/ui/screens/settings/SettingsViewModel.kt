package com.example.atv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val message: String? = null
)

/**
 * ViewModel for the Settings screen.
 * Manages playlist operations and user preferences.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Get channel count
                val channels = channelRepository.getAllChannels().first()
                
                // Get preferences
                val preferences = preferencesRepository.getUserPreferences().first()
                
                _uiState.update { state ->
                    state.copy(
                        channelCount = channels.size,
                        playlistUri = preferences.playlistFilePath,
                        lastPlayedChannelId = preferences.lastChannelNumber.toString(),
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
     * Refreshes settings data.
     */
    fun refresh() {
        loadSettings()
    }
}
