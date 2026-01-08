package com.example.atv.ui.screens.playback

import android.app.Application
import com.example.atv.TestFixtures
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.SwitchChannelUseCase
import com.example.atv.player.AtvPlayer
import com.example.atv.player.PlayerState
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PlaybackViewModel")
class PlaybackViewModelTest {
    
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
    
    private lateinit var viewModel: PlaybackViewModel
    
    private val testDispatcher = StandardTestDispatcher()
    private val playerStateFlow = MutableStateFlow<PlayerState>(PlayerState.Idle)
    
    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)
        
        // Default mocks
        every { atvPlayer.playerState } returns playerStateFlow
        every { atvPlayer.player } returns mockk(relaxed = true)
        every { atvPlayer.initialize() } just runs
        every { channelRepository.getAllChannels() } returns flowOf(emptyList())
        every { preferencesRepository.getLastChannelNumber() } returns flowOf(1)
        coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
    }
    
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    private fun createViewModel(): PlaybackViewModel {
        return PlaybackViewModel(
            application = application,
            atvPlayer = atvPlayer,
            channelRepository = channelRepository,
            preferencesRepository = preferencesRepository,
            switchChannelUseCase = switchChannelUseCase
        )
    }
    
    @Nested
    @DisplayName("V-01: Initial state")
    inner class InitialState {
        
        @Test
        fun `should start with loading state`() = runTest {
            // Given / When
            viewModel = createViewModel()
            
            // Then
            assertTrue(viewModel.uiState.value.isLoading)
        }
        
        @Test
        fun `should start with empty channel list`() = runTest {
            // Given / When
            viewModel = createViewModel()
            
            // Then
            assertTrue(viewModel.uiState.value.channels.isEmpty())
        }
        
        @Test
        fun `should have idle player state initially`() = runTest {
            // Given / When
            viewModel = createViewModel()
            
            // Then
            assertEquals(PlayerState.Idle, viewModel.uiState.value.playerState)
        }
    }
    
    @Nested
    @DisplayName("V-02: Loading channels")
    inner class LoadingChannels {
        
        @Test
        fun `should update channels when repository emits`() = runTest {
            // Given
            val channels = listOf(TestFixtures.SAMPLE_CHANNEL, TestFixtures.SAMPLE_CHANNEL_2)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            every { atvPlayer.playChannel(any()) } just runs
            
            // When
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // Then
            assertEquals(2, viewModel.uiState.value.channels.size)
            assertFalse(viewModel.uiState.value.isLoading)
        }
        
        @Test
        fun `should restore last channel on load`() = runTest {
            // Given
            val channels = listOf(
                TestFixtures.SAMPLE_CHANNEL.copy(number = 1),
                TestFixtures.SAMPLE_CHANNEL_2.copy(number = 2)
            )
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            every { preferencesRepository.getLastChannelNumber() } returns flowOf(2)
            every { atvPlayer.playChannel(any()) } just runs
            
            // When
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // Then
            assertEquals(1, viewModel.uiState.value.currentChannelIndex) // Index 1 = channel number 2
        }
    }
    
    @Nested
    @DisplayName("V-03: Play channel")
    inner class PlayChannel {
        
        @Test
        fun `should call player when playing channel`() = runTest {
            // Given
            val channel = TestFixtures.SAMPLE_CHANNEL
            every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
            every { atvPlayer.playChannel(any()) } just runs
            
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.playChannel(channel)
            advanceUntilIdle()
            
            // Then
            verify { atvPlayer.playChannel(channel) }
        }
        
        @Test
        fun `should save last channel when playing`() = runTest {
            // Given
            val channel = TestFixtures.SAMPLE_CHANNEL.copy(number = 5)
            every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
            every { atvPlayer.playChannel(any()) } just runs
            
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.playChannel(channel)
            advanceUntilIdle()
            
            // Then
            coVerify { preferencesRepository.setLastChannelNumber(5) }
        }
    }
    
    @Nested
    @DisplayName("V-04: Player state updates")
    inner class PlayerStateUpdates {
        
        @Test
        fun `should update UI when player state changes to error`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            playerStateFlow.value = PlayerState.Error(channel = null, message = "Test error")
            advanceUntilIdle()
            
            // Then
            assertTrue(viewModel.uiState.value.showError)
            assertEquals("Test error", viewModel.uiState.value.errorMessage)
        }
        
        @Test
        fun `should update UI when player state changes to playing`() = runTest {
            // Given
            val channel = TestFixtures.SAMPLE_CHANNEL
            every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
            every { atvPlayer.playChannel(any()) } just runs
            
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            playerStateFlow.value = PlayerState.Playing(channel)
            advanceUntilIdle()
            
            // Then
            assertTrue(viewModel.uiState.value.playerState is PlayerState.Playing)
            assertFalse(viewModel.uiState.value.showError)
        }
    }
    
    @Nested
    @DisplayName("V-05: Channel navigation")
    inner class ChannelNavigation {
        
        @Test
        fun `should switch to next channel`() = runTest {
            // Given
            val channel1 = TestFixtures.SAMPLE_CHANNEL.copy(number = 1)
            val channel2 = TestFixtures.SAMPLE_CHANNEL_2.copy(number = 2)
            every { channelRepository.getAllChannels() } returns flowOf(listOf(channel1, channel2))
            every { atvPlayer.playChannel(any()) } just runs
            coEvery { switchChannelUseCase.nextChannel(channel1) } returns channel2
            
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.nextChannel()
            advanceUntilIdle()
            
            // Then
            coVerify { switchChannelUseCase.nextChannel(any()) }
        }
        
        @Test
        fun `should switch to previous channel`() = runTest {
            // Given
            val channel1 = TestFixtures.SAMPLE_CHANNEL.copy(number = 1)
            val channel2 = TestFixtures.SAMPLE_CHANNEL_2.copy(number = 2)
            every { channelRepository.getAllChannels() } returns flowOf(listOf(channel1, channel2))
            every { atvPlayer.playChannel(any()) } just runs
            // Current channel is channel1 (index 0), so previousChannel will be called with channel1
            coEvery { switchChannelUseCase.previousChannel(any()) } returns channel2
            
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.previousChannel()
            advanceUntilIdle()
            
            // Then
            coVerify { switchChannelUseCase.previousChannel(any()) }
        }
    }
    
    @Nested
    @DisplayName("V-06: Overlay management")
    inner class OverlayManagement {
        
        @Test
        fun `should show channel info overlay`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.showChannelInfo()
            
            // Then
            assertTrue(viewModel.uiState.value.showChannelInfo)
        }
        
        @Test
        fun `should show channel list overlay`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.showChannelList()
            
            // Then
            assertTrue(viewModel.uiState.value.showChannelList)
        }
        
        @Test
        fun `should hide channel list when hiding`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showChannelList()
            
            // When
            viewModel.hideChannelList()
            
            // Then
            assertFalse(viewModel.uiState.value.showChannelList)
        }
    }
    
    @Nested
    @DisplayName("V-07: Number pad input")
    inner class NumberPadInput {
        
        @Test
        fun `should show number pad`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            
            // When
            viewModel.showNumberPad()
            
            // Then
            assertTrue(viewModel.uiState.value.showNumberPad)
            assertEquals("", viewModel.uiState.value.numberPadInput)
        }
        
        @Test
        fun `should append digits to number pad`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showNumberPad()
            
            // When
            viewModel.appendNumberPadDigit("1")
            viewModel.appendNumberPadDigit("2")
            viewModel.appendNumberPadDigit("3")
            
            // Then
            assertEquals("123", viewModel.uiState.value.numberPadInput)
        }
        
        @Test
        fun `should ignore leading zero`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showNumberPad()
            
            // When
            viewModel.appendNumberPadDigit("0")
            
            // Then
            assertEquals("", viewModel.uiState.value.numberPadInput)
        }
        
        @Test
        fun `should limit input to 3 digits`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showNumberPad()
            
            // When
            viewModel.appendNumberPadDigit("1")
            viewModel.appendNumberPadDigit("2")
            viewModel.appendNumberPadDigit("3")
            viewModel.appendNumberPadDigit("4")
            
            // Then
            assertEquals("123", viewModel.uiState.value.numberPadInput)
        }
        
        @Test
        fun `should clear number pad input`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showNumberPad()
            viewModel.appendNumberPadDigit("1")
            viewModel.appendNumberPadDigit("2")
            
            // When
            viewModel.clearNumberPadInput()
            
            // Then
            assertEquals("", viewModel.uiState.value.numberPadInput)
        }
        
        @Test
        fun `should backspace number pad digit`() = runTest {
            // Given
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showNumberPad()
            viewModel.appendNumberPadDigit("1")
            viewModel.appendNumberPadDigit("2")
            
            // When
            viewModel.backspaceNumberPadDigit()
            
            // Then
            assertEquals("1", viewModel.uiState.value.numberPadInput)
        }
    }
}
