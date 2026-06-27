package com.example.atv.ui.screens.setup

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

@Composable
fun SetupScreen(
    onOpenChannelSource: () -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(onBack = {
                if (uiState.hasExistingPlaylist) {
                    onBack()
                    true
                } else {
                    false
                }
            }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.app_title),
                style = AtvTypography.displayMedium,
                color = AtvColors.Primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.select_channel_source),
                style = AtvTypography.titleLarge,
                color = AtvColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            SetupButton(
                text = stringResource(R.string.channel_source_title),
                onClick = onOpenChannelSource,
                modifier = Modifier.focusRequester(focusRequester),
            )
            if (uiState.hasExistingPlaylist) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.current_playlist_count, uiState.channelCount),
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AtvColors.Primary.copy(alpha = 0.2f),
            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.4f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(width = 2.dp, color = AtvColors.Primary),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = AtvTypography.titleMedium,
                color = AtvColors.OnSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
