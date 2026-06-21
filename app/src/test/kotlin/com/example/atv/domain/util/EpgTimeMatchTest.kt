package com.example.atv.domain.util

import com.example.atv.domain.model.Program
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EpgTimeMatchTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day: LocalDate = LocalDate.of(2026, 6, 21)

    /** Build a program spanning [fromHour:fromMin, toHour:toMin) on `day` in `zone`. */
    private fun prog(code: String, from: LocalTime, to: LocalTime): Program = Program(
        code = code,
        name = code,
        start = day.atTime(from).atZone(zone).toInstant(),
        end = day.atTime(to).atZone(zone).toInstant(),
        isLive = false,
        isReplayable = false
    )

    private val schedule = listOf(
        prog("a", LocalTime.of(0, 0), LocalTime.of(8, 0)),
        prog("b", LocalTime.of(8, 0), LocalTime.of(12, 0)),
        prog("c", LocalTime.of(12, 0), LocalTime.of(20, 0)),
        prog("d", LocalTime.of(20, 0), LocalTime.of(23, 59))
    )

    @Test
    fun `returns index of program covering the target time`() {
        assertEquals(2, EpgTimeMatch.pickIndexForTimeOfDay(schedule, LocalTime.of(15, 30), zone))
    }

    @Test
    fun `matches the boundary start inclusively`() {
        assertEquals(1, EpgTimeMatch.pickIndexForTimeOfDay(schedule, LocalTime.of(8, 0), zone))
    }

    @Test
    fun `matches first program at midnight`() {
        assertEquals(0, EpgTimeMatch.pickIndexForTimeOfDay(schedule, LocalTime.of(0, 0), zone))
    }

    @Test
    fun `matches last program late in the day`() {
        assertEquals(3, EpgTimeMatch.pickIndexForTimeOfDay(schedule, LocalTime.of(23, 30), zone))
    }

    @Test
    fun `falls back to last program starting at or before target when in a gap`() {
        val gapped = listOf(
            prog("x", LocalTime.of(0, 0), LocalTime.of(8, 0)),
            prog("y", LocalTime.of(9, 0), LocalTime.of(12, 0)) // gap 08:00-09:00
        )
        // 08:30 is in the gap → last program starting at/before 08:30 is "x" (index 0).
        assertEquals(0, EpgTimeMatch.pickIndexForTimeOfDay(gapped, LocalTime.of(8, 30), zone))
    }

    @Test
    fun `returns 0 when target is before the first program`() {
        val late = listOf(prog("only", LocalTime.of(10, 0), LocalTime.of(12, 0)))
        assertEquals(0, EpgTimeMatch.pickIndexForTimeOfDay(late, LocalTime.of(5, 0), zone))
    }

    @Test
    fun `returns 0 for empty list`() {
        assertEquals(0, EpgTimeMatch.pickIndexForTimeOfDay(emptyList(), LocalTime.of(12, 0), zone))
    }
}
