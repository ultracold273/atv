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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.testing.UiTestTags
import kotlinx.coroutines.delay

private const val FOCUS_REQUEST_DELAY_MS = 100L
private const val CHANNEL_NUMBER_WIDTH = 3

/**
 * Channel list overlay. When `epgEnabled && epgPanelContent != null`, the overlay
 * renders side-by-side: a 350.dp channel column on the left and the EPG panel on
 * the right. When EPG is disabled (the default), the layout is identical to the
 * pre-spec behavior.
 */
@Composable
fun ChannelListOverlay(
    channels: List<Channel>,
    currentChannelIndex: Int,
    visible: Boolean,
    onChannelSelected: (Channel) -> Unit,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier,
    epgEnabled: Boolean = false,
    onChannelFocused: (Channel) -> Unit = {},
    onChannelFocusRequesterChanged: (FocusRequester) -> Unit = {},
    todayTabRequester: FocusRequester? = null,
    epgPanelContent: (@Composable () -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        // Disambiguates D-pad LEFT: when focus is in the EPG panel, EpgPanel handles LEFT
        // itself (returning focus to the channel column). When focus is in the channel
        // column, LEFT dismisses the overlay (preserving today's behavior — FR-012).
        var focusInEpgPanel by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(UiTestTags.ChannelListOverlay)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back -> {
                                onDismiss()
                                true
                            }
                            Key.DirectionLeft -> {
                                if (focusInEpgPanel) {
                                    // EpgPanel.onLeftFromPanel handles focus return.
                                    false
                                } else {
                                    onDismiss()
                                    true
                                }
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            val showEpg = epgEnabled && epgPanelContent != null
            if (showEpg) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ChannelColumn(
                        channels = channels,
                        currentChannelIndex = currentChannelIndex,
                        visible = visible,
                        onChannelSelected = onChannelSelected,
                        onUserInteraction = onUserInteraction,
                        onChannelFocused = onChannelFocused,
                        onChannelFocusRequesterChanged = onChannelFocusRequesterChanged,
                        todayTabRequester = todayTabRequester,
                        modifier = Modifier.width(350.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AtvColors.Surface.copy(alpha = 0.95f))
                            .onFocusChanged { focusInEpgPanel = it.hasFocus }
                    ) {
                        epgPanelContent()
                    }
                }
            } else {
                ChannelColumn(
                    channels = channels,
                    currentChannelIndex = currentChannelIndex,
                    visible = visible,
                    onChannelSelected = onChannelSelected,
                    onUserInteraction = onUserInteraction,
                    onChannelFocused = onChannelFocused,
                    onChannelFocusRequesterChanged = onChannelFocusRequesterChanged,
                    modifier = Modifier.width(350.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelColumn(
    channels: List<Channel>,
    currentChannelIndex: Int,
    visible: Boolean,
    onChannelSelected: (Channel) -> Unit,
    onUserInteraction: () -> Unit,
    onChannelFocused: (Channel) -> Unit,
    onChannelFocusRequesterChanged: (FocusRequester) -> Unit,
    todayTabRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val currentChannelFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentChannelIndex - 2).coerceAtLeast(0)
    )

    LaunchedEffect(visible) {
        if (visible) {
            delay(FOCUS_REQUEST_DELAY_MS)
            currentChannelFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
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
                val itemRequester = remember(channel.number) { FocusRequester() }
                ChannelListItem(
                    channel = channel,
                    isCurrentlyPlaying = index == currentChannelIndex,
                    onSelected = { onChannelSelected(channel) },
                    onUserInteraction = onUserInteraction,
                    onFocused = {
                        onChannelFocused(channel)
                        onChannelFocusRequesterChanged(itemRequester)
                    },
                    modifier = Modifier
                        .testTag("${UiTestTags.ChannelListItemPrefix}-${channel.number}")
                        .focusRequester(itemRequester)
                        .then(
                            if (index == currentChannelIndex) {
                                Modifier.focusRequester(currentChannelFocusRequester)
                            } else Modifier
                        )
                        .then(
                            // RIGHT moves into the EPG panel, landing on the "today" tab
                            // (deterministic, not spatial). Only when the panel is shown.
                            if (todayTabRequester != null) {
                                Modifier.onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown &&
                                        event.key == Key.DirectionRight
                                    ) {
                                        // runCatching: never crash on a focus race if the
                                        // tab node isn't attached yet.
                                        runCatching { todayTabRequester.requestFocus() }
                                        true
                                    } else false
                                }
                            } else Modifier
                        )
                )
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
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelected,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onUserInteraction()
                    onFocused()
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.number.toString().padStart(CHANNEL_NUMBER_WIDTH, ' '),
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
