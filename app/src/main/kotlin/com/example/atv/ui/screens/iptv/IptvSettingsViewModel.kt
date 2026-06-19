package com.example.atv.ui.screens.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@HiltViewModel
class IptvSettingsViewModel @Inject constructor(
    private val credentialsStore: IptvCredentialsStore,
    private val deviceDefaults: DeviceDefaultsProvider,
    private val importChannelsUseCase: ImportCtcChannelsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IptvSettingsUiState())
    val uiState: StateFlow<IptvSettingsUiState> = _uiState.asStateFlow()

    /** Coalesces concurrent `testAndImport` taps so only the first runs (FR-017). */
    private val importMutex = Mutex()

    init {
        viewModelScope.launch {
            credentialsStore.observe().collect { stored ->
                if (stored != null) {
                    _uiState.update {
                        it.copy(
                            userId = stored.userId,
                            password = stored.password,
                            stbId = stored.stbId,
                            ip = stored.ip,
                            mac = stored.mac,
                            authServerUrl = stored.authServerUrl,
                        )
                    }
                } else {
                    applyDefaults()
                }
            }
        }
    }

    fun setUserId(v: String) = _uiState.update { it.copy(userId = v) }
    fun setPassword(v: String) = _uiState.update { it.copy(password = v) }
    fun setStbId(v: String) = _uiState.update { it.copy(stbId = v) }
    fun setIp(v: String) = _uiState.update { it.copy(ip = v) }
    fun setMac(v: String) = _uiState.update { it.copy(mac = v) }
    fun setAuthServerUrl(v: String) = _uiState.update { it.copy(authServerUrl = v) }

    fun testAndImport() {
        // Coalesce: if a fetch is in flight, drop the tap (FR-017).
        if (!importMutex.tryLock()) return
        viewModelScope.launch {
            try {
                val creds = _uiState.value.asCredentials
                if (!creds.isComplete) {
                    _uiState.update {
                        it.copy(importStatus = ImportStatus.LoginFailed("incomplete credentials"))
                    }
                    return@launch
                }
                credentialsStore.save(creds)
                _uiState.update { it.copy(importStatus = ImportStatus.LoggingIn) }
                val result = importChannelsUseCase()
                _uiState.update { it.copy(importStatus = result.toStatus()) }
            } finally {
                importMutex.unlock()
            }
        }
    }

    fun requestClearCredentials() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }

    fun dismissClearDialog() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    fun confirmClearCredentials() {
        viewModelScope.launch {
            credentialsStore.clear()
            _uiState.update { it.copy(showClearConfirmation = false, importStatus = ImportStatus.Idle) }
            applyDefaults()
        }
    }

    private fun applyDefaults() {
        val defaults = deviceDefaults.generate()
        _uiState.update {
            it.copy(
                userId = defaults.userId,
                password = defaults.password,
                stbId = defaults.stbId,
                ip = defaults.ip,
                mac = defaults.mac,
                authServerUrl = defaults.authServerUrl,
            )
        }
    }

    private fun ImportResult.toStatus(): ImportStatus = when (this) {
        is ImportResult.Success -> ImportStatus.Success(importedCount)
        is ImportResult.LoginFailure -> ImportStatus.LoginFailed(reason)
        is ImportResult.FetchFailure -> ImportStatus.FetchFailed(reason)
        is ImportResult.NoChannelsReturned -> ImportStatus.NoChannelsReturned
    }
}
