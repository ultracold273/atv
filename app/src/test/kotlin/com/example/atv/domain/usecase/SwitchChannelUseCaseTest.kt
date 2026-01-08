package com.example.atv.domain.usecase

import com.example.atv.TestFixtures
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SwitchChannelUseCase")
class SwitchChannelUseCaseTest {
    
    @MockK
    private lateinit var channelRepository: ChannelRepository
    
    @MockK
    private lateinit var preferencesRepository: PreferencesRepository
    
    private lateinit var useCase: SwitchChannelUseCase
    
    private val channel1 = TestFixtures.SAMPLE_CHANNEL.copy(number = 1, name = "Channel 1")
    private val channel2 = TestFixtures.SAMPLE_CHANNEL_2.copy(number = 2, name = "Channel 2")
    private val channel3 = TestFixtures.SAMPLE_CHANNEL.copy(number = 3, name = "Channel 3")
    
    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        
        useCase = SwitchChannelUseCase(
            channelRepository = channelRepository,
            preferencesRepository = preferencesRepository
        )
    }
    
    @Nested
    @DisplayName("UC-01: Next channel")
    inner class NextChannel {
        
        @Test
        fun `should return next channel in list`() = runTest {
            // Given
            val channels = listOf(channel1, channel2, channel3)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.nextChannel(channel1)
            
            // Then
            assertEquals(channel2, result)
        }
        
        @Test
        fun `should wrap to first channel when at end`() = runTest {
            // Given
            val channels = listOf(channel1, channel2, channel3)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.nextChannel(channel3)
            
            // Then
            assertEquals(channel1, result)
        }
        
        @Test
        fun `should return first channel when current is null`() = runTest {
            // Given
            val channels = listOf(channel1, channel2, channel3)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.nextChannel(null)
            
            // Then
            assertEquals(channel1, result)
        }
        
        @Test
        fun `should return null when no channels exist`() = runTest {
            // Given
            every { channelRepository.getAllChannels() } returns flowOf(emptyList())
            
            // When
            val result = useCase.nextChannel(channel1)
            
            // Then
            assertNull(result)
        }
        
        @Test
        fun `should save last channel number`() = runTest {
            // Given
            val channels = listOf(channel1, channel2)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            useCase.nextChannel(channel1)
            
            // Then
            coVerify { preferencesRepository.setLastChannelNumber(2) }
        }
    }
    
    @Nested
    @DisplayName("UC-02: Previous channel")
    inner class PreviousChannel {
        
        @Test
        fun `should return previous channel in list`() = runTest {
            // Given
            val channels = listOf(channel1, channel2, channel3)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.previousChannel(channel2)
            
            // Then
            assertEquals(channel1, result)
        }
        
        @Test
        fun `should wrap to last channel when at beginning`() = runTest {
            // Given
            val channels = listOf(channel1, channel2, channel3)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.previousChannel(channel1)
            
            // Then
            assertEquals(channel3, result)
        }
        
        @Test
        fun `should return last channel when current is null`() = runTest {
            // Given
            val channels = listOf(channel1, channel2, channel3)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.previousChannel(null)
            
            // Then
            assertEquals(channel3, result)
        }
        
        @Test
        fun `should return null when no channels exist`() = runTest {
            // Given
            every { channelRepository.getAllChannels() } returns flowOf(emptyList())
            
            // When
            val result = useCase.previousChannel(channel1)
            
            // Then
            assertNull(result)
        }
        
        @Test
        fun `should save last channel number`() = runTest {
            // Given
            val channels = listOf(channel1, channel2)
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            useCase.previousChannel(channel2)
            
            // Then
            coVerify { preferencesRepository.setLastChannelNumber(1) }
        }
    }
    
    @Nested
    @DisplayName("UC-03: Switch to specific channel")
    inner class SwitchToChannel {
        
        @Test
        fun `should return channel by number`() = runTest {
            // Given
            coEvery { channelRepository.getChannel(5) } returns channel1.copy(number = 5)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            val result = useCase.switchToChannel(5)
            
            // Then
            assertNotNull(result)
            assertEquals(5, result?.number)
        }
        
        @Test
        fun `should return null when channel does not exist`() = runTest {
            // Given
            coEvery { channelRepository.getChannel(999) } returns null
            
            // When
            val result = useCase.switchToChannel(999)
            
            // Then
            assertNull(result)
        }
        
        @Test
        fun `should save last channel number when found`() = runTest {
            // Given
            coEvery { channelRepository.getChannel(5) } returns channel1.copy(number = 5)
            coEvery { preferencesRepository.setLastChannelNumber(any()) } just runs
            
            // When
            useCase.switchToChannel(5)
            
            // Then
            coVerify { preferencesRepository.setLastChannelNumber(5) }
        }
        
        @Test
        fun `should not save when channel not found`() = runTest {
            // Given
            coEvery { channelRepository.getChannel(999) } returns null
            
            // When
            useCase.switchToChannel(999)
            
            // Then
            coVerify(exactly = 0) { preferencesRepository.setLastChannelNumber(any()) }
        }
    }
    
    @Nested
    @DisplayName("UC-04: Get initial channel")
    inner class GetInitialChannel {
        
        @Test
        fun `should return last watched channel`() = runTest {
            // Given
            every { preferencesRepository.getLastChannelNumber() } returns flowOf(5)
            coEvery { channelRepository.getChannel(5) } returns channel1.copy(number = 5)
            
            // When
            val result = useCase.getInitialChannel()
            
            // Then
            assertNotNull(result)
            assertEquals(5, result?.number)
        }
        
        @Test
        fun `should return first channel when last not found`() = runTest {
            // Given
            val channels = listOf(channel1, channel2)
            every { preferencesRepository.getLastChannelNumber() } returns flowOf(999)
            coEvery { channelRepository.getChannel(999) } returns null
            every { channelRepository.getAllChannels() } returns flowOf(channels)
            
            // When
            val result = useCase.getInitialChannel()
            
            // Then
            assertEquals(channel1, result)
        }
        
        @Test
        fun `should return null when no channels exist`() = runTest {
            // Given
            every { preferencesRepository.getLastChannelNumber() } returns flowOf(1)
            coEvery { channelRepository.getChannel(1) } returns null
            every { channelRepository.getAllChannels() } returns flowOf(emptyList())
            
            // When
            val result = useCase.getInitialChannel()
            
            // Then
            assertNull(result)
        }
    }
}
