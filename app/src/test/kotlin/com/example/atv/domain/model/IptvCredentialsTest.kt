package com.example.atv.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IptvCredentialsTest {

    private fun valid() = IptvCredentials(
        userId = "1234567890123",
        password = "000000",
        stbId = "0".repeat(32),
        ip = "192.0.2.1",
        mac = "00:00:5E:00:53:01",
        authServerUrl = "http://example.com:8298",
    )

    @Test
    fun `isComplete true when all fields valid`() {
        assertTrue(valid().isComplete)
    }

    @Test
    fun `isComplete false when userId blank`() {
        assertFalse(valid().copy(userId = "").isComplete)
        assertFalse(valid().copy(userId = "  ").isComplete)
    }

    @Test
    fun `isComplete false when password blank`() {
        assertFalse(valid().copy(password = "").isComplete)
    }

    @Test
    fun `isComplete false when stbId is not 32 chars`() {
        assertFalse(valid().copy(stbId = "0".repeat(31)).isComplete)
        assertFalse(valid().copy(stbId = "0".repeat(33)).isComplete)
        assertFalse(valid().copy(stbId = "").isComplete)
    }

    @Test
    fun `isComplete false when ip blank`() {
        assertFalse(valid().copy(ip = "").isComplete)
    }

    @Test
    fun `isComplete false when mac blank`() {
        assertFalse(valid().copy(mac = "").isComplete)
    }

    @Test
    fun `isComplete false when authServerUrl is not a valid http url`() {
        assertFalse(valid().copy(authServerUrl = "").isComplete)
        assertFalse(valid().copy(authServerUrl = "not a url").isComplete)
        assertFalse(valid().copy(authServerUrl = "ftp://example.com").isComplete)
    }

    @Test
    fun `isComplete true for both http and https urls`() {
        assertTrue(valid().copy(authServerUrl = "http://x.com").isComplete)
        assertTrue(valid().copy(authServerUrl = "https://x.com").isComplete)
    }
}
