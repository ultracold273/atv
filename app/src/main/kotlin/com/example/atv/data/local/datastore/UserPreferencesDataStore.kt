package com.example.atv.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.atv.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * DataStore wrapper for user preferences.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private object Keys {
        val LAST_CHANNEL_NUMBER = intPreferencesKey("last_channel_number")
        val PLAYLIST_FILE_PATH = stringPreferencesKey("playlist_file_path")
        val AUTO_PLAY_ON_LAUNCH = booleanPreferencesKey("auto_play_on_launch")
    }
    
    /**
     * Flow of user preferences.
     */
    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            UserPreferences(
                lastChannelNumber = preferences[Keys.LAST_CHANNEL_NUMBER] ?: 1,
                playlistFilePath = preferences[Keys.PLAYLIST_FILE_PATH],
                autoPlayOnLaunch = preferences[Keys.AUTO_PLAY_ON_LAUNCH] ?: true
            )
        }
    
    /**
     * Flow of last watched channel number.
     */
    val lastChannelNumber: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[Keys.LAST_CHANNEL_NUMBER] ?: 1 }
    
    /**
     * Flow of playlist file path.
     */
    val playlistFilePath: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[Keys.PLAYLIST_FILE_PATH] }
    
    /**
     * Update last watched channel number.
     */
    suspend fun setLastChannelNumber(number: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_CHANNEL_NUMBER] = number
        }
    }
    
    /**
     * Update playlist file path.
     */
    suspend fun setPlaylistFilePath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path != null) {
                preferences[Keys.PLAYLIST_FILE_PATH] = path
            } else {
                preferences.remove(Keys.PLAYLIST_FILE_PATH)
            }
        }
    }
    
    /**
     * Update auto-play on launch setting.
     */
    suspend fun setAutoPlayOnLaunch(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUTO_PLAY_ON_LAUNCH] = enabled
        }
    }
    
    /**
     * Clear all preferences.
     */
    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
