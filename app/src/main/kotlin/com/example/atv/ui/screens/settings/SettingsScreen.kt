package com.example.atv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.example.atv.BuildConfig
import com.example.atv.R
import com.example.atv.ui.components.AtvConfirmationDialog
import com.example.atv.ui.components.AtvDialog
import com.example.atv.ui.components.AtvDialogActions
import com.example.atv.ui.components.AtvDialogButton
import com.example.atv.ui.components.AtvSettingsRow
import com.example.atv.ui.components.AtvToggleRow
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

private const val MESSAGE_DISPLAY_MS = 3000L

/**
 * Route-level Settings entry point: collects state and wires ViewModel callbacks.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Boolean,
    onNavigateToChannelSource: () -> Unit,
    onManageChannels: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        appVersionName = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onNavigateToChannelSource = onNavigateToChannelSource,
        onManageChannels = onManageChannels,
        onSetEpgEnabled = viewModel::setEpgEnabled,
        onShowClearConfirmation = viewModel::showClearConfirmation,
        onShowAbout = viewModel::showAbout,
        onClearAllData = viewModel::clearAllData,
        onDismissDialog = viewModel::dismissDialog,
        onClearMessage = viewModel::clearMessage
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    appVersionName: String,
    onBack: () -> Boolean,
    onNavigateToChannelSource: () -> Unit,
    onManageChannels: () -> Unit,
    onSetEpgEnabled: (Boolean) -> Unit,
    onShowClearConfirmation: () -> Unit,
    onShowAbout: () -> Unit,
    onClearAllData: () -> Unit,
    onDismissDialog: () -> Unit,
    onClearMessage: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SettingsDialogs(
        uiState = uiState,
        onClearAllData = onClearAllData,
        onDismissDialog = onDismissDialog
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(onBack = { onBack() })
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = AtvTypography.headlineLarge,
                color = AtvColors.OnSurface
            )

            Spacer(modifier = Modifier.height(32.dp))

            SettingsStatsPanel(channelCount = uiState.channelCount)

            Spacer(modifier = Modifier.height(24.dp))

            SettingsOptions(
                uiState = uiState,
                appVersionName = appVersionName,
                onNavigateToChannelSource = onNavigateToChannelSource,
                onManageChannels = onManageChannels,
                onSetEpgEnabled = onSetEpgEnabled,
                onShowClearConfirmation = onShowClearConfirmation,
                onShowAbout = onShowAbout,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                firstItemModifier = Modifier.focusRequester(focusRequester)
            )
        }

        SettingsMessage(
            message = uiState.message,
            onClearMessage = onClearMessage,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SettingsDialogs(
    uiState: SettingsUiState,
    onClearAllData: () -> Unit,
    onDismissDialog: () -> Unit
) {
    if (uiState.showClearConfirmation) {
        AtvConfirmationDialog(
            title = stringResource(R.string.clear_all_data_title),
            message = stringResource(R.string.clear_all_data_message),
            confirmLabel = stringResource(R.string.clear_all),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = onClearAllData,
            onDismiss = onDismissDialog
        )
    }

    if (uiState.showAbout) {
        AboutDialog(onDismiss = onDismissDialog)
    }
}

@Composable
private fun SettingsStatsPanel(
    channelCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AtvColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            label = stringResource(R.string.channels),
            value = channelCount.toString()
        )
        StatItem(
            label = stringResource(R.string.status),
            value = if (channelCount > 0) {
                stringResource(R.string.status_ready)
            } else {
                stringResource(R.string.status_no_playlist)
            }
        )
    }
}

@Composable
private fun SettingsOptions(
    uiState: SettingsUiState,
    appVersionName: String,
    onNavigateToChannelSource: () -> Unit,
    onManageChannels: () -> Unit,
    onSetEpgEnabled: (Boolean) -> Unit,
    onShowClearConfirmation: () -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
    firstItemModifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AtvSettingsRow(
            title = stringResource(R.string.channel_source_title),
            subtitle = stringResource(R.string.channel_source_subtitle),
            onClick = onNavigateToChannelSource,
            modifier = firstItemModifier
        )

        AtvSettingsRow(
            title = stringResource(R.string.channel_management),
            subtitle = stringResource(R.string.manage_channels_subtitle),
            onClick = onManageChannels
        )

        AtvToggleRow(
            title = stringResource(R.string.epg_setting_title),
            subtitle = stringResource(R.string.epg_setting_subtitle),
            checked = uiState.epgEnabled,
            onCheckedChange = onSetEpgEnabled
        )

        AtvSettingsRow(
            title = stringResource(R.string.clear_all_data),
            subtitle = stringResource(R.string.clear_all_data_subtitle),
            onClick = onShowClearConfirmation,
            isDestructive = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        AtvSettingsRow(
            title = stringResource(R.string.about),
            subtitle = stringResource(R.string.about_subtitle, appVersionName),
            onClick = onShowAbout
        )
    }
}

@Composable
private fun SettingsMessage(
    message: String?,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    message?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(MESSAGE_DISPLAY_MS)
            onClearMessage()
        }

        Box(
            modifier = modifier
                .padding(32.dp)
                .background(AtvColors.Surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = it,
                style = AtvTypography.bodyMedium,
                color = AtvColors.OnSurface
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = AtvTypography.headlineMedium,
            color = AtvColors.Primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AtvTypography.bodySmall,
            color = AtvColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    AtvDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.app_title),
            style = AtvTypography.headlineLarge,
            color = AtvColors.Primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.app_subtitle),
            style = AtvTypography.titleMedium,
            color = AtvColors.OnSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
            style = AtvTypography.bodyMedium,
            color = AtvColors.OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.about_description),
            style = AtvTypography.bodyMedium,
            color = AtvColors.OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        AtvDialogActions {
            AtvDialogButton(
                label = stringResource(R.string.close),
                onClick = onDismiss
            )
        }
    }
}
