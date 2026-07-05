package com.example.atv.testing

import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.local.db.toEntity
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.model.ProxySettings
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.player.AtvPlayerController

class E2eDatabaseSeeder(
    private val channelDao: ChannelDao,
) {
    suspend fun seedEmpty() {
        channelDao.deleteAll()
    }

    suspend fun seedChannels(channels: List<Channel>) {
        channelDao.deleteAll()
        channelDao.insertAll(channels.map { it.toEntity() })
    }

    suspend fun resetPlaybackState(
        player: AtvPlayerController,
        preferencesRepository: PreferencesRepository,
    ) {
        player.stop()
        preferencesRepository.clear()
    }

    suspend fun seedSourceMode(
        sourceSettingsStore: ChannelSourceSettingsStore,
        mode: ChannelSourceMode,
    ) {
        sourceSettingsStore.saveMode(mode)
    }

    suspend fun seedCredentials(
        credentialsStore: IptvCredentialsStore,
        credentials: IptvCredentials,
    ) {
        credentialsStore.save(credentials)
    }

    suspend fun seedProxySettings(
        sourceSettingsStore: ChannelSourceSettingsStore,
        settings: ProxySettings,
    ) {
        sourceSettingsStore.saveProxySettings(settings)
    }
}
