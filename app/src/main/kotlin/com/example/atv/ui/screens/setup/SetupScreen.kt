package com.example.atv.ui.screens.setup

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.loadPlaylist(uri)
            }
        }
    }
    
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
                    if (uiState.hasExistingPlaylist) {
                        onBack()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
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
                
                // Browse button after error
                BrowseFilesButton(
                    onClick = {
                        viewModel.dismissError()
                        launchFilePicker(filePickerLauncher)
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )
            } else {
                Text(
                    text = "Select a playlist file to get started",
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                BrowseFilesButton(
                    onClick = { launchFilePicker(filePickerLauncher) },
                    modifier = Modifier.focusRequester(focusRequester)
                )
                
                // Demo playlist button - always available, loads from bundled assets
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    onClick = { viewModel.loadDemoPlaylist() },
                    modifier = Modifier
                        .width(250.dp)
                        .height(48.dp),
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(8.dp)
                    ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AtvColors.SurfaceVariant,
                        focusedContainerColor = AtvColors.Primary.copy(alpha = 0.3f)
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
                            text = "🎬  Load Demo Playlist",
                            style = AtvTypography.bodyLarge,
                            color = AtvColors.OnSurface
                        )
                    }
                }
                
                if (uiState.hasExistingPlaylist) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Current playlist: ${uiState.channelCount} channels",
                        style = AtvTypography.bodyMedium,
                        color = AtvColors.OnSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .width(200.dp)
                            .height(48.dp),
                        shape = ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(8.dp)
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AtvColors.SurfaceVariant,
                            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.2f)
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
                                text = "Continue",
                                style = AtvTypography.titleMedium,
                                color = AtvColors.OnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseFilesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            containerColor = AtvColors.Primary.copy(alpha = 0.2f),
            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.4f)
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
                text = "📂  Browse Files",
                style = AtvTypography.titleMedium,
                color = AtvColors.Primary
            )
        }
    }
}

private fun launchFilePicker(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "audio/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "text/plain",
            "*/*"
        ))
    }
    launcher.launch(intent)
}
