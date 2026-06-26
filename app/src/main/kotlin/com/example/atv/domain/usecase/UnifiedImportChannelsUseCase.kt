package com.example.atv.domain.usecase

import com.example.atv.data.proxy.ProxyChannelClient
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.IptvCredentialsStore
import javax.inject.Inject

class UnifiedImportChannelsUseCase @Inject constructor(
    private val sourceSettingsStore: ChannelSourceSettingsStore,
    private val directImport: ImportCtcChannelsUseCase,
    private val proxyClient: ProxyChannelClient,
    private val channelRepository: ChannelRepository,
    private val credentialsStore: IptvCredentialsStore,
) {
    suspend operator fun invoke(): ImportResult = when (sourceSettingsStore.readMode()) {
        ChannelSourceMode.M3U8 -> ImportResult.LoginFailure("M3U8 imports are manual")
        ChannelSourceMode.DIRECT_CTC -> directImport()
        ChannelSourceMode.HOME_PROXY -> importFromProxy()
    }

    suspend operator fun invoke(mode: ChannelSourceMode): ImportResult = when (mode) {
        ChannelSourceMode.M3U8 -> ImportResult.LoginFailure("M3U8 imports are manual")
        ChannelSourceMode.DIRECT_CTC -> directImport()
        ChannelSourceMode.HOME_PROXY -> importFromProxy()
    }

    suspend fun canBootstrap(): Boolean = when (sourceSettingsStore.readMode()) {
        ChannelSourceMode.M3U8 -> false
        ChannelSourceMode.DIRECT_CTC -> credentialsStore.read()?.isComplete == true
        ChannelSourceMode.HOME_PROXY -> sourceSettingsStore.readProxySettings()?.isComplete == true
    }

    private suspend fun importFromProxy(): ImportResult {
        val settings = sourceSettingsStore.readProxySettings()
        return when {
            settings == null -> ImportResult.LoginFailure("proxy settings missing")
            !settings.isComplete -> ImportResult.LoginFailure("proxy settings incomplete")
            else -> proxyClient.fetchChannels(settings).fold(
                onSuccess = { channels ->
                    if (channels.isEmpty()) {
                        ImportResult.NoChannelsReturned
                    } else {
                        channelRepository.savePlaylistChannels(channels)
                        ImportResult.Success(channels.size)
                    }
                },
                onFailure = { t ->
                    ImportResult.FetchFailure(t.message ?: t::class.simpleName.orEmpty())
                },
            )
        }
    }
}
