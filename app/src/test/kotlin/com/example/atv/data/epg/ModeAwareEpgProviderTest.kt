package com.example.atv.data.epg

import com.example.atv.data.proxy.ProxyEpgProvider
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.Program
import com.example.atv.domain.model.ProxySettings
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ModeAwareEpgProviderTest {

    private val mode = MutableStateFlow(ChannelSourceMode.M3U8)
    private val proxySettings = MutableStateFlow<ProxySettings?>(null)
    private val directConfigured = MutableStateFlow(false)
    private val settingsStore = mockk<ChannelSourceSettingsStore> {
        every { observeMode() } returns mode
        every { observeProxySettings() } returns proxySettings
    }
    private val ctcProvider = mockk<CtcEpgProvider> {
        every { isConfigured } returns directConfigured
    }
    private val proxyProvider = mockk<ProxyEpgProvider>()

    @Test
    fun `configured state follows source mode`() = runTest {
        val provider = provider()
        runCurrent()

        assertFalse(provider.isConfigured.value)

        mode.value = ChannelSourceMode.HOME_PROXY
        proxySettings.value = ProxySettings("http://openwrt:8088", "token")
        runCurrent()
        assertTrue(provider.isConfigured.value)

        mode.value = ChannelSourceMode.DIRECT_CTC
        runCurrent()
        assertFalse(provider.isConfigured.value)
        directConfigured.value = true
        runCurrent()
        assertTrue(provider.isConfigured.value)
    }

    @Test
    fun `fetchPrograms delegates to proxy provider in home proxy mode`() = runTest {
        coEvery { settingsStore.readMode() } returns ChannelSourceMode.HOME_PROXY
        coEvery { proxyProvider.fetchPrograms("ch1", 0) } returns Result.success(listOf(program("proxy")))
        val provider = provider()

        val result = provider.fetchPrograms("ch1", 0)

        assertEquals("proxy", result.getOrThrow().single().code)
        coVerify(exactly = 1) { proxyProvider.fetchPrograms("ch1", 0) }
        coVerify(exactly = 0) { ctcProvider.fetchPrograms(any(), any()) }
    }

    @Test
    fun `fetchPrograms delegates to direct provider in direct ctc mode`() = runTest {
        coEvery { settingsStore.readMode() } returns ChannelSourceMode.DIRECT_CTC
        coEvery { ctcProvider.fetchPrograms("ch1", 0) } returns Result.success(listOf(program("direct")))
        val provider = provider()

        val result = provider.fetchPrograms("ch1", 0)

        assertEquals("direct", result.getOrThrow().single().code)
        coVerify(exactly = 1) { ctcProvider.fetchPrograms("ch1", 0) }
        coVerify(exactly = 0) { proxyProvider.fetchPrograms(any(), any()) }
    }

    @Test
    fun `fetchPrograms fails in m3u8 mode`() = runTest {
        coEvery { settingsStore.readMode() } returns ChannelSourceMode.M3U8
        val provider = provider()

        val result = provider.fetchPrograms("ch1", 0)

        assertTrue(result.isFailure)
    }

    private fun kotlinx.coroutines.test.TestScope.provider() = ModeAwareEpgProvider(
        sourceSettingsStore = settingsStore,
        ctcEpgProvider = ctcProvider,
        proxyEpgProvider = proxyProvider,
        applicationScope = backgroundScope,
    )

    private fun program(code: String) = Program(
        code = code,
        name = "News",
        start = Instant.parse("2026-06-07T08:00:00Z"),
        end = Instant.parse("2026-06-07T09:00:00Z"),
        isLive = true,
        isReplayable = false,
    )
}
