package com.example.atv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.Program
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.testing.UiTestTags
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeSlotFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Overlay showing current channel info (top-left) and optional now-playing /
 * up-next program info (bottom-center). Both blocks share the same visibility
 * lifecycle so they appear and auto-hide together (FR-004).
 */
@Composable
fun ChannelInfoOverlay(
    channel: Channel?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    currentProgram: Program? = null,
    nextProgram: Program? = null,
    currentTime: Instant? = null
) {
    AnimatedVisibility(
        visible = visible && channel != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        channel?.let {
            Box(modifier = Modifier.fillMaxSize().testTag(UiTestTags.ChannelInfoOverlay)) {
                // Top-left: existing channel block (unchanged content)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AtvColors.Surface.copy(alpha = 0.9f))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CH ${channel.number}",
                            style = AtvTypography.titleLarge,
                            color = AtvColors.Primary
                        )
                        Text(
                            text = channel.name,
                            style = AtvTypography.headlineMedium,
                            color = AtvColors.OnSurface
                        )
                        channel.groupTitle?.let { group ->
                            Text(
                                text = group,
                                style = AtvTypography.bodyMedium,
                                color = AtvColors.OnSurfaceVariant
                            )
                        }
                    }
                }

                // Bottom-center: program block, only when current program is known (FR-005)
                if (currentProgram != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        ProgramBlock(
                            current = currentProgram,
                            next = nextProgram,
                            now = currentTime
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramBlock(
    current: Program,
    next: Program?,
    now: Instant?
) {
    Column(
        modifier = Modifier
            .widthIn(min = 360.dp, max = 720.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AtvColors.Surface.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.epg_now_playing_label),
            style = AtvTypography.labelMedium,
            color = AtvColors.OnSurfaceVariant
        )
        Text(
            text = current.name,
            style = AtvTypography.titleMedium,
            color = AtvColors.OnSurface,
            maxLines = 1
        )
        Text(
            text = "${timeSlotFormatter.format(current.start)}–${timeSlotFormatter.format(current.end)}",
            style = AtvTypography.bodySmall,
            color = AtvColors.OnSurfaceVariant
        )
        if (now != null) {
            LinearProgressIndicator(
                progress = { current.progress(now) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (next != null) {
            Text(
                text = stringResource(R.string.epg_up_next_label),
                style = AtvTypography.labelMedium,
                color = AtvColors.OnSurfaceVariant
            )
            Text(
                text = "${next.name} · ${timeSlotFormatter.format(next.start)}",
                style = AtvTypography.bodyMedium,
                color = AtvColors.OnSurface,
                maxLines = 1
            )
        }
    }
}
