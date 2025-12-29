package com.example.atv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

/**
 * Settings screen for managing app preferences and playlists.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoadNewPlaylist: () -> Unit,
    onManageChannels: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Clear confirmation dialog
    if (uiState.showClearConfirmation) {
        ConfirmationDialog(
            title = "Clear All Data?",
            message = "This will remove all channels and reset preferences. This action cannot be undone.",
            confirmLabel = "Clear All",
            onConfirm = { viewModel.clearAllData() },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
    
    // About dialog
    if (uiState.showAbout) {
        AboutDialog(
            onDismiss = { viewModel.dismissDialog() }
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(
                onBack = { onBack() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Header
            Text(
                text = "Settings",
                style = AtvTypography.headlineLarge,
                color = AtvColors.OnSurface
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Stats section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AtvColors.Surface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Channels",
                    value = uiState.channelCount.toString()
                )
                StatItem(
                    label = "Status",
                    value = if (uiState.channelCount > 0) "Ready" else "No Playlist"
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings options
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsItem(
                    title = "Load New Playlist",
                    subtitle = "Replace current channels with a new M3U/M3U8 file",
                    onClick = onLoadNewPlaylist,
                    modifier = Modifier.focusRequester(focusRequester)
                )
                
                SettingsItem(
                    title = "Manage Channels",
                    subtitle = "Add, edit, or delete individual channels",
                    onClick = onManageChannels
                )
                
                SettingsItem(
                    title = "Clear All Data",
                    subtitle = "Remove all channels and reset preferences",
                    onClick = { viewModel.showClearConfirmation() },
                    isDestructive = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingsItem(
                    title = "About",
                    subtitle = "ATV - Android TV IPTV Player v1.0.0",
                    onClick = { viewModel.showAbout() }
                )
            }
        }
        
        // Message snackbar
        uiState.message?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearMessage()
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(AtvColors.Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message,
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurface
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = AtvTypography.headlineMedium,
            color = AtvColors.Primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AtvTypography.bodySmall,
            color = AtvColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val textColor = if (isDestructive) AtvColors.Error else AtvColors.OnSurface
    val subtitleColor = if (isDestructive) AtvColors.Error.copy(alpha = 0.7f) else AtvColors.OnSurfaceVariant
    
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AtvColors.Surface,
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
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = AtvTypography.titleMedium,
                color = textColor
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
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(350.dp)
        ) {
            Column {
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    DialogButton(
                        label = "Cancel",
                        onClick = onDismiss
                    )
                    DialogButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        isDestructive = true
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(400.dp)
        ) {
            Column {
                Text(
                    text = "ATV",
                    style = AtvTypography.headlineLarge,
                    color = AtvColors.Primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Android TV IPTV Player",
                    style = AtvTypography.titleMedium,
                    color = AtvColors.OnSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Version 1.0.0",
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "A simple IPTV player for Android TV devices. " +
                           "Load M3U/M3U8 playlists and watch your favorite channels " +
                           "with a familiar TV remote experience.",
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogButton(
                        label = "Close",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) AtvColors.Error else AtvColors.Primary
    
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color.copy(alpha = 0.1f),
            focusedContainerColor = color.copy(alpha = 0.3f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = color
                ),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AtvTypography.labelMedium,
                color = color
            )
        }
    }
}
