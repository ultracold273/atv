package com.example.atv.domain.util

import com.example.atv.domain.model.Program
import java.time.LocalTime
import java.time.ZoneId

/**
 * Picks which program row to focus by **wall-clock time-of-day**, independent of the
 * calendar date. Used by the EPG panel so that:
 *   - DOWN from a tab lands on the program airing at the current time (today), or the
 *     same time-of-day slot on yesterday/tomorrow.
 *   - LEFT/RIGHT between days from within the list keeps the focused program's start
 *     time-of-day slot.
 */
object EpgTimeMatch {

    /**
     * Index of the program whose `[start, end)` (mapped to local time in [zone])
     * contains [target]. If [target] falls in a gap, returns the last program that
     * starts at or before [target]. Returns 0 when nothing precedes [target] or the
     * list is empty.
     */
    fun pickIndexForTimeOfDay(
        programs: List<Program>,
        target: LocalTime,
        zone: ZoneId
    ): Int {
        if (programs.isEmpty()) return 0

        var lastStartingBefore = -1
        programs.forEachIndexed { index, program ->
            val start = program.start.atZone(zone).toLocalTime()
            val end = program.end.atZone(zone).toLocalTime()
            if (!target.isBefore(start) && target.isBefore(end)) {
                return index
            }
            if (!start.isAfter(target)) {
                lastStartingBefore = index
            }
        }
        return lastStartingBefore.coerceAtLeast(0)
    }
}
