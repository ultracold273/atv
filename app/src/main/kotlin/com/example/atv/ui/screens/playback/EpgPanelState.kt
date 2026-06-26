package com.example.atv.ui.screens.playback

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.Program

/**
 * State for the side-by-side EPG panel inside the channel list overlay.
 *
 * @param focusedChannel the channel currently focused in the channel column,
 *   null when the overlay is closed or no channel has focus yet.
 * @param dateOffset selected CTC date index: -1 = tomorrow, 0 = today, +1 = yesterday.
 *   Always reset to 0 when the overlay reopens (FR-009).
 * @param programs schedule for (focusedChannel, dateOffset). Empty until a fetch resolves.
 * @param isLoading true while a fetch is in flight (after the 250ms debounce).
 * @param errorMessage non-null when fetching failed after retry (FR-015).
 */
data class EpgPanelState(
    val focusedChannel: Channel? = null,
    val dateOffset: Int = 0,
    val programs: List<Program> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    /** True when the panel resolved to "nothing to show" — drives empty-state rendering. */
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && programs.isEmpty()

    /**
     * True when the scrollable program list is actually rendered (channel has an EPG
     * mapping, not loading, no error, non-empty). Only then is the program-list focus
     * target attached — guards focus requests that would otherwise crash.
     */
    val programListVisible: Boolean
        get() = focusedChannel?.channelCode != null && !isLoading && errorMessage == null && !isEmpty
}
