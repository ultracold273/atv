package com.example.atv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

/**
 * Shared focused button surface for Android TV controls.
 */
@Composable
fun AtvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AtvColors.Primary,
    contentColor: Color = color,
    containerColor: Color = color.copy(alpha = 0.2f),
    focusedContainerColor: Color = color.copy(alpha = 0.3f),
    focusedBorderColor: Color = color,
    height: Dp = 56.dp,
    fillMaxWidth: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    textStyle: TextStyle = AtvTypography.titleMedium,
    leadingText: String? = null
) {
    val sizedModifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier
    val effectiveContentColor = if (enabled) contentColor else AtvColors.OnSurfaceVariant

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = sizedModifier.height(height),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (enabled) containerColor else AtvColors.SurfaceVariant,
            focusedContainerColor = focusedContainerColor
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = focusedBorderColor),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = contentAlignment
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingText?.let {
                    Text(
                        text = it,
                        style = textStyle,
                        color = effectiveContentColor
                    )
                }
                Text(
                    text = label,
                    style = textStyle,
                    color = effectiveContentColor
                )
            }
        }
    }
}
