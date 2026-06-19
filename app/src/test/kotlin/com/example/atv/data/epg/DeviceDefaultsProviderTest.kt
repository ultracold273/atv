package com.example.atv.data.epg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class DeviceDefaultsProviderTest {

    private fun newProvider(
        seed: Long = 42L,
        lanIp: String? = "10.0.0.5",
    ) = DefaultDeviceDefaultsProvider(
        random = Random(seed),
        lanIpSource = { lanIp },
    )

    @Test
    fun `userId and password are empty by default`() {
        val c = newProvider().generate()
        assertEquals("", c.userId)
        assertEquals("", c.password)
    }

    @Test
    fun `stbId is 32 hex chars`() {
        val c = newProvider().generate()
        assertEquals(32, c.stbId.length)
        assertTrue(c.stbId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `mac is in RFC 7042 documentation range`() {
        val c = newProvider().generate()
        assertTrue(
            c.mac.startsWith("00:00:5E:00:53:"),
            "mac=${c.mac}",
        )
        // Last byte is 2 hex chars.
        assertEquals(17, c.mac.length)
    }

    @Test
    fun `ip uses lan source when available`() {
        val c = newProvider(lanIp = "10.20.30.40").generate()
        assertEquals("10.20.30.40", c.ip)
    }

    @Test
    fun `ip falls back to RFC 5737 documentation range when lan source returns null`() {
        val c = newProvider(lanIp = null).generate()
        assertEquals("192.0.2.1", c.ip)
    }

    @Test
    fun `authServerUrl uses the operator default`() {
        val c = newProvider().generate()
        assertEquals("http://itv.jsinfo.net:8298", c.authServerUrl)
    }
}
