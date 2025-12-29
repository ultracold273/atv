package com.example.atv.data.repository

import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.local.db.toDomain
import com.example.atv.data.local.db.toEntity
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChannelRepository using Room.
 */
@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao
) : ChannelRepository {
    
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
    
    override suspend fun savePlaylistChannels(channels: List<Channel>) {
        // Delete existing playlist channels first
        channelDao.deletePlaylistChannels()
        
        // Insert new channels
        val entities = channels.map { it.toEntity(isManuallyAdded = false) }
        channelDao.insertAll(entities)
    }
    
    override suspend fun addChannel(channel: Channel) {
        val entity = channel.toEntity(isManuallyAdded = true)
        channelDao.insert(entity)
    }
    
    override suspend fun updateChannel(channel: Channel) {
        // Preserve the isManuallyAdded flag
        val existing = channelDao.getChannel(channel.number)
        val entity = channel.toEntity(isManuallyAdded = existing?.isManuallyAdded ?: false)
        channelDao.update(entity)
    }
    
    override suspend fun deleteChannel(channel: Channel) {
        val entity = channel.toEntity()
        channelDao.delete(entity)
    }
    
    override suspend fun clearPlaylistChannels() {
        channelDao.deletePlaylistChannels()
    }
    
    override suspend fun clearAll() {
        channelDao.deleteAll()
    }
}
