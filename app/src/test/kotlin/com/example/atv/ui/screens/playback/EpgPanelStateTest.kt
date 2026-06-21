package com.example.atv.ui.screens.playback

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.Program
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class EpgPanelStateTest {

    private val ctcChannel = Channel(
        number = 1,
        name = "CCTV-1",
        streamUrl = "igmp://239.0.0.1:8000",
        channelCode = "ch00000000000000000001"
    )
    private val m3uChannel = Channel(
        number = 2,
        name = "Local",
        streamUrl = "http://example.com/s.m3u8",
        channelCode = null
    )
    private val program = Program(
        code = "p1",
        name = "News",
        start = Instant.parse("2026-06-21T08:00:00Z"),
        end = Instant.parse("2026-06-21T09:00:00Z"),
        isLive = true,
        isReplayable = false
    )

    @Test
    fun `programListVisible true only when programs are shown`() {
        val state = EpgPanelState(
            focusedChannel = ctcChannel,
            programs = listOf(program),
            isLoading = false,
            errorMessage = null
        )
        assertTrue(state.programListVisible)
    }

    @Test
    fun `programListVisible false while loading`() {
        val state = EpgPanelState(focusedChannel = ctcChannel, isLoading = true)
        assertFalse(state.programListVisible)
    }

    @Test
    fun `programListVisible false on error`() {
        val state = EpgPanelState(focusedChannel = ctcChannel, errorMessage = "boom")
        assertFalse(state.programListVisible)
    }

    @Test
    fun `programListVisible false when empty`() {
        val state = EpgPanelState(focusedChannel = ctcChannel, programs = emptyList())
        assertFalse(state.programListVisible)
    }

    @Test
    fun `programListVisible false when channel has no EPG code`() {
        val state = EpgPanelState(focusedChannel = m3uChannel, programs = listOf(program))
        assertFalse(state.programListVisible)
    }

    @Test
    fun `programListVisible false when no channel focused`() {
        assertFalse(EpgPanelState(focusedChannel = null).programListVisible)
    }
}
