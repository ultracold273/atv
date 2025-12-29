package com.example.atv.ui.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.example.atv.ui.components.ChannelInfoOverlay
import com.example.atv.ui.components.ChannelListOverlay
import com.example.atv.ui.components.ErrorOverlay
import com.example.atv.ui.components.NumberPadOverlay
import com.example.atv.ui.components.SettingsMenu
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.util.handleDPadKeyEvents

/**
 * Main playback screen with full-screen video and overlays.
 */
@Composable
fun PlaybackScreen(
    onNavigateToSetup: () -> Unit,
    onNavigateToChannelManagement: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    
    // Request focus when screen appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .focusRequester(focusRequester)
            .focusable()
            .handleDPadKeyEvents(
                onUp = { 
                    if (!uiState.hasActiveOverlay) {
                        viewModel.previousChannel()
                    }
                },
                onDown = { 
                    if (!uiState.hasActiveOverlay) {
                        viewModel.nextChannel()
                    }
                },
                onLeft = { 
                    if (!uiState.hasActiveOverlay) {
                        viewModel.showChannelList()
                    }
                },
                onCenter = { 
                    if (!uiState.hasActiveOverlay) {
                        viewModel.showNumberPad()
                    }
                },
                onBack = {
                    viewModel.dismissActiveOverlay()
                },
                onMenu = {
                    if (!uiState.hasActiveOverlay) {
                        viewModel.showSettings()
                    }
                }
            )
    ) {
        // Video player
        viewModel.player?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { playerView ->
                    playerView.player = player
                }
            )
        }
        
        // Loading indicator
        if (uiState.playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.tv.material3.Text(
                    text = "Loading...",
                    style = com.example.atv.ui.theme.AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface
                )
            }
        }
        
        // Channel info overlay (top)
        ChannelInfoOverlay(
            channel = uiState.currentChannel,
            visible = uiState.showChannelInfo
        )
        
        // Channel list overlay (left)
        ChannelListOverlay(
            channels = uiState.channels,
            currentChannelIndex = uiState.currentChannelIndex,
            visible = uiState.showChannelList,
            onChannelSelected = { viewModel.selectChannelFromList(it) },
            onDismiss = { viewModel.hideChannelList() }
        )
        
        // Number pad overlay (center)
        NumberPadOverlay(
            input = uiState.numberPadInput,
            visible = uiState.showNumberPad,
            maxChannels = uiState.channelCount,
            onDigitPressed = { viewModel.appendNumberPadDigit(it) },
            onClear = { viewModel.clearNumberPadInput() },
            onConfirm = { viewModel.confirmNumberPadInput() },
            onDismiss = { viewModel.hideNumberPad() }
        )
        
        // Settings menu (right)
        SettingsMenu(
            visible = uiState.showSettings,
            onLoadPlaylist = {
                viewModel.hideSettings()
                onNavigateToSetup()
            },
            onManageChannels = {
                viewModel.hideSettings()
                onNavigateToChannelManagement()
            },
            onClearPlaylist = {
                // TODO: Implement clear playlist
                viewModel.hideSettings()
            },
            onOpenSettings = {
                viewModel.hideSettings()
                onNavigateToSettings()
            },
            onDismiss = { viewModel.hideSettings() }
        )
        
        // Error overlay (center)
        ErrorOverlay(
            message = uiState.errorMessage ?: "Unknown error",
            visible = uiState.showError,
            onRetry = { viewModel.retry() },
            onNextChannel = { 
                viewModel.dismissError()
                viewModel.nextChannel()
            },
            onDismiss = { viewModel.dismissError() }
        )
    }
}
