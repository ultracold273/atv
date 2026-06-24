package com.example.atv.data.proxy

import com.example.atv.domain.model.ProxySettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProxyChannelClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ProxyChannelClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ProxyChannelClient(
            http = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchChannels parses channel response and sends bearer token`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    {"number": 1, "name": "CCTV-1", "streamUrl": "http://openwrt:4022/udp/239.0.0.1:8000", "channelCode": "ch1"}
                  ],
                  "cache": {"stale": false, "cachedAt": "2026-06-24T20:00:00+08:00", "ttlSeconds": 3600}
                }
                """.trimIndent(),
            ),
        )

        val result = client.fetchChannels(settings())

        assertTrue(result.isSuccess)
        val channels = result.getOrThrow()
        assertEquals(1, channels.single().number)
        assertEquals("CCTV-1", channels.single().name)
        assertEquals("ch1", channels.single().channelCode)
        assertEquals("Bearer local-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `fetchChannels maps unauthorized to failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val result = client.fetchChannels(settings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("authorization"))
    }

    @Test
    fun `fetchChannels maps structured proxy error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503).setBody(
                """{"error":{"code":"backend_unavailable","message":"Backend down"}}""",
            ),
        )

        val result = client.fetchChannels(settings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("backend_unavailable"))
    }

    private fun settings() = ProxySettings(
        proxyBaseUrl = server.url("/").toString().trimEnd('/'),
        accessToken = "local-token",
    )
}

