package com.example.atv.ui.screens.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.LoadPlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import androidx.core.net.toUri

data class SetupUiState(
    val isLoading: Boolean = false,
    val hasExistingPlaylist: Boolean = false,
    val channelCount: Int = 0,
    val errorMessage: String? = null,
    val isPlaylistLoaded: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loadPlaylistUseCase: LoadPlaylistUseCase,
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()
    
    init {
        checkExistingPlaylist()
    }
    
    private fun checkExistingPlaylist() {
        viewModelScope.launch {
            val count = channelRepository.getChannelCount()
            val hasPlaylist = preferencesRepository.getPlaylistFilePath().first() != null
            
            _uiState.update { state ->
                state.copy(
                    hasExistingPlaylist = hasPlaylist && count > 0,
                    channelCount = count
                )
            }
        }
    }
    
    fun loadPlaylist(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            loadPlaylistUseCase(uri)
                .onSuccess { channels ->
                    Timber.d("Loaded ${channels.size} channels")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            channelCount = channels.size,
                            isPlaylistLoaded = true,
                            hasExistingPlaylist = true
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load playlist")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load playlist"
                        )
                    }
                }
        }
    }
    
    /**
     * Load demo playlist bundled in app assets (for testing without file picker)
     */
    fun loadDemoPlaylist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                // Copy asset to internal storage and load from there
                val assetContent = context.assets.open("demo_playlist.m3u8")
                    .bufferedReader().use { it.readText() }
                
                val internalFile = File(context.filesDir, "demo_playlist.m3u8")
                internalFile.writeText(assetContent)
                
                loadPlaylistUseCase(Uri.fromFile(internalFile))
                    .onSuccess { channels ->
                        Timber.d("Loaded ${channels.size} demo channels")
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                channelCount = channels.size,
                                isPlaylistLoaded = true,
                                hasExistingPlaylist = true
                            )
                        }
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to load demo playlist")
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load demo playlist"
                            )
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read demo playlist from assets")
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Failed to load demo playlist: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Load playlist from a file path (for emulator testing where file picker isn't available)
     */
    fun loadPlaylistFromPath(path: String) {
        val file = File(path)
        if (file.exists()) {
            loadPlaylist(Uri.fromFile(file))
        } else {
            _uiState.update { state ->
                state.copy(errorMessage = "Demo playlist not found at: $path\nPush file with: adb push test_playlist.m3u8 /sdcard/Download/")
            }
        }
    }
    
    /**
     * Load playlist from a URL (HTTP/HTTPS)
     */
    fun loadPlaylistFromUrl(url: String) {
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a URL") }
            return
        }
        
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            _uiState.update { it.copy(errorMessage = "URL must start with http:// or https://") }
            return
        }
        
        loadPlaylist(trimmedUrl.toUri())
    }
    
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
