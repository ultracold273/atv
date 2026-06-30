package com.example.atv.ui.screens.iptv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

@Composable
fun ClearCredentialsDialog(
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
                    IptvActionButton(
                        label = stringResource(R.string.cancel),
                        enabled = true,
                        onClick = onDismiss,
                        fillMaxWidth = false,
                    )
                    IptvActionButton(
                        label = stringResource(R.string.iptv_clear_dialog_confirm),
                        enabled = true,
                        onClick = onConfirm,
                        destructive = true,
                        fillMaxWidth = false,
                    )
                }
            }
        }
    }
}
