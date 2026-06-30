package com.example.atv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.atv.ui.theme.AtvColors

/**
 * Shared translucent panel treatment for playback overlays.
 */
@Composable
fun AtvOverlayPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    containerColor: Color = AtvColors.Surface.copy(alpha = 0.95f),
    padding: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(padding)
    ) {
        content()
    }
}
