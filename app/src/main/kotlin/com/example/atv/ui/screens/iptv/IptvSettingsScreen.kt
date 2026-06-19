package com.example.atv.ui.screens.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

@Composable
fun IptvSettingsScreen(
    onBack: () -> Boolean,
    viewModel: IptvSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showClearConfirmation) {
        ClearCredentialsDialog(
            onConfirm = viewModel::confirmClearCredentials,
            onDismiss = viewModel::dismissClearDialog,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(onBack = { onBack() }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.iptv_setup_title),
                style = AtvTypography.headlineLarge,
                color = AtvColors.OnSurface,
            )
            Spacer(Modifier.height(8.dp))

            IptvField(
                label = stringResource(R.string.iptv_field_user_id),
                value = uiState.userId,
                onValueChange = viewModel::setUserId,
                keyboardType = KeyboardType.NumberPassword,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_password),
                value = uiState.password,
                onValueChange = viewModel::setPassword,
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_stb_id),
                value = uiState.stbId,
                onValueChange = viewModel::setStbId,
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
                onValueChange = viewModel::setIp,
                keyboardType = KeyboardType.Number,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_mac),
                value = uiState.mac,
                onValueChange = viewModel::setMac,
                keyboardType = KeyboardType.Ascii,
            )
            IptvField(
                label = stringResource(R.string.iptv_field_auth_server_url),
                value = uiState.authServerUrl,
                onValueChange = viewModel::setAuthServerUrl,
                keyboardType = KeyboardType.Uri,
            )

            Spacer(Modifier.height(8.dp))

            ActionButton(
                label = stringResource(R.string.iptv_action_test_and_import),
                enabled = uiState.isFormValid,
                onClick = viewModel::testAndImport,
            )

            StatusBlock(status = uiState.importStatus)

            Spacer(Modifier.height(16.dp))

            ActionButton(
                label = stringResource(R.string.iptv_action_clear_credentials),
                enabled = true,
                onClick = viewModel::requestClearCredentials,
                destructive = true,
            )
        }
    }
}

@Composable
private fun IptvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { M3Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supportingText?.let { { M3Text(it) } },
    )
}

@Composable
private fun StatusBlock(status: ImportStatus) {
    val text = when (status) {
        ImportStatus.Idle -> stringResource(R.string.iptv_status_idle)
        ImportStatus.LoggingIn -> stringResource(R.string.iptv_status_logging_in)
        ImportStatus.FetchingChannels -> stringResource(R.string.iptv_status_fetching_channels)
        is ImportStatus.Success -> stringResource(R.string.iptv_status_imported, status.importedCount)
        is ImportStatus.LoginFailed -> stringResource(R.string.iptv_status_login_failed, status.reason)
        is ImportStatus.FetchFailed -> stringResource(R.string.iptv_status_fetch_failed, status.reason)
        ImportStatus.NoChannelsReturned -> stringResource(R.string.iptv_status_no_channels)
    }
    val color = when (status) {
        is ImportStatus.LoginFailed, is ImportStatus.FetchFailed -> AtvColors.Error
        is ImportStatus.Success -> AtvColors.Primary
        else -> AtvColors.OnSurfaceVariant
    }
    Text(
        text = text,
        style = AtvTypography.bodyMedium,
        color = color,
    )
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val baseColor = if (destructive) AtvColors.Error else AtvColors.Primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (enabled) baseColor.copy(alpha = 0.2f) else AtvColors.SurfaceVariant,
            focusedContainerColor = baseColor.copy(alpha = 0.3f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = baseColor),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = AtvTypography.titleMedium,
                color = if (enabled) baseColor else AtvColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClearCredentialsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(400.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.iptv_clear_dialog_title),
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.iptv_clear_dialog_message),
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    ActionButton(
                        label = stringResource(R.string.cancel),
                        enabled = true,
                        onClick = onDismiss,
                    )
                    ActionButton(
                        label = stringResource(R.string.iptv_clear_dialog_confirm),
                        enabled = true,
                        onClick = onConfirm,
                        destructive = true,
                    )
                }
            }
        }
    }
}
