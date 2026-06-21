package com.example.atv.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UdpxyUrlRewriterTest {

    @Test
    fun `rewrites igmp url through proxy`() {
        assertEquals(
            "http://openwrt:4022/udp/239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", "openwrt:4022"),
        )
    }

    @Test
    fun `rewrites rtp url through proxy`() {
        assertEquals(
            "http://openwrt:4022/udp/239.49.0.2:8008",
            UdpxyUrlRewriter.rewrite("rtp://239.49.0.2:8008", "openwrt:4022"),
        )
    }

    @Test
    fun `returns input unchanged when proxy is blank`() {
        assertEquals(
            "igmp://239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", ""),
        )
        assertEquals(
            "igmp://239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", "   "),
        )
    }

    @Test
    fun `returns input unchanged when proxy is null`() {
        assertEquals(
            "igmp://239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", null),
        )
    }

    @Test
    fun `leaves http url unchanged`() {
        assertEquals(
            "http://example.com/stream.m3u8",
            UdpxyUrlRewriter.rewrite("http://example.com/stream.m3u8", "openwrt:4022"),
        )
    }

    @Test
    fun `leaves rtsp and file urls unchanged`() {
        assertEquals(
            "rtsp://1.2.3.4:554/abc",
            UdpxyUrlRewriter.rewrite("rtsp://1.2.3.4:554/abc", "openwrt:4022"),
        )
        assertEquals(
            "file:///sdcard/clip.ts",
            UdpxyUrlRewriter.rewrite("file:///sdcard/clip.ts", "openwrt:4022"),
        )
    }

    @Test
    fun `accepts proxy with http scheme prefix`() {
        assertEquals(
            "http://openwrt:4022/udp/239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", "http://openwrt:4022"),
        )
    }

    @Test
    fun `accepts proxy with trailing slash`() {
        assertEquals(
            "http://openwrt:4022/udp/239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", "openwrt:4022/"),
        )
    }

    @Test
    fun `accepts proxy with http scheme and trailing slash`() {
        assertEquals(
            "http://openwrt:4022/udp/239.49.0.1:8000",
            UdpxyUrlRewriter.rewrite("igmp://239.49.0.1:8000", "http://openwrt:4022/"),
        )
    }
}
