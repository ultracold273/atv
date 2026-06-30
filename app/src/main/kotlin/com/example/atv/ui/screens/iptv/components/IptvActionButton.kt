package com.example.atv.ui.screens.iptv.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.atv.ui.components.AtvButton
import com.example.atv.ui.theme.AtvColors

@Composable
fun IptvActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    fillMaxWidth: Boolean = true,
) {
    val baseColor = if (destructive) AtvColors.Error else AtvColors.Primary
    AtvButton(
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        color = baseColor,
        contentColor = baseColor,
        fillMaxWidth = fillMaxWidth,
    )
}
