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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import kotlinx.coroutines.delay

/**
 * Overlay showing the full channel list.
 * Slides in from the left side of the screen.
 */
@Composable
fun ChannelListOverlay(
    channels: List<Channel>,
    currentChannelIndex: Int,
    visible: Boolean,
    onChannelSelected: (Channel) -> Unit,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back, Key.DirectionLeft -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            val currentChannelFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (currentChannelIndex - 2).coerceAtLeast(0)
            )
            
            LaunchedEffect(visible) {
                if (visible) {
                    // Small delay to ensure list is rendered before requesting focus
                    delay(100)
                    currentChannelFocusRequester.requestFocus()
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(350.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(AtvColors.Surface.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.channels),
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )
                
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(channels) { index, channel ->
                        ChannelListItem(
                            channel = channel,
                            isCurrentlyPlaying = index == currentChannelIndex,
                            onSelected = { onChannelSelected(channel) },
                            onUserInteraction = onUserInteraction,
                            modifier = if (index == currentChannelIndex) {
                                Modifier.focusRequester(currentChannelFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelListItem(
    channel: Channel,
    isCurrentlyPlaying: Boolean,
    onSelected: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelected,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onUserInteraction()
                }
            },
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrentlyPlaying) {
                AtvColors.Primary.copy(alpha = 0.2f)
            } else {
                AtvColors.SurfaceVariant.copy(alpha = 0.5f)
            },
            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.3f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = AtvColors.FocusRing
                ),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.number.toString().padStart(3, ' '),
                style = AtvTypography.titleMedium,
                color = if (isCurrentlyPlaying) AtvColors.Primary else AtvColors.OnSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurface,
                    maxLines = 1
                )
                channel.groupTitle?.let { group ->
                    Text(
                        text = group,
                        style = AtvTypography.labelMedium,
                        color = AtvColors.OnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            if (isCurrentlyPlaying) {
                Text(
                    text = "▶",
                    style = AtvTypography.titleMedium,
                    color = AtvColors.Primary
                )
            }
        }
    }
}
