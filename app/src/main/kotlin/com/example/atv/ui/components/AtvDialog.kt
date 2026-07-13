package com.example.atv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

@Composable
fun AtvDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 400.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(width)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun AtvConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 350.dp
) {
    AtvDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        width = width
    ) {
        Text(
            text = title,
            style = AtvTypography.headlineMedium,
            color = AtvColors.OnSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = AtvTypography.bodyLarge,
            color = AtvColors.OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        AtvDialogActions {
            AtvDialogButton(
                label = dismissLabel,
                onClick = onDismiss
            )
            AtvDialogButton(
                label = confirmLabel,
                onClick = onConfirm,
                isDestructive = true
            )
        }
    }
}

@Composable
fun AtvDialogActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
        content()
    }
}

@Composable
fun AtvDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    AtvButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        color = if (isDestructive) AtvColors.Error else AtvColors.Primary,
        containerColor = if (isDestructive) {
            AtvColors.Error.copy(alpha = 0.1f)
        } else {
            AtvColors.Primary.copy(alpha = 0.1f)
        },
        focusedBorderWidth = 1.dp,
        height = 40.dp,
        textStyle = AtvTypography.labelMedium
    )
}
