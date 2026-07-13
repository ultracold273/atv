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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.testing.UiTestTags
import kotlinx.coroutines.delay

private const val FOCUS_REQUEST_DELAY_MS = 100L

/**
 * On-screen number pad for direct channel selection.
 */
@Composable
fun NumberPadOverlay(
    input: String,
    visible: Boolean,
    maxChannels: Int,
    onDigitPressed: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit = {},
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
                .testTag(UiTestTags.NumberPadOverlay)
                .background(AtvColors.Background.copy(alpha = 0.7f))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val digit = event.key.asNumberPadDigit()
                        if (digit != null) {
                            onDigitPressed(digit)
                            onUserInteraction()
                            true
                        } else when (event.key) {
                            Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                onConfirm()
                                onUserInteraction()
                                true
                            }
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
            // Focus requester for the center button (digit 5)
            val centerButtonFocusRequester = remember { FocusRequester() }
            
            LaunchedEffect(visible) {
                if (visible) {
                    // Delay to prevent SELECT key from triggering click on focused button
                    delay(FOCUS_REQUEST_DELAY_MS)
                    centerButtonFocusRequester.requestFocus()
                }
            }
            
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AtvColors.Surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.enter_channel_number),
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
                        text = input.ifEmpty { "-" },
                        modifier = Modifier.testTag(UiTestTags.NumberPadInput),
                        style = AtvTypography.displayMedium,
                        color = if (input.isEmpty()) AtvColors.OnSurfaceVariant else AtvColors.Primary,
                        textAlign = TextAlign.Center
                    )
                }
                
                Text(
                    text = stringResource(R.string.channel_range, maxChannels),
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
                        NumberButton("1", onDigitPressed, onUserInteraction)
                        NumberButton("2", onDigitPressed, onUserInteraction)
                        NumberButton("3", onDigitPressed, onUserInteraction)
                    }
                    // Row 4-6 (5 gets initial focus)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberButton("4", onDigitPressed, onUserInteraction)
                        NumberButton(
                            digit = "5",
                            onClick = onDigitPressed,
                            onUserInteraction = onUserInteraction,
                            modifier = Modifier.focusRequester(centerButtonFocusRequester)
                        )
                        NumberButton("6", onDigitPressed, onUserInteraction)
                    }
                    // Row 7-9
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberButton("7", onDigitPressed, onUserInteraction)
                        NumberButton("8", onDigitPressed, onUserInteraction)
                        NumberButton("9", onDigitPressed, onUserInteraction)
                    }
                    // Row CLR-0-GO
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            stringResource(R.string.clear),
                            AtvColors.OnSurfaceVariant,
                            onBackspace,
                            onUserInteraction
                        )
                        NumberButton("0", onDigitPressed, onUserInteraction)
                        ActionButton(stringResource(R.string.go), AtvColors.Secondary, onConfirm, onUserInteraction)
                    }
                }
            }
        }
    }
}

private fun Key.asNumberPadDigit(): String? = when (this) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}

@Composable
private fun NumberButton(
    digit: String,
    onClick: (String) -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(digit) },
        modifier = modifier
            .size(72.dp)
            .testTag("${UiTestTags.NumberPadButtonPrefix}-$digit")
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onUserInteraction()
                }
            },
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
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .testTag(if (label == stringResource(R.string.go)) UiTestTags.NumberPadGoButton else label)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onUserInteraction()
                }
            },
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
