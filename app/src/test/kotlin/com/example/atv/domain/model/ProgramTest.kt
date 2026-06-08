package com.example.atv.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ProgramTest {

    private fun sample(
        start: String = "2026-06-07T08:00:00Z",
        end: String = "2026-06-07T09:00:00Z"
    ) = Program(
        code = "p1",
        name = "News",
        start = Instant.parse(start),
        end = Instant.parse(end),
        isLive = true,
        isReplayable = false
    )

    @Test
    fun `airsAt returns true when given instant is within start-end`() {
        assertTrue(sample().airsAt(Instant.parse("2026-06-07T08:30:00Z")))
    }

    @Test
    fun `airsAt is inclusive of start and exclusive of end`() {
        val p = sample()
        assertTrue(p.airsAt(Instant.parse("2026-06-07T08:00:00Z")))
        assertFalse(p.airsAt(Instant.parse("2026-06-07T09:00:00Z")))
    }

    @Test
    fun `airsAt returns false outside window`() {
        val p = sample()
        assertFalse(p.airsAt(Instant.parse("2026-06-07T07:59:59Z")))
        assertFalse(p.airsAt(Instant.parse("2026-06-07T09:30:00Z")))
    }

    @Test
    fun `progress is 0 at start, half at midpoint, 1 at end`() {
        val p = sample(end = "2026-06-07T10:00:00Z")
        assertEquals(0.0f, p.progress(Instant.parse("2026-06-07T08:00:00Z")))
        assertEquals(0.5f, p.progress(Instant.parse("2026-06-07T09:00:00Z")))
        assertEquals(1.0f, p.progress(Instant.parse("2026-06-07T10:00:00Z")))
    }

    @Test
    fun `progress is clamped to 0 and 1`() {
        val p = sample(end = "2026-06-07T10:00:00Z")
        assertEquals(0.0f, p.progress(Instant.parse("2026-06-07T07:00:00Z")))
        assertEquals(1.0f, p.progress(Instant.parse("2026-06-07T11:00:00Z")))
    }
}
