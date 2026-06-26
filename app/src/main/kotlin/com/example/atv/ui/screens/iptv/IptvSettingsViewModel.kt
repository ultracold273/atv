package com.example.atv.ui.screens.iptv

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.data.proxy.ProxyPairingClient
import com.example.atv.data.proxy.ProxyPairingCreateRequestDto
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.ProxySettings
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.io.File
import java.security.SecureRandom
import java.util.Base64
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
    private val proxyPairingClient: ProxyPairingClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IptvSettingsUiState())
    val uiState: StateFlow<IptvSettingsUiState> = _uiState.asStateFlow()

    private val importMutex = Mutex()
    private val secureRandom = SecureRandom()
    private var pairingJob: Job? = null
    private var activePairingId: String? = null

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
                    activeSourceMode = mode,
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
    fun setProxyAccessToken(v: String) {
        cancelProxyPairing(markCancelled = false)
        _uiState.update { it.copy(proxyAccessToken = v, proxyPairingStatus = ProxyPairingStatus.Idle) }
    }

    fun startProxyPairing() {
        val proxyBaseUrl = _uiState.value.proxyBaseUrl.trim()
        val validation = ProxySettings(proxyBaseUrl, "placeholder")
        if (!validation.isComplete) {
            _uiState.update { it.copy(proxyPairingStatus = ProxyPairingStatus.Error("invalid proxy URL")) }
            return
        }
        cancelProxyPairing(markCancelled = false)
        val pairingId = newPairingId()
        val clientNonce = newClientNonce()
        activePairingId = pairingId
        pairingJob = viewModelScope.launch {
            _uiState.update { it.copy(proxyPairingStatus = ProxyPairingStatus.Creating) }
            val createRequest = ProxyPairingCreateRequestDto(
                deviceName = deviceName(),
                appId = context.packageName,
                appVersion = appVersion(),
                clientNonce = clientNonce,
            )
            proxyPairingClient.createSession(proxyBaseUrl, createRequest).fold(
                onSuccess = { response ->
                    if (!isActivePairing(pairingId)) return@launch
                    _uiState.update {
                        it.copy(
                            proxyPairingStatus = ProxyPairingStatus.Pending(
                                sessionId = response.sessionId,
                                pairingCode = response.pairingCode,
                                expiresAt = response.expiresAt,
                            ),
                        )
                    }
                    pollProxyPairing(
                        pairingId = pairingId,
                        proxyBaseUrl = proxyBaseUrl,
                        sessionId = response.sessionId,
                        clientNonce = clientNonce,
                        intervalSeconds = response.pollIntervalSeconds,
                    )
                },
                onFailure = { t ->
                    if (isActivePairing(pairingId)) {
                        _uiState.update { it.copy(proxyPairingStatus = ProxyPairingStatus.Error(t.message.orEmpty())) }
                    }
                },
            )
        }
    }

    fun cancelProxyPairing() = cancelProxyPairing(markCancelled = true)

    fun testAndImport() {
        if (!importMutex.tryLock()) return
        cancelProxyPairing(markCancelled = false)
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
                        sourceSettingsStore.saveMode(ChannelSourceMode.M3U8)
                        _uiState.update { state ->
                            state.copy(
                                playlistUrl = importedUri,
                                activeSourceMode = ChannelSourceMode.M3U8,
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
                sourceSettingsStore.saveMode(ChannelSourceMode.M3U8)
                _uiState.update {
                    it.copy(
                        activeSourceMode = ChannelSourceMode.M3U8,
                        importStatus = ImportStatus.Success(channels.size),
                    )
                }
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
        preferencesRepository.setUdpxyProxy(_uiState.value.udpxyProxy)
        _uiState.update { it.copy(importStatus = ImportStatus.LoggingIn) }
        val status = importChannelsUseCase(ChannelSourceMode.DIRECT_CTC).toStatus()
        if (status is ImportStatus.Success) {
            sourceSettingsStore.saveMode(ChannelSourceMode.DIRECT_CTC)
        }
        _uiState.update { state ->
            state.copy(
                activeSourceMode = state.activeModeAfterSuccess(status, ChannelSourceMode.DIRECT_CTC),
                importStatus = status,
            )
        }
    }

    private suspend fun importHomeProxy() {
        val settings = _uiState.value.asProxySettings
        if (!settings.isComplete) {
            _uiState.update { it.copy(importStatus = ImportStatus.LoginFailed("proxy settings incomplete")) }
            return
        }
        sourceSettingsStore.saveProxySettings(settings)
        _uiState.update { it.copy(importStatus = ImportStatus.FetchingChannels) }
        val status = importChannelsUseCase(ChannelSourceMode.HOME_PROXY).toStatus()
        if (status is ImportStatus.Success) {
            sourceSettingsStore.saveMode(ChannelSourceMode.HOME_PROXY)
        }
        _uiState.update { state ->
            state.copy(
                activeSourceMode = state.activeModeAfterSuccess(status, ChannelSourceMode.HOME_PROXY),
                importStatus = status,
            )
        }
    }

    private fun IptvSettingsUiState.activeModeAfterSuccess(
        status: ImportStatus,
        mode: ChannelSourceMode,
    ): ChannelSourceMode = if (status is ImportStatus.Success) mode else activeSourceMode

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
                    proxyPairingStatus = ProxyPairingStatus.Idle,
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

    private suspend fun pollProxyPairing(
        pairingId: String,
        proxyBaseUrl: String,
        sessionId: String,
        clientNonce: String,
        intervalSeconds: Long,
    ) {
        var nextInterval = intervalSeconds.coerceIn(MIN_PAIRING_POLL_SECONDS, MAX_PAIRING_POLL_SECONDS)
        while (isActivePairing(pairingId)) {
            delay(nextInterval * MILLIS_PER_SECOND)
            if (!isActivePairing(pairingId)) return
            proxyPairingClient.pollSession(proxyBaseUrl, sessionId, clientNonce).fold(
                onSuccess = { response ->
                    if (!isActivePairing(pairingId)) return
                    when (response.status) {
                        "pending" -> {
                            nextInterval = (response.pollIntervalSeconds ?: nextInterval)
                                .coerceIn(MIN_PAIRING_POLL_SECONDS, MAX_PAIRING_POLL_SECONDS)
                            _uiState.update { state ->
                                state.copy(
                                    proxyPairingStatus = ProxyPairingStatus.Pending(
                                        sessionId = sessionId,
                                        pairingCode = (state.proxyPairingStatus as? ProxyPairingStatus.Pending)
                                            ?.pairingCode
                                            .orEmpty(),
                                        expiresAt = response.expiresAt
                                            ?: (state.proxyPairingStatus as? ProxyPairingStatus.Pending)?.expiresAt
                                            ?: 0L,
                                    ),
                                )
                            }
                        }
                        "approved" -> handlePairingApproved(pairingId, proxyBaseUrl, response.accessToken, response.tokenType)
                        "rejected" -> finishPairing(pairingId, ProxyPairingStatus.Rejected)
                        "expired" -> finishPairing(pairingId, ProxyPairingStatus.Expired)
                        else -> finishPairing(pairingId, ProxyPairingStatus.Error("unknown pairing status"))
                    }
                },
                onFailure = { t ->
                    if (isActivePairing(pairingId)) {
                        finishPairing(pairingId, ProxyPairingStatus.Error(t.message.orEmpty()))
                    }
                },
            )
        }
    }

    private suspend fun handlePairingApproved(
        pairingId: String,
        proxyBaseUrl: String,
        accessToken: String?,
        tokenType: String?,
    ) {
        if (accessToken.isNullOrBlank() || !tokenType.equals("Bearer", ignoreCase = true)) {
            finishPairing(pairingId, ProxyPairingStatus.Error("pairing response missing token"))
            return
        }
        val settings = ProxySettings(proxyBaseUrl, accessToken)
        if (!settings.isComplete) {
            finishPairing(pairingId, ProxyPairingStatus.Error("pairing response invalid"))
            return
        }
        sourceSettingsStore.saveProxySettings(settings)
        sourceSettingsStore.saveMode(ChannelSourceMode.HOME_PROXY)
        activePairingId = null
        _uiState.update {
            it.copy(
                proxyAccessToken = accessToken,
                activeSourceMode = ChannelSourceMode.HOME_PROXY,
                proxyPairingStatus = ProxyPairingStatus.Approved,
            )
        }
    }

    private fun finishPairing(pairingId: String, status: ProxyPairingStatus) {
        if (!isActivePairing(pairingId)) return
        activePairingId = null
        _uiState.update { it.copy(proxyPairingStatus = status) }
    }

    private fun cancelProxyPairing(markCancelled: Boolean) {
        val hadActivePairing = activePairingId != null
        activePairingId = null
        pairingJob?.cancel()
        pairingJob = null
        if (markCancelled && hadActivePairing) {
            _uiState.update { it.copy(proxyPairingStatus = ProxyPairingStatus.Cancelled) }
        }
    }

    private fun isActivePairing(pairingId: String): Boolean = activePairingId == pairingId

    private fun newPairingId(): String = newClientNonce()

    private fun newClientNonce(): String {
        val bytes = ByteArray(CLIENT_NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun deviceName(): String {
        val configuredName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        if (!configuredName.isNullOrBlank()) return configuredName
        return listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android TV" }
    }

    private fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty().ifBlank { "unknown" }
    }.getOrDefault("unknown")

    override fun onCleared() {
        cancelProxyPairing(markCancelled = false)
        super.onCleared()
    }

    private companion object {
        const val CLIENT_NONCE_BYTES = 32
        const val MIN_PAIRING_POLL_SECONDS = 2L
        const val MAX_PAIRING_POLL_SECONDS = 10L
        const val MILLIS_PER_SECOND = 1000L
    }
}
