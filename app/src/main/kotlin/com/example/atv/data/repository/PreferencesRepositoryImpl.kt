package com.example.atv.data.repository

import com.example.atv.data.local.datastore.UserPreferencesDataStore
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PreferencesRepository using DataStore.
 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) : PreferencesRepository {
    
    override fun getUserPreferences(): Flow<UserPreferences> {
        return dataStore.userPreferences
    }
    
    override fun getLastChannelNumber(): Flow<Int> {
        return dataStore.lastChannelNumber
    }
    
    override fun getPlaylistFilePath(): Flow<String?> {
        return dataStore.playlistFilePath
    }
    
    override suspend fun setLastChannelNumber(number: Int) {
        dataStore.setLastChannelNumber(number)
    }
    
    override suspend fun setPlaylistFilePath(path: String?) {
        dataStore.setPlaylistFilePath(path)
    }
    
    override suspend fun setAutoPlayOnLaunch(enabled: Boolean) {
        dataStore.setAutoPlayOnLaunch(enabled)
    }
    
    override suspend fun clear() {
        dataStore.clear()
    }
}
