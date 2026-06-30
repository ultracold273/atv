package com.example.atv.ui.screens.iptv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.screens.iptv.ImportStatus
import com.example.atv.ui.screens.iptv.ProxyPairingStatus
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProxyPairingStatusView(
    status: ProxyPairingStatus,
    onCancel: () -> Unit,
) {
    when (status) {
        ProxyPairingStatus.Idle -> Unit
        ProxyPairingStatus.Creating -> Text(
            text = stringResource(R.string.proxy_pairing_creating),
            style = AtvTypography.bodyMedium,
            color = AtvColors.OnSurfaceVariant,
        )
        is ProxyPairingStatus.Pending -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatPairingCode(status.pairingCode),
                    style = AtvTypography.headlineLarge,
                    color = AtvColors.Primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.proxy_pairing_pending),
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.proxy_pairing_expires_at, formatPairingExpiry(status.expiresAt)),
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant,
                )
                IptvActionButton(
                    label = stringResource(R.string.proxy_pairing_cancel),
                    enabled = true,
                    onClick = onCancel,
                )
            }
        }
        ProxyPairingStatus.Approved -> Text(
            text = stringResource(R.string.proxy_pairing_approved),
            style = AtvTypography.bodyMedium,
            color = AtvColors.Primary,
        )
        ProxyPairingStatus.Rejected -> Text(
            text = stringResource(R.string.proxy_pairing_rejected),
            style = AtvTypography.bodyMedium,
            color = AtvColors.Error,
        )
        ProxyPairingStatus.Expired -> Text(
            text = stringResource(R.string.proxy_pairing_expired),
            style = AtvTypography.bodyMedium,
            color = AtvColors.Error,
        )
        ProxyPairingStatus.Cancelled -> Text(
            text = stringResource(R.string.proxy_pairing_cancelled),
            style = AtvTypography.bodyMedium,
            color = AtvColors.OnSurfaceVariant,
        )
        is ProxyPairingStatus.Error -> Text(
            text = stringResource(R.string.proxy_pairing_error, status.reason),
            style = AtvTypography.bodyMedium,
            color = AtvColors.Error,
        )
    }
}

@Composable
fun ImportStatusView(status: ImportStatus) {
    val text = when (status) {
        ImportStatus.Idle -> stringResource(R.string.iptv_status_idle)
        ImportStatus.LoggingIn -> stringResource(R.string.iptv_status_logging_in)
        ImportStatus.FetchingChannels -> stringResource(R.string.iptv_status_fetching_channels)
        ImportStatus.LoadingPlaylist -> stringResource(R.string.loading_playlist)
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

private fun formatPairingCode(code: String): String = code
    .filter { it.isDigit() }
    .chunked(3)
    .joinToString(" ")

private fun formatPairingExpiry(expiresAt: Long): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(expiresAt))
}.getOrDefault("--:--")
