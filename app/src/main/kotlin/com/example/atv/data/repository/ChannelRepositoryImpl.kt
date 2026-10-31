package com.example.atv.data.repository

import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.local.db.toDomain
import com.example.atv.data.local.db.toEntity
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ChannelCatalogSnapshot
import com.example.atv.domain.repository.ChannelCatalogRepository
import com.example.atv.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChannelRepository using Room.
 */
@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao
) : ChannelRepository, ChannelCatalogRepository {
    private val catalogMutex = Mutex()

    override suspend fun <T> withSnapshot(block: suspend (ChannelCatalogSnapshot) -> T): T =
        catalogMutex.withLock {
            block(ChannelCatalogSnapshot.from(channelDao.getSnapshot().map { it.toDomain() }))
        }
    
    override fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun getChannel(number: Int): Channel? {
        return channelDao.getChannel(number)?.toDomain()
    }
    
    override suspend fun getChannelCount(): Int {
        return channelDao.getChannelCount()
    }
    
    override suspend fun savePlaylistChannels(channels: List<Channel>) = catalogMutex.withLock {
        val entities = channels.map { it.toEntity(isManuallyAdded = false) }
        channelDao.replacePlaylistChannels(entities)
    }
    
    override suspend fun addChannel(channel: Channel) = catalogMutex.withLock {
        val entity = channel.toEntity(isManuallyAdded = true)
        channelDao.insert(entity)
    }
    
    override suspend fun updateChannel(channel: Channel) = catalogMutex.withLock {
        // Preserve the isManuallyAdded flag
        val existing = channelDao.getChannel(channel.number)
        val entity = channel.toEntity(isManuallyAdded = existing?.isManuallyAdded ?: false)
        channelDao.update(entity)
    }
    
    override suspend fun deleteChannel(channel: Channel) = catalogMutex.withLock {
        val entity = channel.toEntity()
        channelDao.delete(entity)
    }
    
    override suspend fun clearPlaylistChannels() = catalogMutex.withLock {
        channelDao.deletePlaylistChannels()
    }
    
    override suspend fun clearAll() = catalogMutex.withLock {
        channelDao.deleteAll()
    }
}
