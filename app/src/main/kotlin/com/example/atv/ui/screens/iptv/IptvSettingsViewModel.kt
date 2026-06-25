package com.example.atv.ui.screens.iptv

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.ImportResult
import com.example.atv.domain.usecase.LoadPlaylistUseCase
import com.example.atv.domain.usecase.UnifiedImportChannelsUseCase
import com.example.atv.util.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class IptvSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentialsStore: IptvCredentialsStore,
    private val sourceSettingsStore: ChannelSourceSettingsStore,
    private val preferencesRepository: PreferencesRepository,
    private val deviceDefaults: DeviceDefaultsProvider,
    private val importChannelsUseCase: UnifiedImportChannelsUseCase,
    private val loadPlaylistUseCase: LoadPlaylistUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IptvSettingsUiState())
    val uiState: StateFlow<IptvSettingsUiState> = _uiState.asStateFlow()

    private val importMutex = Mutex()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            val mode = sourceSettingsStore.readMode()
            val prefs = preferencesRepository.getUserPreferences().first()
            val proxy = sourceSettingsStore.readProxySettings()
            _uiState.update {
                it.copy(
                    sourceMode = mode,
                    playlistUrl = prefs.playlistFilePath.orEmpty(),
                    udpxyProxy = prefs.udpxyProxy.orEmpty(),
                    proxyBaseUrl = proxy?.proxyBaseUrl.orEmpty(),
                    proxyAccessToken = proxy?.accessToken.orEmpty(),
                )
            }
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

    fun selectMode(mode: ChannelSourceMode) {
        _uiState.update { it.copy(sourceMode = mode, importStatus = ImportStatus.Idle) }
        viewModelScope.launch { sourceSettingsStore.saveMode(mode) }
    }

    fun setPlaylistUrl(v: String) = _uiState.update { it.copy(playlistUrl = v) }
    fun setUserId(v: String) = _uiState.update { it.copy(userId = v) }
    fun setPassword(v: String) = _uiState.update { it.copy(password = v) }
    fun setStbId(v: String) = _uiState.update { it.copy(stbId = v) }
    fun setIp(v: String) = _uiState.update { it.copy(ip = v) }
    fun setMac(v: String) = _uiState.update { it.copy(mac = v) }
    fun setAuthServerUrl(v: String) = _uiState.update { it.copy(authServerUrl = v) }
    fun setUdpxyProxy(v: String) = _uiState.update { it.copy(udpxyProxy = v) }
    fun setProxyBaseUrl(v: String) = _uiState.update { it.copy(proxyBaseUrl = v) }
    fun setProxyAccessToken(v: String) = _uiState.update { it.copy(proxyAccessToken = v) }

    fun testAndImport() {
        if (!importMutex.tryLock()) return
        viewModelScope.launch {
            try {
                when (_uiState.value.sourceMode) {
                    ChannelSourceMode.M3U8 -> importM3u8Url()
                    ChannelSourceMode.DIRECT_CTC -> importDirectCtc()
                    ChannelSourceMode.HOME_PROXY -> importHomeProxy()
                }
            } finally {
                importMutex.unlock()
            }
        }
    }

    fun loadDemoPlaylist() {
        if (!importMutex.tryLock()) return
        viewModelScope.launch {
            try {
                selectMode(ChannelSourceMode.M3U8)
                _uiState.update { it.copy(importStatus = ImportStatus.LoadingPlaylist) }
                val assetContent = context.assets.open("demo_playlist.m3u8").bufferedReader().use { it.readText() }
                val internalFile = File(context.filesDir, "demo_playlist.m3u8")
                internalFile.writeText(assetContent)
                loadPlaylistUseCase(internalFile.toUri()).fold(
                    onSuccess = { channels ->
                        val importedUri = internalFile.toUri().toString()
                        _uiState.update { state ->
                            state.copy(
                                playlistUrl = importedUri,
                                importStatus = ImportStatus.Success(channels.size),
                            )
                        }
                    },
                    onFailure = { t -> setFetchFailed(t.message.orEmpty()) },
                )
            } catch (e: java.io.IOException) {
                Timber.e(e, "Failed to load demo playlist")
                setFetchFailed(e.message.orEmpty())
            } finally {
                importMutex.unlock()
            }
        }
    }

    private suspend fun importM3u8Url() {
        val url = _uiState.value.playlistUrl.trim()
        val validation = UrlValidator.validate(url)
        if (validation.isFailure) {
            _uiState.update { it.copy(importStatus = ImportStatus.FetchFailed("invalid playlist URL")) }
            return
        }
        sourceSettingsStore.saveMode(ChannelSourceMode.M3U8)
        preferencesRepository.setUdpxyProxy(_uiState.value.udpxyProxy)
        _uiState.update { it.copy(importStatus = ImportStatus.LoadingPlaylist) }
        loadPlaylistUseCase(url.toUri()).fold(
            onSuccess = { channels ->
                _uiState.update { it.copy(importStatus = ImportStatus.Success(channels.size)) }
            },
            onFailure = { t -> setFetchFailed(t.message.orEmpty()) },
        )
    }

    private fun setFetchFailed(message: String) {
        _uiState.update { it.copy(importStatus = ImportStatus.FetchFailed(message)) }
    }

    private suspend fun importDirectCtc() {
        val creds = _uiState.value.asCredentials
        if (!creds.isComplete) {
            _uiState.update { it.copy(importStatus = ImportStatus.LoginFailed("incomplete credentials")) }
            return
        }
        credentialsStore.save(creds)
        sourceSettingsStore.saveMode(ChannelSourceMode.DIRECT_CTC)
        preferencesRepository.setUdpxyProxy(_uiState.value.udpxyProxy)
        _uiState.update { it.copy(importStatus = ImportStatus.LoggingIn) }
        _uiState.update { it.copy(importStatus = importChannelsUseCase().toStatus()) }
    }

    private suspend fun importHomeProxy() {
        val settings = _uiState.value.asProxySettings
        if (!settings.isComplete) {
            _uiState.update { it.copy(importStatus = ImportStatus.LoginFailed("proxy settings incomplete")) }
            return
        }
        sourceSettingsStore.saveProxySettings(settings)
        sourceSettingsStore.saveMode(ChannelSourceMode.HOME_PROXY)
        _uiState.update { it.copy(importStatus = ImportStatus.FetchingChannels) }
        _uiState.update { it.copy(importStatus = importChannelsUseCase().toStatus()) }
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
            sourceSettingsStore.clearProxySettings()
            _uiState.update {
                it.copy(
                    showClearConfirmation = false,
                    importStatus = ImportStatus.Idle,
                    proxyBaseUrl = "",
                    proxyAccessToken = "",
                )
            }
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
                udpxyProxy = it.udpxyProxy.ifBlank { UserPreferences.DEFAULT_UDPXY_PROXY },
            )
        }
    }

    private fun ImportResult.toStatus(): ImportStatus = when (this) {
        is ImportResult.Success -> ImportStatus.Success(importedCount)
        is ImportResult.LoginFailure -> ImportStatus.LoginFailed(reason)
        is ImportResult.FetchFailure -> ImportStatus.FetchFailed(reason)
        ImportResult.NoChannelsReturned -> ImportStatus.NoChannelsReturned
    }
}
