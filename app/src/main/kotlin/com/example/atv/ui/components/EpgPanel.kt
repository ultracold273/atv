package com.example.atv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Program
import com.example.atv.domain.util.EpgTimeMatch
import com.example.atv.ui.screens.playback.EpgPanelState
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val displayZone: ZoneId = ZoneId.systemDefault()

private val rowTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(displayZone)

private const val DATE_TAB_COUNT = 3
private const val TODAY_TAB_INDEX = 1 // tabs map to offsets [-1, 0, +1]
private const val MIN_DATE_OFFSET = -1
private const val MAX_DATE_OFFSET = 1

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
    onTodayTabRequesterChanged: (FocusRequester) -> Unit = {},
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val channel = state.focusedChannel
    if (channel == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    // Focus targets owned by the panel: one requester per date tab and one for the
    // program list's entry row. Cross-component hops (to the channel column) go through
    // [onLeftFromPanel]; the today-tab requester is published up so the channel column's
    // RIGHT can land on it.
    val tabRequesters = remember { List(DATE_TAB_COUNT) { FocusRequester() } }
    val programListEntryRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onTodayTabRequesterChanged(tabRequesters[TODAY_TAB_INDEX])
    }

    // The program list (and its entryRequester node) is only composed when there is
    // actual schedule data to show. DOWN-from-tab must not request focus on the entry
    // requester otherwise — an unattached FocusRequester throws "not initialized".
    val programsVisible = state.programListVisible

    // Index of the row to focus / centre on. By default the program at the current
    // time-of-day; after a day switch it's the row matching [pendingFocusTimeOfDay]
    // (the now-time, or the previously-focused program's start time).
    var pendingFocusTimeOfDay by remember { mutableStateOf<LocalTime?>(null) }
    val nowLocalTime = remember(currentTime) { currentTime.atZone(displayZone).toLocalTime() }
    val focusIndex = remember(state.programs, pendingFocusTimeOfDay, nowLocalTime) {
        val target = pendingFocusTimeOfDay ?: nowLocalTime
        EpgTimeMatch.pickIndexForTimeOfDay(state.programs, target, displayZone)
    }

    // Switch to an adjacent day and remember which time-of-day slot to focus once the
    // new day's programs arrive (the reload is async). No-op outside [-1, +1].
    val switchDay: (Int, LocalTime) -> Unit = { newOffset, focusTimeOfDay ->
        if (newOffset in MIN_DATE_OFFSET..MAX_DATE_OFFSET) {
            onUserInteraction()
            pendingFocusTimeOfDay = focusTimeOfDay
            onDateOffsetSelected(newOffset)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Scroll the focus row into view, THEN request focus. The scroll is essential: a
    // LazyColumn cannot focus a row that is scrolled out of the laid-out viewport, so a
    // bare requestFocus() silently no-ops when the entry row is off-screen (e.g. DOWN
    // from a tab after the user had scrolled the list). Only safe when the list is shown.
    fun focusEntryRow() {
        if (!programsVisible) return
        coroutineScope.launch {
            runCatching {
                listState.scrollToItem(focusIndex)
                programListEntryRequester.requestFocus()
            }
        }
    }

    // After a day switch, once the new programs are in and the list is shown, scroll to
    // and focus the matching row, then clear the pending request. Gated on the pending
    // flag so ordinary channel re-renders never steal focus into the list.
    LaunchedEffect(state.programs, state.dateOffset, programsVisible) {
        if (pendingFocusTimeOfDay != null && programsVisible) {
            focusEntryRow()
            pendingFocusTimeOfDay = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = channel.name,
            style = AtvTypography.titleLarge,
            color = AtvColors.OnSurface
        )

        DateTabStrip(
            selected = state.dateOffset,
            currentTime = currentTime,
            tabRequesters = tabRequesters,
            onSelected = onDateOffsetSelected,
            onLeftFromYesterday = onLeftFromPanel,
            onDownToList = {
                // Scroll the entry row into view and focus it. Without the scroll, a bare
                // requestFocus() no-ops when that row is off-screen, so DOWN appears dead.
                pendingFocusTimeOfDay = null
                focusEntryRow()
            },
            onUserInteraction = onUserInteraction
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
                currentTime = currentTime,
                dateOffset = state.dateOffset,
                listState = listState,
                focusIndex = focusIndex,
                entryRequester = programListEntryRequester,
                onLeftToChannel = onLeftFromPanel,
                onUpToTab = { tabRequesters[state.dateOffset - MIN_DATE_OFFSET].requestFocus() },
                onSwitchDay = switchDay,
                onUserInteraction = onUserInteraction
            )
        }
    }
}

@Composable
private fun DateTabStrip(
    selected: Int,
    currentTime: Instant,
    tabRequesters: List<FocusRequester>,
    onSelected: (Int) -> Unit,
    onLeftFromYesterday: () -> Unit,
    onDownToList: () -> Unit,
    onUserInteraction: () -> Unit
) {
    val labels = listOf(
        stringResource(R.string.epg_date_yesterday),
        stringResource(R.string.epg_date_today),
        stringResource(R.string.epg_date_tomorrow)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val offset = index - 1
            DateTab(
                label = label,
                isSelected = offset == selected,
                modifier = Modifier
                    .focusRequester(tabRequesters[index])
                    .onFocusChanged {
                        if (it.isFocused) {
                            onUserInteraction()
                            onSelected(offset)
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                // Move focus to the previous tab — its onFocusChanged
                                // switches the displayed day. Focus stays on the tab strip.
                                if (index == 0) onLeftFromYesterday()
                                else tabRequesters[index - 1].requestFocus()
                                true
                            }
                            Key.DirectionRight -> {
                                // Move focus to the next tab (switches the day via its
                                // onFocusChanged). No-op on the last (tomorrow) tab; consume
                                // so focus doesn't escape to a program row spatially.
                                if (index < labels.lastIndex) tabRequesters[index + 1].requestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                onDownToList()
                                true
                            }
                            else -> false
                        }
                    },
                onClick = { onSelected(offset) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Display-only current time at the right of the tab row (never focusable).
        Text(
            text = rowTimeFormatter.format(currentTime),
            style = AtvTypography.labelLarge,
            color = AtvColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun DateTab(
    label: String,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
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

@Composable
private fun ProgramList(
    programs: List<Program>,
    currentTime: Instant,
    dateOffset: Int,
    listState: LazyListState,
    focusIndex: Int,
    entryRequester: FocusRequester,
    onLeftToChannel: () -> Unit,
    onUpToTab: () -> Unit,
    onSwitchDay: (Int, LocalTime) -> Unit,
    onUserInteraction: () -> Unit
) {
    // Centre the list on the focus row when it first appears for this data set.
    LaunchedEffect(programs) {
        runCatching { listState.scrollToItem((focusIndex - 2).coerceAtLeast(0)) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(programs, key = { _, program -> program.code }) { index, program ->
            // [entryRequester] is attached to the focus row (matching the current time,
            // or the pending day-switch target). Rows do NOT grab focus on data load —
            // focus stays on the channel column while the user navigates channels.
            val startLocalTime = program.start.atZone(displayZone).toLocalTime()
            val rowModifier = Modifier
                .then(if (index == focusIndex) Modifier.focusRequester(entryRequester) else Modifier)
                .onFocusChanged { if (it.isFocused) onUserInteraction() }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            // Yesterday's list LEFT returns to the channel column; other
                            // days move to the previous day, keeping this program's slot.
                            if (dateOffset == MIN_DATE_OFFSET) onLeftToChannel()
                            else onSwitchDay(dateOffset - 1, startLocalTime)
                            true
                        }
                        Key.DirectionRight -> {
                            // Tomorrow's list RIGHT is a no-op; other days move forward.
                            if (dateOffset < MAX_DATE_OFFSET) onSwitchDay(dateOffset + 1, startLocalTime)
                            true
                        }
                        Key.DirectionUp -> {
                            if (index == 0) {
                                onUpToTab()
                                true
                            } else {
                                false // let the list move focus to the previous row
                            }
                        }
                        else -> false
                    }
                }
            ProgramRow(
                program = program,
                currentTime = currentTime,
                modifier = rowModifier
            )
        }
    }
}

@Composable
private fun ProgramRow(
    program: Program,
    currentTime: Instant,
    modifier: Modifier = Modifier
) {
    val isAiring = program.airsAt(currentTime)
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        // Don't scale on focus: the panel column is narrow and a scaled-up row
        // overflows / clips. The focus ring + container colour already signal focus.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isAiring) {
                AtvColors.Primary.copy(alpha = 0.2f)
            } else {
                AtvColors.SurfaceVariant.copy(alpha = 0.3f)
            },
            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.4f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = AtvColors.FocusRing),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${rowTimeFormatter.format(program.start)}–${rowTimeFormatter.format(program.end)}",
                    style = AtvTypography.labelMedium,
                    color = if (isAiring) AtvColors.Primary else AtvColors.OnSurfaceVariant
                )
                Text(
                    text = program.name,
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurface,
                    maxLines = 1
                )
            }
            if (isAiring) {
                LinearProgressIndicator(
                    progress = { program.progress(currentTime) },
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
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
