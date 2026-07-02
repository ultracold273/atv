package com.example.atv.ui.screens.playback

import android.app.Application
import com.example.atv.R
import com.example.atv.TestFixtures
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
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PlaybackViewModel EPG")
class PlaybackViewModelEpgTest {

    @MockK
    private lateinit var application: Application

    @MockK
    private lateinit var atvPlayer: AtvPlayerController

    @MockK
    private lateinit var channelRepository: ChannelRepository

    @MockK
    private lateinit var channelSourceSettingsStore: ChannelSourceSettingsStore

    @MockK
    private lateinit var preferencesRepository: PreferencesRepository

    @MockK
    private lateinit var switchChannelUseCase: SwitchChannelUseCase

    @MockK
    private lateinit var epgProvider: EpgProvider

    private lateinit var viewModel: PlaybackViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val playerStateFlow = MutableStateFlow<PlayerState>(PlayerState.Idle)
    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val isConfiguredFlow = MutableStateFlow(false)

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-06-07T10:00:00Z"), ZoneOffset.UTC)

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        every { atvPlayer.playerState } returns playerStateFlow
        every { atvPlayer.player } returns mockk(relaxed = true)
        every { atvPlayer.initialize() } just runs
        every { channelRepository.getAllChannels() } returns flowOf(emptyList())
        every { channelSourceSettingsStore.observeMode() } returns flowOf(ChannelSourceMode.DIRECT_CTC)
        every { preferencesRepository.getLastChannelNumber() } returns flowOf(1)
        every { preferencesRepository.getUserPreferences() } returns prefsFlow
        coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
        every { epgProvider.isConfigured } returns isConfiguredFlow
        every { application.getString(R.string.epg_load_error) } returns "Unable to load programs"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PlaybackViewModel = PlaybackViewModel(
        application = application,
        atvPlayer = atvPlayer,
        channelRepository = channelRepository,
        channelSourceSettingsStore = channelSourceSettingsStore,
        preferencesRepository = preferencesRepository,
        switchChannelUseCase = switchChannelUseCase,
        resolveStreamUrl = ResolveStreamUrlUseCase(),
        epgProvider = epgProvider,
        clock = fixedClock
    )

    @Test
    fun `epgEnabled defaults to false and reflects preference flow`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.epgEnabled)

        prefsFlow.value = UserPreferences(epgEnabled = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.epgEnabled)
    }

    @Test
    fun `epgConfigured reflects provider flow`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.epgConfigured)

        isConfiguredFlow.value = true
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.epgConfigured)
    }

    @Test
    fun `toggling epgEnabled off clears banner programs and panel state`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        viewModel = createViewModel()
        advanceUntilIdle()

        prefsFlow.value = UserPreferences(epgEnabled = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentProgram)
        assertNull(state.nextProgram)
        assertEquals(EpgPanelState(), state.epgPanel)
    }

    @Test
    fun `playChannel emits null current and next when channel has no channelCode`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        val channel = TestFixtures.SAMPLE_CHANNEL
        every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
        every { atvPlayer.playChannel(any()) } just runs
        coEvery { epgProvider.fetchPrograms(any(), any()) } returns Result.success(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.playChannel(channel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.currentProgram)
        assertNull(state.nextProgram)
        coVerify(exactly = 0) { epgProvider.fetchPrograms(any(), any()) }
    }

    @Test
    fun `playChannel populates current and next programs when provider returns data`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        val channel = TestFixtures.SAMPLE_CHANNEL
        every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
        every { atvPlayer.playChannel(any()) } just runs

        val nowProgram = Program(
            code = "p1",
            name = "News",
            start = Instant.parse("2026-06-07T09:30:00Z"),
            end = Instant.parse("2026-06-07T10:30:00Z"),
            isLive = true,
            isReplayable = false
        )
        val nextProgram = Program(
            code = "p2",
            name = "Weather",
            start = Instant.parse("2026-06-07T10:30:00Z"),
            end = Instant.parse("2026-06-07T11:00:00Z"),
            isLive = false,
            isReplayable = false
        )
        coEvery { epgProvider.fetchPrograms("CCTV-1", 0) } returns
            Result.success(listOf(nowProgram, nextProgram))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Drive the EPG flow directly with an explicit channel code via the test seam.
        viewModel.loadBannerEpgForCode(channel, channelCode = "CCTV-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.currentProgram)
        assertEquals("News", state.currentProgram?.name)
        assertEquals("Weather", state.nextProgram?.name)
    }

    @Test
    fun `toggling epgEnabled off after a populated banner clears current and next`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        val channel = TestFixtures.SAMPLE_CHANNEL
        val nowProgram = Program(
            code = "p1",
            name = "News",
            start = Instant.parse("2026-06-07T09:30:00Z"),
            end = Instant.parse("2026-06-07T10:30:00Z"),
            isLive = true,
            isReplayable = false
        )
        coEvery { epgProvider.fetchPrograms("CCTV-1", 0) } returns
            Result.success(listOf(nowProgram))
        every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
        every { atvPlayer.playChannel(any()) } just runs

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadBannerEpgForCode(channel, channelCode = "CCTV-1")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.currentProgram)

        prefsFlow.value = UserPreferences(epgEnabled = false)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentProgram)
        assertNull(viewModel.uiState.value.nextProgram)
    }

    @Test
    fun `five rapid onChannelFocused calls coalesce into one provider call`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        every { channelRepository.getAllChannels() } returns flowOf(emptyList())
        coEvery { epgProvider.fetchPrograms(any(), any()) } returns Result.success(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        val ch = TestFixtures.SAMPLE_CHANNEL
        repeat(5) { viewModel.onChannelFocusedWithCode(ch, "CCTV-1") }

        advanceTimeBy(300)  // past the 250ms debounce
        advanceUntilIdle()

        coVerify(exactly = 1) { epgProvider.fetchPrograms("CCTV-1", 0) }
    }

    @Test
    fun `sequential focus A then B cancels A and only B resolves`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        every { channelRepository.getAllChannels() } returns flowOf(emptyList())
        coEvery { epgProvider.fetchPrograms("A", 0) } returns Result.success(emptyList())
        coEvery { epgProvider.fetchPrograms("B", 0) } returns Result.success(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        val chA = TestFixtures.SAMPLE_CHANNEL.copy(number = 1, name = "A")
        val chB = TestFixtures.SAMPLE_CHANNEL.copy(number = 2, name = "B")
        viewModel.onChannelFocusedWithCode(chA, "A")
        advanceTimeBy(50)
        viewModel.onChannelFocusedWithCode(chB, "B")
        advanceTimeBy(300)
        advanceUntilIdle()

        coVerify(exactly = 0) { epgProvider.fetchPrograms("A", 0) }
        coVerify(exactly = 1) { epgProvider.fetchPrograms("B", 0) }
        assertEquals(chB, viewModel.uiState.value.epgPanel.focusedChannel)
    }

    @Test
    fun `setEpgDateOffset rejects values outside -1 to 1`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThrows(IllegalArgumentException::class.java) { viewModel.setEpgDateOffset(2) }
        assertThrows(IllegalArgumentException::class.java) { viewModel.setEpgDateOffset(-2) }
    }

    @Test
    fun `focusing a different channel resets EPG date offset to today`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        every { channelRepository.getAllChannels() } returns flowOf(emptyList())
        coEvery { epgProvider.fetchPrograms(any(), any()) } returns Result.success(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        val chA = TestFixtures.SAMPLE_CHANNEL.copy(number = 1)

        // Focus channel A on Yesterday, letting the panel flow write the offset into uiState.
        viewModel.onChannelFocusedWithCode(chA, "A")
        viewModel.setEpgDateOffset(1)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.epgPanel.dateOffset)

        // Moving to a different channel must snap the day back to Today immediately.
        val chB = TestFixtures.SAMPLE_CHANNEL.copy(number = 2)
        viewModel.onChannelFocused(chB)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.epgPanel.dateOffset)
    }

    @Test
    fun `re-focusing the same channel does not reset the date offset`() = runTest {
        prefsFlow.value = UserPreferences(epgEnabled = true)
        isConfiguredFlow.value = true
        every { channelRepository.getAllChannels() } returns flowOf(emptyList())
        coEvery { epgProvider.fetchPrograms(any(), any()) } returns Result.success(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        val ch = TestFixtures.SAMPLE_CHANNEL.copy(number = 1)
        viewModel.onChannelFocusedWithCode(ch, "A")
        viewModel.setEpgDateOffset(1)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.epgPanel.dateOffset)

        // Re-focusing the SAME channel number must not reset a manually chosen day.
        viewModel.onChannelFocused(ch.copy(channelCode = "A"))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.epgPanel.dateOffset)
    }
}
