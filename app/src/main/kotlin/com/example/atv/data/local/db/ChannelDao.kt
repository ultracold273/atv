package com.example.atv.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for channel operations.
 */
@Dao
interface ChannelDao {
    
    /**
     * Get all channels ordered by number.
     */
    @Query("SELECT * FROM channels ORDER BY number ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>
    
    /**
     * Get a single channel by number.
     */
    @Query("SELECT * FROM channels WHERE number = :number")
    suspend fun getChannel(number: Int): ChannelEntity?
    
    /**
     * Get total channel count.
     */
    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelCount(): Int
    
    /**
     * Get the maximum channel number.
     */
    @Query("SELECT MAX(number) FROM channels")
    suspend fun getMaxChannelNumber(): Int?
    
    /**
     * Insert multiple channels (replaces on conflict).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)
    
    /**
     * Insert a single channel.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity)
    
    /**
     * Update a channel.
     */
    @Update
    suspend fun update(channel: ChannelEntity)
    
    /**
     * Delete a channel.
     */
    @Delete
    suspend fun delete(channel: ChannelEntity)
    
    /**
     * Delete all channels loaded from playlist (not manually added).
     */
    @Query("DELETE FROM channels WHERE is_manually_added = 0")
    suspend fun deletePlaylistChannels()
    
    /**
     * Delete all channels.
     */
    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}
