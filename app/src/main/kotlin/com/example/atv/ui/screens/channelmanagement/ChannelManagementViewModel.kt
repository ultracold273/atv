package com.example.atv.ui.screens.channelmanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelManagementUiState(
    val channels: List<Channel> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingChannel: Channel? = null,
    val deletingChannel: Channel? = null,
    val errorMessage: String? = null,
    val editName: String = "",
    val editUrl: String = ""
)

@HiltViewModel
class ChannelManagementViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChannelManagementUiState())
    val uiState: StateFlow<ChannelManagementUiState> = _uiState.asStateFlow()
    
    init {
        observeChannels()
    }
    
    private fun observeChannels() {
        viewModelScope.launch {
            channelRepository.getAllChannels().collect { channels ->
                _uiState.update { state ->
                    state.copy(
                        channels = channels,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    // ==================== Add Channel ====================
    
    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }
    
    fun addChannel(name: String, url: String, number: Int) {
        if (name.isBlank() || url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name and URL are required") }
            return
        }
        
        viewModelScope.launch {
            val newChannel = Channel(
                number = number,
                name = name.trim(),
                streamUrl = url.trim()
            )
            
            channelRepository.addChannel(newChannel)
            dismissDialog()
        }
    }
    
    // ==================== Edit Channel ====================
    
    fun showEditDialog(channel: Channel) {
        _uiState.update { it.copy(editingChannel = channel) }
    }
    
    fun updateChannel(channel: Channel) {
        viewModelScope.launch {
            channelRepository.updateChannel(channel)
            dismissDialog()
        }
    }
    
    // ==================== Delete Channel ====================
    
    fun showDeleteConfirm(channel: Channel) {
        _uiState.update { it.copy(deletingChannel = channel) }
    }
    
    fun deleteChannel(channel: Channel) {
        viewModelScope.launch {
            channelRepository.deleteChannel(channel)
            dismissDialog()
        }
    }
    
    // ==================== Dismiss ====================
    
    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = false,
                editingChannel = null,
                deletingChannel = null
            )
        }
    }
    
    // ==================== Form State ====================
    
    fun updateEditName(name: String) {
        _uiState.update { it.copy(editName = name) }
    }
    
    fun updateEditUrl(url: String) {
        _uiState.update { it.copy(editUrl = url) }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
