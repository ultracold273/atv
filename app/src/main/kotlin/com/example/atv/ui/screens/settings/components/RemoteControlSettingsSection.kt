package com.example.atv.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.data.remotecontrol.ControlListenerStatus
import com.example.atv.data.remotecontrol.RemoteControlState
import com.example.atv.ui.components.AtvConfirmationDialog
import com.example.atv.ui.components.AtvDialog
import com.example.atv.ui.components.AtvDialogButton
import com.example.atv.ui.components.AtvSettingsRow
import com.example.atv.ui.components.AtvToggleRow
import com.example.atv.ui.screens.settings.RemoteControlViewModel
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

@Composable
fun RemoteControlSettingsSection(viewModel: RemoteControlViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemoteControlSettingsContent(state, viewModel::setEnabled, viewModel::rotateCredential, viewModel::retry)
}

@Composable
private fun RemoteControlSettingsContent(
    state: RemoteControlState,
    onEnabled: (Boolean) -> Unit,
    onRotate: () -> Unit,
    onRetry: () -> Unit,
) {
    var showConnection by remember { mutableStateOf(false) }
    var confirmRotation by remember { mutableStateOf(false) }
    AtvToggleRow(
        title = stringResource(R.string.remote_control_title),
        subtitle = stringResource(R.string.remote_control_description),
        checked = state.enabled,
        onCheckedChange = onEnabled,
        modifier = Modifier.testTag("remote_control_toggle"),
    )
    if (state.enabled) {
        Text(
            text = stringResource(statusText(state.status)),
            style = AtvTypography.bodySmall,
            color = if (state.status == ControlListenerStatus.ERROR) AtvColors.Error else AtvColors.OnSurfaceVariant,
            modifier = Modifier.testTag("remote_control_status"),
        )
        if (state.status == ControlListenerStatus.ERROR) {
            AtvSettingsRow(
                title = stringResource(R.string.retry),
                subtitle = stringResource(R.string.remote_control_retry_hint),
                onClick = onRetry,
            )
        }
        if (state.fingerprint.isNotBlank()) {
            AtvSettingsRow(
                title = stringResource(R.string.remote_control_connection),
                subtitle = stringResource(R.string.remote_control_connection_hint),
                onClick = { showConnection = true },
                modifier = Modifier.testTag("remote_control_connection"),
            )
            AtvSettingsRow(
                title = stringResource(R.string.remote_control_rotate),
                subtitle = stringResource(R.string.remote_control_rotate_hint),
                onClick = { confirmRotation = true },
            )
        }
    }
    if (showConnection && state.enabled) ConnectionDialog(state) { showConnection = false }
    if (confirmRotation && state.enabled) {
        AtvConfirmationDialog(
            title = stringResource(R.string.remote_control_rotate),
            message = stringResource(R.string.remote_control_rotate_hint),
            confirmLabel = stringResource(R.string.remote_control_rotate),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { confirmRotation = false; onRotate() },
            onDismiss = { confirmRotation = false },
        )
    }
}

@Composable
private fun ConnectionDialog(state: RemoteControlState, onDismiss: () -> Unit) {
    AtvDialog(onDismissRequest = onDismiss, width = 620.dp) {
        val firstField = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstField.requestFocus() }
        Column(
            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.remote_control_connection), style = AtvTypography.titleLarge)
            Text(stringResource(R.string.remote_control_connection_instructions), style = AtvTypography.bodySmall)
            ConnectionField(
                stringResource(R.string.remote_control_address),
                state.endpoints.joinToString(System.lineSeparator())
                    .ifEmpty { stringResource(R.string.remote_control_no_network) },
                modifier = Modifier.testTag("remote_control_address").focusRequester(firstField),
            )
            ConnectionField(
                stringResource(R.string.remote_control_token), state.token, Modifier.testTag("remote_control_token")
            )
            ConnectionField(
                stringResource(R.string.remote_control_fingerprint), state.fingerprint,
                Modifier.testTag("remote_control_fingerprint")
            )
            AtvDialogButton(stringResource(R.string.close), onDismiss)
        }
    }
}

@Composable
private fun ConnectionField(label: String, value: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    // Read-only fields are focus targets so D-pad navigation can scroll every connection detail into view.
    Column(
        modifier = modifier.fillMaxWidth()
            .border(2.dp, if (focused) AtvColors.FocusRing else AtvColors.SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Text(label, style = AtvTypography.labelMedium, color = AtvColors.Primary)
        Text(value, style = AtvTypography.bodySmall, fontFamily = FontFamily.Monospace, color = AtvColors.OnSurface)
    }
}

private fun statusText(status: ControlListenerStatus): Int = when (status) {
    ControlListenerStatus.DISABLED -> R.string.remote_control_disabled
    ControlListenerStatus.STARTING -> R.string.remote_control_starting
    ControlListenerStatus.RUNNING -> R.string.remote_control_running
    ControlListenerStatus.PAUSED -> R.string.remote_control_paused
    ControlListenerStatus.ERROR -> R.string.remote_control_error
}
