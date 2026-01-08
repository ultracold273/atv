package com.example.atv.data.repository

import app.cash.turbine.test
import com.example.atv.TestFixtures
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.local.db.ChannelEntity
import com.example.atv.data.local.db.toDomain
import com.example.atv.data.local.db.toEntity
import com.example.atv.domain.model.Channel
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChannelRepositoryImpl")
class ChannelRepositoryImplTest {
    
    @MockK
    private lateinit var channelDao: ChannelDao
    
    private lateinit var repository: ChannelRepositoryImpl
    
    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        repository = ChannelRepositoryImpl(channelDao)
    }
    
    @Nested
    @DisplayName("R-01: Get all channels")
    inner class GetAllChannels {
        
        @Test
        fun `should return channels from DAO as domain models`() = runTest {
            // Given
            val entity = ChannelEntity(
                number = 1,
                name = "Test Channel",
                streamUrl = "https://example.com/stream.m3u8",
                groupTitle = "Test Group",
                logoUrl = "https://example.com/logo.png",
                isManuallyAdded = false
            )
            every { channelDao.getAllChannels() } returns flowOf(listOf(entity))
            
            // When / Then
            repository.getAllChannels().test {
                val channels = awaitItem()
                assertEquals(1, channels.size)
                assertEquals("Test Channel", channels[0].name)
                assertEquals("https://example.com/stream.m3u8", channels[0].streamUrl)
                cancelAndConsumeRemainingEvents()
            }
        }
        
        @Test
        fun `should return empty list when no channels exist`() = runTest {
            // Given
            every { channelDao.getAllChannels() } returns flowOf(emptyList())
            
            // When / Then
            repository.getAllChannels().test {
                val channels = awaitItem()
                assertTrue(channels.isEmpty())
                cancelAndConsumeRemainingEvents()
            }
        }
    }
    
    @Nested
    @DisplayName("R-02: Get channel by number")
    inner class GetChannelByNumber {
        
        @Test
        fun `should return channel when exists`() = runTest {
            // Given
            val entity = ChannelEntity(
                number = 5,
                name = "Channel 5",
                streamUrl = "https://example.com/ch5.m3u8",
                groupTitle = null,
                logoUrl = null,
                isManuallyAdded = false
            )
            coEvery { channelDao.getChannel(5) } returns entity
            
            // When
            val result = repository.getChannel(5)
            
            // Then
            assertNotNull(result)
            assertEquals("Channel 5", result?.name)
            assertEquals(5, result?.number)
        }
        
        @Test
        fun `should return null when channel does not exist`() = runTest {
            // Given
            coEvery { channelDao.getChannel(999) } returns null
            
            // When
            val result = repository.getChannel(999)
            
            // Then
            assertNull(result)
        }
    }
    
    @Nested
    @DisplayName("R-03: Save playlist channels")
    inner class SavePlaylistChannels {
        
        @Test
        fun `should delete existing and insert new channels`() = runTest {
            // Given
            val channels = listOf(
                TestFixtures.SAMPLE_CHANNEL,
                TestFixtures.SAMPLE_CHANNEL_2
            )
            coEvery { channelDao.deletePlaylistChannels() } just runs
            coEvery { channelDao.insertAll(any()) } just runs
            
            // When
            repository.savePlaylistChannels(channels)
            
            // Then
            coVerify(exactly = 1) { channelDao.deletePlaylistChannels() }
            coVerify(exactly = 1) { channelDao.insertAll(match { it.size == 2 }) }
        }
        
        @Test
        fun `should mark channels as not manually added`() = runTest {
            // Given
            val channels = listOf(TestFixtures.SAMPLE_CHANNEL)
            val capturedEntities = slot<List<ChannelEntity>>()
            coEvery { channelDao.deletePlaylistChannels() } just runs
            coEvery { channelDao.insertAll(capture(capturedEntities)) } just runs
            
            // When
            repository.savePlaylistChannels(channels)
            
            // Then
            assertFalse(capturedEntities.captured[0].isManuallyAdded)
        }
    }
    
    @Nested
    @DisplayName("R-04: Add manual channel")
    inner class AddChannel {
        
        @Test
        fun `should insert channel with isManuallyAdded true`() = runTest {
            // Given
            val channel = TestFixtures.SAMPLE_CHANNEL
            val capturedEntity = slot<ChannelEntity>()
            coEvery { channelDao.insert(capture(capturedEntity)) } just runs
            
            // When
            repository.addChannel(channel)
            
            // Then
            coVerify(exactly = 1) { channelDao.insert(any()) }
            assertTrue(capturedEntity.captured.isManuallyAdded)
        }
    }
    
    @Nested
    @DisplayName("R-05: Update channel")
    inner class UpdateChannel {
        
        @Test
        fun `should preserve isManuallyAdded flag on update`() = runTest {
            // Given
            val existingEntity = ChannelEntity(
                number = 1,
                name = "Old Name",
                streamUrl = "https://example.com/old.m3u8",
                groupTitle = null,
                logoUrl = null,
                isManuallyAdded = true
            )
            val updatedChannel = TestFixtures.SAMPLE_CHANNEL
            val capturedEntity = slot<ChannelEntity>()
            
            coEvery { channelDao.getChannel(1) } returns existingEntity
            coEvery { channelDao.update(capture(capturedEntity)) } just runs
            
            // When
            repository.updateChannel(updatedChannel)
            
            // Then
            assertTrue(capturedEntity.captured.isManuallyAdded)
        }
        
        @Test
        fun `should set isManuallyAdded false when no existing channel`() = runTest {
            // Given
            val channel = TestFixtures.SAMPLE_CHANNEL
            val capturedEntity = slot<ChannelEntity>()
            
            coEvery { channelDao.getChannel(any()) } returns null
            coEvery { channelDao.update(capture(capturedEntity)) } just runs
            
            // When
            repository.updateChannel(channel)
            
            // Then
            assertFalse(capturedEntity.captured.isManuallyAdded)
        }
    }
    
    @Nested
    @DisplayName("R-06: Delete and clear operations")
    inner class DeleteOperations {
        
        @Test
        fun `should delete specific channel`() = runTest {
            // Given
            val channel = TestFixtures.SAMPLE_CHANNEL
            coEvery { channelDao.delete(any()) } just runs
            
            // When
            repository.deleteChannel(channel)
            
            // Then
            coVerify(exactly = 1) { channelDao.delete(any()) }
        }
        
        @Test
        fun `should clear playlist channels only`() = runTest {
            // Given
            coEvery { channelDao.deletePlaylistChannels() } just runs
            
            // When
            repository.clearPlaylistChannels()
            
            // Then
            coVerify(exactly = 1) { channelDao.deletePlaylistChannels() }
        }
        
        @Test
        fun `should clear all channels`() = runTest {
            // Given
            coEvery { channelDao.deleteAll() } just runs
            
            // When
            repository.clearAll()
            
            // Then
            coVerify(exactly = 1) { channelDao.deleteAll() }
        }
    }
    
    @Nested
    @DisplayName("Channel count")
    inner class ChannelCount {
        
        @Test
        fun `should return channel count from DAO`() = runTest {
            // Given
            coEvery { channelDao.getChannelCount() } returns 42
            
            // When
            val count = repository.getChannelCount()
            
            // Then
            assertEquals(42, count)
        }
    }
}
