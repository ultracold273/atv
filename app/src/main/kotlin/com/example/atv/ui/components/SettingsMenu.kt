package com.example.atv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
 * Settings menu overlay.
 * Slides in from the right side of the screen.
 */
@Composable
fun SettingsMenu(
    visible: Boolean,
    onLoadPlaylist: () -> Unit,
    onManageChannels: () -> Unit,
    onClearPlaylist: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back, Key.DirectionRight -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            val focusRequester = remember { FocusRequester() }
            
            LaunchedEffect(visible) {
                if (visible) {
                    focusRequester.requestFocus()
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(AtvColors.Surface.copy(alpha = 0.95f))
                    .padding(24.dp)
                    .focusRequester(focusRequester),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_menu),
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                SettingsMenuItem(
                    icon = "📂",
                    label = stringResource(R.string.load_playlist),
                    onClick = onLoadPlaylist
                )
                
                SettingsMenuItem(
                    icon = "📺",
                    label = stringResource(R.string.channel_management),
                    onClick = onManageChannels
                )
                
                SettingsMenuItem(
                    icon = "🗑️",
                    label = stringResource(R.string.clear_playlist),
                    onClick = onClearPlaylist,
                    isDestructive = true
                )
                
                SettingsMenuItem(
                    icon = "⚙️",
                    label = stringResource(R.string.all_settings),
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textColor = if (isDestructive) AtvColors.Error else AtvColors.OnSurface
    
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AtvColors.SurfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = if (isDestructive) {
                AtvColors.Error.copy(alpha = 0.2f)
            } else {
                AtvColors.Primary.copy(alpha = 0.2f)
            }
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = if (isDestructive) AtvColors.Error else AtvColors.Primary
                ),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "$icon  $label",
                style = AtvTypography.titleMedium,
                color = textColor
            )
        }
    }
}
