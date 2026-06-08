package com.example.atv.domain.repository

import com.example.atv.domain.model.Program
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of program-guide data, abstracted from the operator-specific protocol.
 *
 * `isConfigured` is false until provider-specific credentials are available.
 * UI must hide all EPG surfaces when this is false, regardless of the user
 * "Show program guide" toggle. In spec 004 alone, this is permanently false
 * (no login UI ships); spec 005 will populate credentials and flip it true.
 */
interface EpgProvider {
    val isConfigured: StateFlow<Boolean>

    /**
     * Fetch the program list for a channel on a given date offset.
     *
     * @param channelCode opaque per-provider channel identifier
     * @param dateOffset -1 = yesterday, 0 = today, +1 = tomorrow
     */
    suspend fun fetchPrograms(channelCode: String, dateOffset: Int): Result<List<Program>>
}
