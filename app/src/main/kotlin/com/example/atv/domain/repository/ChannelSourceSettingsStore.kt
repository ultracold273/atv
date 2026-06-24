package com.example.atv.domain.repository

import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.ProxySettings
import kotlinx.coroutines.flow.Flow

interface ChannelSourceSettingsStore {
    fun observeMode(): Flow<ChannelSourceMode>
    suspend fun readMode(): ChannelSourceMode
    suspend fun saveMode(mode: ChannelSourceMode)

    fun observeProxySettings(): Flow<ProxySettings?>
    suspend fun readProxySettings(): ProxySettings?
    suspend fun saveProxySettings(settings: ProxySettings)
    suspend fun clearProxySettings()
}

