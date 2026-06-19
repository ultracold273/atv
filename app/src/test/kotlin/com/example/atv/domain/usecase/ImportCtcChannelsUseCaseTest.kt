package com.example.atv.domain.usecase

import com.example.atv.data.epg.CtcAuthClient
import com.example.atv.data.epg.CtcChannelEntry
import com.example.atv.data.epg.CtcChannelFetcher
import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.epg.LoginResult
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.IptvCredentialsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportCtcChannelsUseCaseTest {

    private lateinit var authClient: CtcAuthClient
    private lateinit var fetcher: CtcChannelFetcher
    private lateinit var repo: ChannelRepository
    private lateinit var store: IptvCredentialsStore
    private lateinit var provider: CtcEpgProvider
    private lateinit var useCase: ImportCtcChannelsUseCase

    private val creds = IptvCredentials(
        userId = "1234567890123",
        password = "000000",
        stbId = "0".repeat(32),
        ip = "192.0.2.1",
        mac = "00:00:5E:00:53:01",
        authServerUrl = "http://example.com:8298",
    )

    private fun login() = LoginResult.Success(
        epgLbBase = "http://lb/iptvepg/function/",
        jsessionId = "JS",
        config = emptyMap(),
        userToken = "tok",
    )

    @BeforeEach
    fun setUp() {
        authClient = mockk()
        fetcher = mockk()
        repo = mockk()
        store = mockk()
        provider = mockk(relaxed = true)
        useCase = ImportCtcChannelsUseCase(authClient, fetcher, repo, store, provider)
    }

    @Test
    fun `returns LoginFailure when credentials are missing`() = runTest {
        coEvery { store.read() } returns null

        val r = useCase()
        assertTrue(r is ImportResult.LoginFailure)
        assertTrue((r as ImportResult.LoginFailure).reason.contains("no credentials", ignoreCase = true))
    }

    @Test
    fun `returns LoginFailure when credentials are incomplete`() = runTest {
        coEvery { store.read() } returns creds.copy(password = "")

        val r = useCase()
        assertTrue(r is ImportResult.LoginFailure)
        assertTrue((r as ImportResult.LoginFailure).reason.contains("incomplete", ignoreCase = true))
    }

    @Test
    fun `returns LoginFailure when login itself fails`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns LoginResult.Failure("auth refused")

        val r = useCase()
        assertTrue(r is ImportResult.LoginFailure)
        assertEquals("auth refused", (r as ImportResult.LoginFailure).reason)
    }

    @Test
    fun `returns FetchFailure when channel fetch fails after a successful login`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns login()
        coEvery { fetcher.fetch(any()) } returns Result.failure(RuntimeException("boom"))

        val r = useCase()
        assertTrue(r is ImportResult.FetchFailure)
        assertEquals("boom", (r as ImportResult.FetchFailure).reason)
    }

    @Test
    fun `returns NoChannelsReturned when fetch succeeds with empty list`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns login()
        coEvery { fetcher.fetch(any()) } returns Result.success(emptyList())

        val r = useCase()
        assertTrue(r is ImportResult.NoChannelsReturned)
    }

    @Test
    fun `returns Success and persists mapped channels and flips isConfigured`() = runTest {
        val entries = listOf(
            CtcChannelEntry("ch1", "CCTV-1", "001", "igmp://239.0.0.1", displayNumber = 1),
            CtcChannelEntry("ch2", "CCTV-2", "002", "igmp://239.0.0.2", displayNumber = 2),
        )
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns login()
        coEvery { fetcher.fetch(any()) } returns Result.success(entries)
        val saved = slot<List<Channel>>()
        coEvery { repo.savePlaylistChannels(capture(saved)) } just runs

        val r = useCase()
        assertTrue(r is ImportResult.Success)
        assertEquals(2, (r as ImportResult.Success).importedCount)

        // Mapping check
        val out = saved.captured
        assertEquals(1, out[0].number)
        assertEquals("CCTV-1", out[0].name)
        assertEquals("igmp://239.0.0.1", out[0].streamUrl)
        assertEquals("ch1", out[0].channelCode)
        assertEquals(2, out[1].number)
        assertEquals("ch2", out[1].channelCode)

        coVerify { provider.markConfigured(true) }
    }

    @Test
    fun `does not flip isConfigured on any failure path`() = runTest {
        coEvery { store.read() } returns creds
        coEvery { authClient.login(creds) } returns LoginResult.Failure("nope")

        useCase()

        coVerify(exactly = 0) { provider.markConfigured(any()) }
    }
}
