package com.example.atv.ui.screens.setup

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

/**
 * Setup screen for loading playlist files.
 */
@Composable
fun SetupScreen(
    onPlaylistLoaded: () -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    
    // Navigate to playback when playlist is loaded
    LaunchedEffect(uiState.isPlaylistLoaded) {
        if (uiState.isPlaylistLoaded) {
            onPlaylistLoaded()
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(
                onBack = { 
                    when {
                        showUrlInput -> {
                            showUrlInput = false
                            true
                        }
                        uiState.hasExistingPlaylist -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showUrlInput) {
            // URL Input Dialog
            UrlInputDialog(
                urlText = urlText,
                onUrlChange = { urlText = it },
                onConfirm = {
                    viewModel.loadPlaylistFromUrl(urlText)
                    showUrlInput = false
                },
                onDismiss = { showUrlInput = false }
            )
        } else {
            // Main setup screen
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // App logo/title
                Text(
                    text = "📺",
                    style = AtvTypography.displayLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "ATV",
                    style = AtvTypography.displayMedium,
                    color = AtvColors.Primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Android TV IPTV Player",
                    style = AtvTypography.titleLarge,
                    color = AtvColors.OnSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                if (uiState.isLoading) {
                    Text(
                        text = "Loading playlist...",
                        style = AtvTypography.titleMedium,
                        color = AtvColors.OnSurface
                    )
                } else if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = AtvTypography.bodyLarge,
                        color = AtvColors.Error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // URL input button after error
                    SetupButton(
                        text = "🔗  Enter Playlist URL",
                        onClick = {
                            viewModel.dismissError()
                            showUrlInput = true
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                } else {
                    Text(
                        text = "Select a playlist source",
                        style = AtvTypography.bodyLarge,
                        color = AtvColors.OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // URL input button (primary option for Android TV)
                    SetupButton(
                        text = "🔗  Enter Playlist URL",
                        onClick = { showUrlInput = true },
                        modifier = Modifier.focusRequester(focusRequester),
                        isPrimary = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Demo playlist button
                    SetupButton(
                        text = "🎬  Load Demo Playlist",
                        onClick = { viewModel.loadDemoPlaylist() }
                    )
                    
                    if (uiState.hasExistingPlaylist) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Current playlist: ${uiState.channelCount} channels",
                            style = AtvTypography.bodyMedium,
                            color = AtvColors.OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * URL input dialog for entering playlist URL
 */
@Composable
private fun UrlInputDialog(
    urlText: String,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val textFieldFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .background(
                color = AtvColors.Surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter Playlist URL",
            style = AtvTypography.titleLarge,
            color = AtvColors.OnSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Enter the URL of your M3U8 playlist",
            style = AtvTypography.bodyMedium,
            color = AtvColors.OnSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Text input field
        BasicTextField(
            value = urlText,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    color = AtvColors.SurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .focusRequester(textFieldFocusRequester),
            textStyle = AtvTypography.bodyLarge.copy(color = AtvColors.OnSurface),
            cursorBrush = SolidColor(AtvColors.Primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onConfirm() }
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (urlText.isEmpty()) {
                        Text(
                            text = "https://example.com/playlist.m3u8",
                            style = AtvTypography.bodyLarge,
                            color = AtvColors.OnSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            SetupButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            
            SetupButton(
                text = "Load",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                isPrimary = true
            )
        }
    }
}

/**
 * Reusable button component for setup screen
 */
@Composable
private fun SetupButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(250.dp)
            .height(56.dp),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) {
                AtvColors.Primary.copy(alpha = 0.2f)
            } else {
                AtvColors.SurfaceVariant
            },
            focusedContainerColor = if (isPrimary) {
                AtvColors.Primary.copy(alpha = 0.4f)
            } else {
                AtvColors.Primary.copy(alpha = 0.3f)
            }
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = AtvColors.Primary
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
                text = text,
                style = if (isPrimary) AtvTypography.titleMedium else AtvTypography.bodyLarge,
                color = if (isPrimary) AtvColors.Primary else AtvColors.OnSurface
            )
        }
    }
}
