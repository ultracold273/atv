package com.example.atv.ui.screens.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.ui.screens.iptv.components.ClearCredentialsDialog
import com.example.atv.ui.screens.iptv.components.DirectCtcSourceForm
import com.example.atv.ui.screens.iptv.components.HomeProxySourceForm
import com.example.atv.ui.screens.iptv.components.ImportStatusView
import com.example.atv.ui.screens.iptv.components.IptvActionButton
import com.example.atv.ui.screens.iptv.components.M3u8SourceForm
import com.example.atv.ui.screens.iptv.components.SourceModeSelector
import com.example.atv.ui.testing.UiTestTags
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

@Composable
fun IptvSettingsScreen(
    onBack: () -> Boolean,
    onBackAfterSuccessfulImport: (() -> Boolean)? = null,
    initialMode: ChannelSourceMode? = null,
    viewModel: IptvSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialMode) {
        initialMode?.let(viewModel::selectMode)
    }

    if (uiState.showClearConfirmation) {
        ClearCredentialsDialog(
            onConfirm = viewModel::confirmClearCredentials,
            onDismiss = viewModel::dismissClearDialog,
        )
    }

    IptvSettingsContent(
        uiState = uiState,
        onBack = {
            if (uiState.importStatus is ImportStatus.Success) {
                onBackAfterSuccessfulImport?.invoke() ?: onBack()
            } else {
                onBack()
            }
        },
        onModeSelected = viewModel::selectMode,
        onPlaylistUrlChanged = viewModel::setPlaylistUrl,
        onUserIdChanged = viewModel::setUserId,
        onPasswordChanged = viewModel::setPassword,
        onStbIdChanged = viewModel::setStbId,
        onIpChanged = viewModel::setIp,
        onMacChanged = viewModel::setMac,
        onAuthServerUrlChanged = viewModel::setAuthServerUrl,
        onUdpxyProxyChanged = viewModel::setUdpxyProxy,
        onProxyBaseUrlChanged = viewModel::setProxyBaseUrl,
        onProxyAccessTokenChanged = viewModel::setProxyAccessToken,
        onStartProxyPairing = viewModel::startProxyPairing,
        onCancelProxyPairing = viewModel::cancelProxyPairing,
        onTestAndImport = viewModel::testAndImport,
        onLoadDemoPlaylist = viewModel::loadDemoPlaylist,
        onRequestClearCredentials = viewModel::requestClearCredentials,
    )
}

@Composable
private fun IptvSettingsContent(
    uiState: IptvSettingsUiState,
    onBack: () -> Boolean,
    onModeSelected: (ChannelSourceMode) -> Unit,
    onPlaylistUrlChanged: (String) -> Unit,
    onUserIdChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onStbIdChanged: (String) -> Unit,
    onIpChanged: (String) -> Unit,
    onMacChanged: (String) -> Unit,
    onAuthServerUrlChanged: (String) -> Unit,
    onUdpxyProxyChanged: (String) -> Unit,
    onProxyBaseUrlChanged: (String) -> Unit,
    onProxyAccessTokenChanged: (String) -> Unit,
    onStartProxyPairing: () -> Unit,
    onCancelProxyPairing: () -> Unit,
    onTestAndImport: () -> Unit,
    onLoadDemoPlaylist: () -> Unit,
    onRequestClearCredentials: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.IptvSettingsScreen)
            .background(AtvColors.Background)
            .handleDPadKeyEvents(onBack = onBack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.channel_source_title),
                style = AtvTypography.headlineLarge,
                color = AtvColors.OnSurface,
            )
            Spacer(Modifier.height(8.dp))

            SourceModeSelector(
                selected = uiState.sourceMode,
                active = uiState.activeSourceMode,
                onSelected = onModeSelected,
            )

            when (uiState.sourceMode) {
                ChannelSourceMode.M3U8 -> M3u8SourceForm(
                    playlistUrl = uiState.playlistUrl,
                    udpxyProxy = uiState.udpxyProxy,
                    onPlaylistUrlChanged = onPlaylistUrlChanged,
                    onUdpxyProxyChanged = onUdpxyProxyChanged,
                )
                ChannelSourceMode.DIRECT_CTC -> DirectCtcSourceForm(
                    uiState = uiState,
                    onUserIdChanged = onUserIdChanged,
                    onPasswordChanged = onPasswordChanged,
                    onStbIdChanged = onStbIdChanged,
                    onIpChanged = onIpChanged,
                    onMacChanged = onMacChanged,
                    onAuthServerUrlChanged = onAuthServerUrlChanged,
                    onUdpxyProxyChanged = onUdpxyProxyChanged,
                )
                ChannelSourceMode.HOME_PROXY -> HomeProxySourceForm(
                    uiState = uiState,
                    onProxyBaseUrlChanged = onProxyBaseUrlChanged,
                    onProxyAccessTokenChanged = onProxyAccessTokenChanged,
                    onStartProxyPairing = onStartProxyPairing,
                    onCancelProxyPairing = onCancelProxyPairing,
                )
            }

            Spacer(Modifier.height(8.dp))

            IptvActionButton(
                label = when (uiState.sourceMode) {
                    ChannelSourceMode.M3U8 -> stringResource(R.string.channel_source_import_playlist)
                    ChannelSourceMode.DIRECT_CTC -> stringResource(R.string.iptv_action_test_and_import)
                    ChannelSourceMode.HOME_PROXY -> stringResource(R.string.iptv_action_test_and_import)
                },
                enabled = uiState.isFormValid,
                onClick = onTestAndImport,
            )

            if (uiState.sourceMode == ChannelSourceMode.M3U8) {
                IptvActionButton(
                    label = stringResource(R.string.load_demo_playlist),
                    enabled = !uiState.importStatus.isInProgress,
                    onClick = onLoadDemoPlaylist,
                )
            }

            ImportStatusView(status = uiState.importStatus)

            Spacer(Modifier.height(16.dp))

            IptvActionButton(
                label = stringResource(R.string.iptv_action_clear_credentials),
                enabled = true,
                onClick = onRequestClearCredentials,
                destructive = true,
            )
        }
    }
}
