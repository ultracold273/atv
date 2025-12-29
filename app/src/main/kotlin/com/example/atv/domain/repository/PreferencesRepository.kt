package com.example.atv.domain.repository

import com.example.atv.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user preferences.
 */
interface PreferencesRepository {
    
    /**
     * Get user preferences as a Flow.
     */
    fun getUserPreferences(): Flow<UserPreferences>
    
    /**
     * Get last watched channel number as a Flow.
     */
    fun getLastChannelNumber(): Flow<Int>
    
    /**
     * Get playlist file path as a Flow.
     */
    fun getPlaylistFilePath(): Flow<String?>
    
    /**
     * Update last watched channel number.
     */
    suspend fun setLastChannelNumber(number: Int)
    
    /**
     * Update playlist file path.
     */
    suspend fun setPlaylistFilePath(path: String?)
    
    /**
     * Update auto-play on launch setting.
     */
    suspend fun setAutoPlayOnLaunch(enabled: Boolean)
    
    /**
     * Clear all preferences.
     */
    suspend fun clear()
}
