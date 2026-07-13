package com.example.atv.testing.fakes

import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.ProxySettings
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeChannelSourceSettingsStore : ChannelSourceSettingsStore {
    private val mode = MutableStateFlow(ChannelSourceMode.DIRECT_CTC)
    private val proxySettings = MutableStateFlow<ProxySettings?>(null)

    override fun observeMode(): Flow<ChannelSourceMode> = mode

    override suspend fun readMode(): ChannelSourceMode = mode.value

    override suspend fun saveMode(mode: ChannelSourceMode) {
        this.mode.value = mode
    }

    override fun observeProxySettings(): Flow<ProxySettings?> = proxySettings

    override suspend fun readProxySettings(): ProxySettings? = proxySettings.value

    override suspend fun saveProxySettings(settings: ProxySettings) {
        proxySettings.value = settings
    }

    override suspend fun clearProxySettings() {
        proxySettings.value = null
    }
}
