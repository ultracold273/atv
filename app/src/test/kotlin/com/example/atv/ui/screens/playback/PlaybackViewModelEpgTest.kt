package com.example.atv.ui.screens.playback

import android.app.Application
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.EpgProvider
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.SwitchChannelUseCase
import com.example.atv.player.AtvPlayer
import com.example.atv.player.PlayerState
import io.mockk.MockKAnnotations
import io.mockk.coEvery
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    private lateinit var atvPlayer: AtvPlayer

    @MockK
    private lateinit var channelRepository: ChannelRepository

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
        every { preferencesRepository.getLastChannelNumber() } returns flowOf(1)
        every { preferencesRepository.getUserPreferences() } returns prefsFlow
        coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
        every { epgProvider.isConfigured } returns isConfiguredFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PlaybackViewModel = PlaybackViewModel(
        application = application,
        atvPlayer = atvPlayer,
        channelRepository = channelRepository,
        preferencesRepository = preferencesRepository,
        switchChannelUseCase = switchChannelUseCase,
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
}
