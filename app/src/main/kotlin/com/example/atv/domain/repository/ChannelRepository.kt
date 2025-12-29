package com.example.atv.domain.repository

import com.example.atv.domain.model.Channel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for channel operations.
 */
interface ChannelRepository {
    
    /**
     * Get all channels as a Flow.
     */
    fun getAllChannels(): Flow<List<Channel>>
    
    /**
     * Get a channel by number.
     */
    suspend fun getChannel(number: Int): Channel?
    
    /**
     * Get total channel count.
     */
    suspend fun getChannelCount(): Int
    
    /**
     * Save channels from a playlist (replaces existing playlist channels).
     */
    suspend fun savePlaylistChannels(channels: List<Channel>)
    
    /**
     * Add a manually created channel.
     */
    suspend fun addChannel(channel: Channel)
    
    /**
     * Update an existing channel.
     */
    suspend fun updateChannel(channel: Channel)
    
    /**
     * Delete a channel.
     */
    suspend fun deleteChannel(channel: Channel)
    
    /**
     * Clear all channels from the playlist (keeps manually added).
     */
    suspend fun clearPlaylistChannels()
    
    /**
     * Clear all channels.
     */
    suspend fun clearAll()
}
