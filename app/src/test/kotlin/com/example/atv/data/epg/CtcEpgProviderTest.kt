package com.example.atv.data.epg

import com.example.atv.EpgFixtures
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CtcEpgProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var authClient: CtcAuthClient
    private lateinit var credentialsStore: IptvCredentialsStore

    private val creds = IptvCredentials(
        userId = EpgFixtures.USER_ID,
        password = EpgFixtures.PASSWORD,
        stbId = EpgFixtures.STB_ID,
        ip = EpgFixtures.IP,
        mac = EpgFixtures.MAC,
        authServerUrl = "http://example.com:8298",
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder().build()
        authClient = mockk()
        credentialsStore = mockk {
            coEvery { read() } returns creds
        }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun successLogin(): LoginResult.Success = LoginResult.Success(
        epgLbBase = server.url("/iptvepg/function/").toString(),
        jsessionId = "JS-1",
        config = mapOf("UserToken" to "tok"),
        userToken = "tok",
    )

    private val sampleProgramsJson: String = """
        {"channelPrevue":[
          {"prevuecode":"p1","prevuename":"News","begintime":"20260607080000",
           "endtime":"20260607090000","isLive":"1","isBack":"0","isRecord":"0"}
        ]}
    """.trimIndent()

    @Test
    fun `fetchPrograms returns failure when not configured`() = runTest {
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())

        val result = provider.fetchPrograms("ch1", 0)
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
        coVerify(exactly = 0) { authClient.login(any()) }
    }

    @Test
    fun `fetchPrograms returns programs on cache miss after configure`() = runTest {
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val result = provider.fetchPrograms("ch1", 0)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size)
        assertEquals("p1", result.getOrNull()!![0].code)
    }

    @Test
    fun `fetchPrograms passes CTC dateindex query params`() = runTest {
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        provider.fetchPrograms("ch42", -1)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/frame1194/CHANNEL_PLAYER_UTILS/datas/prevue_list.jsp"))
        assertTrue(req.path!!.contains("channelcode=ch42"))
        assertTrue(req.path!!.contains("dateindex=-1"))
        assertTrue(req.path!!.contains("framecode=frame1194"))
        assertTrue(req.path!!.contains("ajax=1"))
        assertTrue(req.getHeader("Cookie").orEmpty().contains("JSESSIONID=JS-1"))
    }

    @Test
    fun `fetchPrograms cache hit within TTL serves without network`() = runTest {
        val fixed = Clock.fixed(Instant.parse("2026-06-07T08:00:00Z"), ZoneOffset.UTC)
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, fixed)
        provider.markConfigured(true)

        provider.fetchPrograms("ch1", 0) // miss
        val req1 = server.requestCount
        provider.fetchPrograms("ch1", 0) // hit
        assertEquals(req1, server.requestCount)
    }

    @Test
    fun `fetchPrograms cache miss after TTL re-fetches`() = runTest {
        val t0 = Instant.parse("2026-06-07T08:00:00Z")
        var now = t0
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): Instant = now
        }
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, clock)
        provider.markConfigured(true)

        provider.fetchPrograms("ch1", 0)
        now = t0.plusSeconds(61)
        provider.fetchPrograms("ch1", 0)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchPrograms retries once on IOException`() = runTest(StandardTestDispatcher()) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val deferred = async { provider.fetchPrograms("ch1", 0) }
        advanceTimeBy(2_000)
        val result = deferred.await()
        assertTrue(result.isSuccess, "got $result")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchPrograms returns failure after second IOException`() = runTest(StandardTestDispatcher()) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val deferred = async { provider.fetchPrograms("ch1", 0) }
        advanceTimeBy(2_000)
        val result = deferred.await()
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchPrograms retries on HTTP 5xx then succeeds`() = runTest(StandardTestDispatcher()) {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val deferred = async { provider.fetchPrograms("ch1", 0) }
        advanceTimeBy(2_000)
        assertTrue(deferred.await().isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchPrograms returns failure on malformed JSON after retry`() = runTest(StandardTestDispatcher()) {
        // Strict JSON parsing (since the kotlinx.serialization pivot): malformed JSON is
        // a hard failure, not silently mapped to empty success. Retry only fires on
        // IOException / 5xx — a 200 with garbage body fails on the first try.
        server.enqueue(MockResponse().setBody("garbage"))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val deferred = async { provider.fetchPrograms("ch1", 0) }
        advanceTimeBy(2_000)
        val result = deferred.await()
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchPrograms triggers re-login when response is HTML session expired`() = runTest(StandardTestDispatcher()) {
        // The CTC server returns an HTML login page (instead of JSON) when the JSESSIONID
        // has expired. We detect this via Content-Type and treat it as a session-expired
        // signal: invalidate the cached session, re-login, retry once. This is the
        // diagnosis-not-silent-fallback path that replaced the python reference's
        // _loads_lenient hack.
        server.enqueue(
            MockResponse()
                .setBody("<html><body>Login expired</body></html>")
                .addHeader("Content-Type", "text/html; charset=utf-8")
        )
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin() andThen successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val deferred = async { provider.fetchPrograms("ch1", 0) }
        advanceTimeBy(2_000)
        val result = deferred.await()
        assertTrue(result.isSuccess, "got $result")
        assertEquals(2, server.requestCount)
        coVerify(exactly = 2) { authClient.login(any()) }
    }

    @Test
    fun `concurrent fetches for same key issue one network call - single-flight`() = runTest {
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC())
        provider.markConfigured(true)

        val results = listOf(
            async { provider.fetchPrograms("ch1", 0) },
            async { provider.fetchPrograms("ch1", 0) },
            async { provider.fetchPrograms("ch1", 0) },
        ).awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `bounded LRU evicts oldest when capacity exceeded`() = runTest {
        coEvery { authClient.login(any()) } returns successLogin()
        val provider = CtcEpgProvider(authClient, http, credentialsStore, Clock.systemUTC(), maxCacheEntries = 2)
        provider.markConfigured(true)

        // Three distinct keys, each with one network response.
        repeat(3) { server.enqueue(MockResponse().setBody(sampleProgramsJson)) }
        provider.fetchPrograms("ch1", 0)
        provider.fetchPrograms("ch2", 0)
        provider.fetchPrograms("ch3", 0)
        // ch1 was evicted; refetch should hit the network again.
        server.enqueue(MockResponse().setBody(sampleProgramsJson))
        provider.fetchPrograms("ch1", 0)
        assertEquals(4, server.requestCount)
    }
}
