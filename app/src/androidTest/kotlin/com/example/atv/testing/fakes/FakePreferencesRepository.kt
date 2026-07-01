package com.example.atv.testing.fakes

import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePreferencesRepository : PreferencesRepository {
    private val preferences = MutableStateFlow(UserPreferences())

    override fun getUserPreferences(): Flow<UserPreferences> = preferences

    override fun getLastChannelNumber(): Flow<Int> = preferences.map { it.lastChannelNumber }

    override fun getPlaylistFilePath(): Flow<String?> = preferences.map { it.playlistFilePath }

    override suspend fun setLastChannelNumber(number: Int) {
        preferences.update { it.copy(lastChannelNumber = number) }
    }

    override suspend fun setPlaylistFilePath(path: String?) {
        preferences.update { it.copy(playlistFilePath = path) }
    }

    override suspend fun setAutoPlayOnLaunch(enabled: Boolean) {
        preferences.update { it.copy(autoPlayOnLaunch = enabled) }
    }

    override suspend fun setEpgEnabled(enabled: Boolean) {
        preferences.update { it.copy(epgEnabled = enabled) }
    }

    override suspend fun setUdpxyProxy(value: String?) {
        preferences.update { it.copy(udpxyProxy = value) }
    }

    override suspend fun clear() {
        preferences.value = UserPreferences()
    }
}
