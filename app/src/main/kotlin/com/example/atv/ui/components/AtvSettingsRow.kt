package com.example.atv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

@Composable
fun AtvSettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val titleColor = if (isDestructive) AtvColors.Error else AtvColors.OnSurface
    val subtitleColor = if (isDestructive) {
        AtvColors.Error.copy(alpha = 0.7f)
    } else {
        AtvColors.OnSurfaceVariant
    }

    SettingsRowSurface(
        onClick = onClick,
        modifier = modifier,
        isDestructive = isDestructive
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = AtvTypography.titleMedium,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = AtvTypography.bodyMedium,
                color = subtitleColor
            )
        }
    }
}

@Composable
fun AtvToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsRowSurface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AtvTypography.titleMedium,
                    color = AtvColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AtvColors.Primary,
                    checkedTrackColor = AtvColors.Primary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun SettingsRowSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    content: @Composable () -> Unit
) {
    val focusColor = if (isDestructive) AtvColors.Error else AtvColors.Primary

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AtvColors.Surface,
            focusedContainerColor = focusColor.copy(alpha = 0.2f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = focusColor),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        content()
    }
}
