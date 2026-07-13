package com.example.atv.ui.screens.iptv.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.atv.R
import com.example.atv.ui.screens.iptv.IptvSettingsUiState

@Composable
fun M3u8SourceForm(
    playlistUrl: String,
    udpxyProxy: String,
    onPlaylistUrlChanged: (String) -> Unit,
    onUdpxyProxyChanged: (String) -> Unit,
) {
    IptvField(
        label = stringResource(R.string.channel_source_playlist_url),
        value = playlistUrl,
        onValueChange = onPlaylistUrlChanged,
        keyboardType = KeyboardType.Uri,
    )
    IptvField(
        label = stringResource(R.string.udpxy_proxy_label),
        value = udpxyProxy,
        onValueChange = onUdpxyProxyChanged,
        keyboardType = KeyboardType.Uri,
    )
}

@Composable
fun DirectCtcSourceForm(
    uiState: IptvSettingsUiState,
    onUserIdChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onStbIdChanged: (String) -> Unit,
    onIpChanged: (String) -> Unit,
    onMacChanged: (String) -> Unit,
    onAuthServerUrlChanged: (String) -> Unit,
    onUdpxyProxyChanged: (String) -> Unit,
) {
    IptvField(
        label = stringResource(R.string.iptv_field_user_id),
        value = uiState.userId,
        onValueChange = onUserIdChanged,
        keyboardType = KeyboardType.NumberPassword,
    )
    IptvField(
        label = stringResource(R.string.iptv_field_password),
        value = uiState.password,
        onValueChange = onPasswordChanged,
        keyboardType = KeyboardType.NumberPassword,
        isPassword = true,
    )
    IptvField(
        label = stringResource(R.string.iptv_field_stb_id),
        value = uiState.stbId,
        onValueChange = onStbIdChanged,
        keyboardType = KeyboardType.Ascii,
        supportingText = if (uiState.stbId.isNotEmpty() && uiState.stbId.length != 32) {
            stringResource(R.string.iptv_validation_stb_length)
        } else {
            null
        },
    )
    IptvField(
        label = stringResource(R.string.iptv_field_ip),
        value = uiState.ip,
        onValueChange = onIpChanged,
        keyboardType = KeyboardType.Number,
    )
    IptvField(
        label = stringResource(R.string.iptv_field_mac),
        value = uiState.mac,
        onValueChange = onMacChanged,
        keyboardType = KeyboardType.Ascii,
    )
    IptvField(
        label = stringResource(R.string.iptv_field_auth_server_url),
        value = uiState.authServerUrl,
        onValueChange = onAuthServerUrlChanged,
        keyboardType = KeyboardType.Uri,
    )
    IptvField(
        label = stringResource(R.string.udpxy_proxy_label),
        value = uiState.udpxyProxy,
        onValueChange = onUdpxyProxyChanged,
        keyboardType = KeyboardType.Uri,
    )
}

@Composable
fun HomeProxySourceForm(
    uiState: IptvSettingsUiState,
    onProxyBaseUrlChanged: (String) -> Unit,
    onProxyAccessTokenChanged: (String) -> Unit,
    onStartProxyPairing: () -> Unit,
    onCancelProxyPairing: () -> Unit,
) {
    IptvField(
        label = stringResource(R.string.channel_source_proxy_url),
        value = uiState.proxyBaseUrl,
        onValueChange = onProxyBaseUrlChanged,
        keyboardType = KeyboardType.Uri,
    )
    IptvActionButton(
        label = stringResource(R.string.proxy_pairing_generate_code),
        enabled = uiState.canStartProxyPairing,
        onClick = onStartProxyPairing,
    )
    ProxyPairingStatusView(
        status = uiState.proxyPairingStatus,
        onCancel = onCancelProxyPairing,
    )
    IptvField(
        label = stringResource(R.string.channel_source_proxy_token),
        value = uiState.proxyAccessToken,
        onValueChange = onProxyAccessTokenChanged,
        keyboardType = KeyboardType.Ascii,
        isPassword = true,
    )
}
