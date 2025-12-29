package com.example.atv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

/**
 * On-screen number pad for direct channel selection.
 */
@Composable
fun NumberPadOverlay(
    input: String,
    visible: Boolean,
    maxChannels: Int,
    onDigitPressed: (String) -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AtvColors.Background.copy(alpha = 0.7f))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            val focusRequester = remember { FocusRequester() }
            
            LaunchedEffect(visible) {
                if (visible) {
                    focusRequester.requestFocus()
                }
            }
            
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AtvColors.Surface)
                    .padding(24.dp)
                    .focusRequester(focusRequester),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enter Channel Number",
                    style = AtvTypography.titleLarge,
                    color = AtvColors.OnSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Display input
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AtvColors.SurfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = input.ifEmpty { "---" },
                        style = AtvTypography.displayMedium,
                        color = if (input.isEmpty()) AtvColors.OnSurfaceVariant else AtvColors.Primary,
                        textAlign = TextAlign.Center
                    )
                }
                
                Text(
                    text = "1 - $maxChannels",
                    style = AtvTypography.labelMedium,
                    color = AtvColors.OnSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Number grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1-3
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberButton("1", onDigitPressed)
                        NumberButton("2", onDigitPressed)
                        NumberButton("3", onDigitPressed)
                    }
                    // Row 4-6
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberButton("4", onDigitPressed)
                        NumberButton("5", onDigitPressed)
                        NumberButton("6", onDigitPressed)
                    }
                    // Row 7-9
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberButton("7", onDigitPressed)
                        NumberButton("8", onDigitPressed)
                        NumberButton("9", onDigitPressed)
                    }
                    // Row Clear-0-Go
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("CLR", AtvColors.Warning, onClear)
                        NumberButton("0", onDigitPressed)
                        ActionButton("GO", AtvColors.Secondary, onConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberButton(
    digit: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(digit) },
        modifier = modifier.size(72.dp),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AtvColors.SurfaceVariant,
            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.3f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = AtvColors.FocusRing
                ),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit,
                style = AtvTypography.headlineLarge,
                color = AtvColors.OnSurface
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(72.dp),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color.copy(alpha = 0.2f),
            focusedContainerColor = color.copy(alpha = 0.4f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = color
                ),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AtvTypography.titleMedium,
                color = color
            )
        }
    }
}
