package com.example.atv.data.proxy

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

class ProxyPairingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ProxyPairingClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ProxyPairingClient(
            http = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createSession posts device metadata and parses pairing code`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "sessionId": "ps_1",
                  "pairingCode": "482913",
                  "expiresAt": 1782543300,
                  "pollIntervalSeconds": 2
                }
                """.trimIndent(),
            ),
        )

        val result = client.createSession(
            proxyBaseUrl = server.url("/").toString().trimEnd('/'),
            requestDto = ProxyPairingCreateRequestDto(
                deviceName = "Living Room ATV",
                appId = "com.example.atv",
                appVersion = "1.0",
                clientNonce = "nonce",
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals("482913", result.getOrThrow().pairingCode)
        val request = server.takeRequest()
        assertEquals("/api/v1/pairing/sessions", request.path)
        assertTrue(request.body.readUtf8().contains("Living Room ATV"))
    }

    @Test
    fun `pollSession sends client nonce and parses approved token`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status": "approved",
                  "accessToken": "local-token",
                  "tokenType": "Bearer"
                }
                """.trimIndent(),
            ),
        )

        val result = client.pollSession(
            proxyBaseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "ps_1",
            clientNonce = "nonce",
        )

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("approved", response.status)
        assertEquals("local-token", response.accessToken)
        val request = server.takeRequest()
        assertEquals("/api/v1/pairing/sessions/ps_1", request.path)
        assertEquals("nonce", request.getHeader("X-Client-Nonce"))
    }

    @Test
    fun `createSession maps structured proxy error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setBody(
                """{"error":{"code":"pairing_rate_limited","message":"Slow down"}}""",
            ),
        )

        val result = client.createSession(
            proxyBaseUrl = server.url("/").toString().trimEnd('/'),
            requestDto = ProxyPairingCreateRequestDto(
                deviceName = "TV",
                appId = "com.example.atv",
                appVersion = "1.0",
                clientNonce = "nonce",
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("pairing_rate_limited"))
    }
}
