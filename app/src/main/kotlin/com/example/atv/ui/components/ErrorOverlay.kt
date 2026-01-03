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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

/**
 * Error overlay showing stream failure with retry/next options.
 */
@Composable
fun ErrorOverlay(
    message: String,
    visible: Boolean,
    onRetry: () -> Unit,
    onNextChannel: () -> Unit,
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
                .background(AtvColors.Background.copy(alpha = 0.8f))
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
                    .padding(32.dp)
                    .focusRequester(focusRequester),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Error icon
                Text(
                    text = "⚠️",
                    style = AtvTypography.displayLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.error_playback),
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.Error
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = message,
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Retry button
                    Surface(
                        onClick = onRetry,
                        modifier = Modifier
                            .width(140.dp)
                            .height(48.dp),
                        shape = ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(8.dp)
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AtvColors.Primary.copy(alpha = 0.2f),
                            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.4f)
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = AtvColors.Primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.retry),
                                style = AtvTypography.titleMedium,
                                color = AtvColors.Primary
                            )
                        }
                    }
                    
                    // Next channel button
                    Surface(
                        onClick = onNextChannel,
                        modifier = Modifier
                            .width(140.dp)
                            .height(48.dp),
                        shape = ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(8.dp)
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AtvColors.Secondary.copy(alpha = 0.2f),
                            focusedContainerColor = AtvColors.Secondary.copy(alpha = 0.4f)
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = AtvColors.Secondary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.next_channel),
                                style = AtvTypography.titleMedium,
                                color = AtvColors.Secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
