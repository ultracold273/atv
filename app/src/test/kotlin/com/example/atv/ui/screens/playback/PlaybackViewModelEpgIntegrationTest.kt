package com.example.atv.ui.screens.playback

import android.app.Application
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.Program
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.EpgProvider
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.ResolveStreamUrlUseCase
import com.example.atv.domain.usecase.SwitchChannelUseCase
import com.example.atv.player.AtvPlayerController
import com.example.atv.player.PlayerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * End-to-end integration test for the EPG feature surfaces.
 *
 * Locks in three invariants for spec 004:
 *   1. EPG-disabled + provider-unconfigured: zero fetches.
 *   2. EPG-enabled + provider-unconfigured (the 004 default): zero fetches per FR-019.
 *   3. EPG-enabled + provider-configured + explicit channelCode (driven via the
 *      Phase 3 test seam, since Channel.channelCode is always null in 004 alone):
 *      exactly one fetch and the populated state propagates to the banner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PlaybackViewModel EPG end-to-end integration")
class PlaybackViewModelEpgIntegrationTest {

    private lateinit var application: Application
    private lateinit var atvPlayer: AtvPlayerController
    private lateinit var channelRepository: ChannelRepository
    private lateinit var channelSourceSettingsStore: ChannelSourceSettingsStore
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var switchChannelUseCase: SwitchChannelUseCase
    private lateinit var epgProvider: EpgProvider
    private lateinit var isConfiguredFlow: MutableStateFlow<Boolean>
    private lateinit var playerStateFlow: MutableStateFlow<PlayerState>

    private val testDispatcher = StandardTestDispatcher()
    private val fixedNow = Instant.parse("2026-06-07T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"))

    // Uses the REAL Channel fields. There is no `channelCode` on Channel in 004 —
    // it's exposed via an extension property that always returns null. Tests that
    // need a non-null channelCode use the `loadBannerEpgForCode` test seam below.
    private val sampleChannel = Channel(
        number = 1,
        name = "CCTV-1",
        streamUrl = "http://example.com/cctv1.m3u8",
        groupTitle = "CCTV",
        logoUrl = null,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        atvPlayer = mockk(relaxed = true)
        channelRepository = mockk(relaxed = true)
        channelSourceSettingsStore = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        switchChannelUseCase = mockk(relaxed = true)
        epgProvider = mockk(relaxed = true)
        isConfiguredFlow = MutableStateFlow(false)
        playerStateFlow = MutableStateFlow<PlayerState>(PlayerState.Idle)

        every { epgProvider.isConfigured } returns isConfiguredFlow
        every { atvPlayer.playerState } returns playerStateFlow
        every { atvPlayer.initialize() } just runs
        every { atvPlayer.playChannel(any(), any()) } just runs
        every { channelRepository.getAllChannels() } returns flowOf(listOf(sampleChannel))
        every { channelSourceSettingsStore.observeMode() } returns flowOf(ChannelSourceMode.DIRECT_CTC)
        every { preferencesRepository.getLastChannelNumber() } returns flowOf(1)
        coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(epgEnabled: Boolean): PlaybackViewModel {
        every { preferencesRepository.getUserPreferences() } returns flowOf(
            UserPreferences(
                playlistFilePath = "/test/playlist.m3u8",
                lastChannelNumber = 1,
                epgEnabled = epgEnabled,
            )
        )
        return PlaybackViewModel(
            application = application,
            atvPlayer = atvPlayer,
            channelRepository = channelRepository,
            channelSourceSettingsStore = channelSourceSettingsStore,
            preferencesRepository = preferencesRepository,
            switchChannelUseCase = switchChannelUseCase,
            resolveStreamUrl = ResolveStreamUrlUseCase(),
            epgProvider = epgProvider,
            clock = fixedClock,
        )
    }

    @Test
    fun `epg disabled and provider unconfigured - no fetches on channel switch`() = runTest {
        // Given
        isConfiguredFlow.value = false
        val vm = createViewModel(epgEnabled = false)
        advanceUntilIdle()

        // When — playChannel is the production path triggered by switchToChannel(Int)
        vm.playChannel(sampleChannel)
        advanceTimeBy(500)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { epgProvider.fetchPrograms(any(), any()) }
    }

    @Test
    fun `epg enabled but provider unconfigured - no fetches in the 004 default state`() = runTest {
        // Given - this is the exact 004 shipping state
        isConfiguredFlow.value = false
        val vm = createViewModel(epgEnabled = true)
        advanceUntilIdle()

        // When
        vm.playChannel(sampleChannel)
        advanceTimeBy(500)
        advanceUntilIdle()

        // Then - FR-019: isConfigured=false blocks fetching even with toggle on
        coVerify(exactly = 0) { epgProvider.fetchPrograms(any(), any()) }
    }

    @Test
    fun `epg enabled, configured, explicit channelCode via test seam - one fetch and programs propagate`() = runTest {
        // Given
        val current = Program(
            code = "p-now",
            name = "Morning News",
            start = Instant.parse("2026-06-07T11:30:00Z"),
            end = Instant.parse("2026-06-07T12:30:00Z"),
            isLive = true,
            isReplayable = false,
        )
        val next = Program(
            code = "p-next",
            name = "Weather",
            start = Instant.parse("2026-06-07T12:30:00Z"),
            end = Instant.parse("2026-06-07T13:00:00Z"),
            isLive = false,
            isReplayable = false,
        )
        coEvery {
            epgProvider.fetchPrograms("CCTV1HD", 0)
        } returns Result.success(listOf(current, next))

        isConfiguredFlow.value = true
        val vm = createViewModel(epgEnabled = true)
        advanceUntilIdle()

        // When — drive the fetch directly with an explicit channelCode via the
        // internal Phase 3 test seam. In 004, Channel.channelCode (extension) is
        // always null, so the production switchToChannel/playChannel path never
        // reaches the provider; this seam is the ONLY way to exercise the populated
        // state until 005 puts channelCode on the data class.
        vm.loadBannerEpgForCode(sampleChannel, "CCTV1HD")
        advanceTimeBy(500)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { epgProvider.fetchPrograms("CCTV1HD", 0) }
        val state = vm.uiState.value
        assertNotNull(state.currentProgram)
        assertEquals("Morning News", state.currentProgram?.name)
        assertNotNull(state.nextProgram)
        assertEquals("Weather", state.nextProgram?.name)
    }
}
