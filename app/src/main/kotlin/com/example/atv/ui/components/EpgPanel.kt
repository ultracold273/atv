package com.example.atv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Program
import com.example.atv.domain.model.channelCode
import com.example.atv.ui.screens.playback.EpgPanelState
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Side-by-side EPG panel. Renders the schedule for `state.focusedChannel` on the
 * date selected via the three tabs (Yesterday/Today/Tomorrow).
 *
 * `onLeftFromPanel` is invoked when D-pad LEFT is pressed from any focused element
 * inside the panel — it is the caller's job to return focus to the channel column
 * (FR-011). The panel itself does not own a FocusRequester for the channel column.
 */
@Composable
fun EpgPanel(
    state: EpgPanelState,
    currentTime: Instant,
    onDateOffsetSelected: (Int) -> Unit,
    onLeftFromPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channel = state.focusedChannel
    if (channel == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onLeftFromPanel()
                    true
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = channel.name,
            style = AtvTypography.titleLarge,
            color = AtvColors.OnSurface
        )

        DateTabStrip(
            selected = state.dateOffset,
            onSelected = onDateOffsetSelected
        )

        when {
            // FR-014: if the channel has no EPG mapping at all, there is nothing to load
            // and nothing to wait for — never show "Loading..." or "Unable to load"; just
            // tell the user no guide exists for this channel. This branch must fire BEFORE
            // the loading/error/empty branches so that a stray transient state (e.g. a
            // loading spinner from a previously-focused channel) cannot leak through.
            channel.channelCode == null ->
                CenteredText(stringResource(R.string.epg_unavailable_for_channel))
            state.isLoading -> CenteredText(stringResource(R.string.epg_loading))
            state.errorMessage != null -> CenteredText(state.errorMessage)
            state.isEmpty -> CenteredText(stringResource(R.string.epg_no_programs))
            else -> ProgramList(
                programs = state.programs,
                currentTime = currentTime
            )
        }
    }
}

@Composable
private fun DateTabStrip(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    val tabs = listOf(
        -1 to stringResource(R.string.epg_date_yesterday),
        0 to stringResource(R.string.epg_date_today),
        1 to stringResource(R.string.epg_date_tomorrow)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (offset, label) ->
            val isSelected = offset == selected
            Surface(
                onClick = { onSelected(offset) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) {
                        AtvColors.Primary.copy(alpha = 0.2f)
                    } else {
                        AtvColors.SurfaceVariant.copy(alpha = 0.5f)
                    },
                    focusedContainerColor = AtvColors.Primary.copy(alpha = 0.3f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(width = 2.dp, color = AtvColors.FocusRing),
                        shape = RoundedCornerShape(8.dp)
                    )
                )
            ) {
                Text(
                    text = label,
                    style = AtvTypography.labelLarge,
                    color = if (isSelected) AtvColors.Primary else AtvColors.OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProgramList(
    programs: List<Program>,
    currentTime: Instant
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(programs, key = { it.code }) { program ->
            ProgramRow(program = program, currentTime = currentTime)
        }
    }
}

@Composable
private fun ProgramRow(
    program: Program,
    currentTime: Instant
) {
    val isAiring = program.airsAt(currentTime)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isAiring) {
                    AtvColors.Primary.copy(alpha = 0.2f)
                } else {
                    AtvColors.SurfaceVariant.copy(alpha = 0.3f)
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${rowTimeFormatter.format(program.start)}–${rowTimeFormatter.format(program.end)}",
                style = AtvTypography.labelMedium,
                color = if (isAiring) AtvColors.Primary else AtvColors.OnSurfaceVariant
            )
            Text(
                text = program.name,
                style = AtvTypography.bodyLarge,
                color = AtvColors.OnSurface,
                maxLines = 1
            )
        }
        if (isAiring) {
            LinearProgressIndicator(
                progress = { program.progress(currentTime) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AtvTypography.bodyMedium,
            color = AtvColors.OnSurfaceVariant
        )
    }
}
