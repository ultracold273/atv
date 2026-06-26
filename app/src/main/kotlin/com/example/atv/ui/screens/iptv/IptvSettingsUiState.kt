package com.example.atv.ui.screens.iptv

import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.model.ProxySettings

data class IptvSettingsUiState(
    val sourceMode: ChannelSourceMode = ChannelSourceMode.DIRECT_CTC,
    val activeSourceMode: ChannelSourceMode = ChannelSourceMode.DIRECT_CTC,
    val playlistUrl: String = "",
    val userId: String = "",
    val password: String = "",
    val stbId: String = "",
    val ip: String = "",
    val mac: String = "",
    val authServerUrl: String = "",
    val udpxyProxy: String = "",
    val proxyBaseUrl: String = "",
    val proxyAccessToken: String = "",
    val proxyPairingStatus: ProxyPairingStatus = ProxyPairingStatus.Idle,
    val importStatus: ImportStatus = ImportStatus.Idle,
    val showClearConfirmation: Boolean = false,
) {
    val asCredentials: IptvCredentials
        get() = IptvCredentials(userId, password, stbId, ip, mac, authServerUrl)

    val asProxySettings: ProxySettings
        get() = ProxySettings(proxyBaseUrl, proxyAccessToken)

    val isFormValid: Boolean
        get() = !importStatus.isInProgress && when (sourceMode) {
            ChannelSourceMode.M3U8 -> playlistUrl.isNotBlank()
            ChannelSourceMode.DIRECT_CTC -> asCredentials.isComplete
            ChannelSourceMode.HOME_PROXY -> asProxySettings.isComplete
        }

    val canStartProxyPairing: Boolean
        get() = sourceMode == ChannelSourceMode.HOME_PROXY &&
            asProxySettings.copy(accessToken = "placeholder").isComplete &&
            !proxyPairingStatus.isInProgress
}

sealed class ProxyPairingStatus {
    object Idle : ProxyPairingStatus()
    object Creating : ProxyPairingStatus()
    data class Pending(
        val sessionId: String,
        val pairingCode: String,
        val expiresAt: Long,
    ) : ProxyPairingStatus()
    object Approved : ProxyPairingStatus()
    object Rejected : ProxyPairingStatus()
    object Expired : ProxyPairingStatus()
    object Cancelled : ProxyPairingStatus()
    data class Error(val reason: String) : ProxyPairingStatus()

    val isInProgress: Boolean
        get() = this is Creating || this is Pending
}

sealed class ImportStatus {
    object Idle : ImportStatus()
    object LoggingIn : ImportStatus()
    object FetchingChannels : ImportStatus()
    object LoadingPlaylist : ImportStatus()
    data class Success(val importedCount: Int) : ImportStatus()
    data class LoginFailed(val reason: String) : ImportStatus()
    data class FetchFailed(val reason: String) : ImportStatus()
    object NoChannelsReturned : ImportStatus()

    val isInProgress: Boolean
        get() = this is LoggingIn || this is FetchingChannels || this is LoadingPlaylist
}
