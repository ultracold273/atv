package com.example.atv.data.proxy

import com.example.atv.domain.model.Program
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyEpgProvider @Inject constructor(
    private val proxyClient: ProxyChannelClient,
    private val sourceSettingsStore: ChannelSourceSettingsStore,
) {
    suspend fun fetchPrograms(
        channelCode: String,
        dateOffset: Int,
    ): Result<List<Program>> {
        val settings = sourceSettingsStore.readProxySettings()
            ?: return Result.failure(IllegalStateException("proxy settings missing"))
        if (!settings.isComplete) {
            return Result.failure(IllegalStateException("proxy settings incomplete"))
        }
        return proxyClient.fetchPrograms(settings, channelCode, dateOffset)
    }
}
