package com.example.atv.domain.usecase

import com.example.atv.domain.model.ChannelSourceMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveStreamUrlUseCaseTest {
    private val resolver = ResolveStreamUrlUseCase()

    @Test
    fun `direct ctc rewrites multicast through udpxy`() {
        assertEquals(
            "http://openwrt:4022/udp/239.1.1.1:8000",
            resolver("igmp://239.1.1.1:8000", ChannelSourceMode.DIRECT_CTC, "openwrt:4022"),
        )
    }

    @Test
    fun `m3u8 rewrites rtp through udpxy`() {
        assertEquals(
            "http://openwrt:4022/udp/239.1.1.2:8000",
            resolver("rtp://239.1.1.2:8000", ChannelSourceMode.M3U8, "http://openwrt:4022/"),
        )
    }

    @Test
    fun `home proxy treats stream url as final`() {
        assertEquals(
            "igmp://239.1.1.1:8000",
            resolver("igmp://239.1.1.1:8000", ChannelSourceMode.HOME_PROXY, "openwrt:4022"),
        )
    }

    @Test
    fun `http url passes through unchanged`() {
        assertEquals(
            "http://example.com/live.m3u8",
            resolver("http://example.com/live.m3u8", ChannelSourceMode.DIRECT_CTC, "openwrt:4022"),
        )
    }
}

