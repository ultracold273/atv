package com.example.atv.data.epg

import com.example.atv.data.proxy.ProxyEpgProvider
import com.example.atv.di.ApplicationScope
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.Program
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.EpgProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModeAwareEpgProvider @Inject constructor(
    private val sourceSettingsStore: ChannelSourceSettingsStore,
    private val ctcEpgProvider: CtcEpgProvider,
    private val proxyEpgProvider: ProxyEpgProvider,
    @ApplicationScope applicationScope: CoroutineScope,
) : EpgProvider {

    override val isConfigured: StateFlow<Boolean> = combine(
        sourceSettingsStore.observeMode(),
        sourceSettingsStore.observeProxySettings(),
        ctcEpgProvider.isConfigured,
    ) { mode, proxySettings, directConfigured ->
        when (mode) {
            ChannelSourceMode.DIRECT_CTC -> directConfigured
            ChannelSourceMode.HOME_PROXY -> proxySettings?.isComplete == true
            ChannelSourceMode.M3U8 -> false
        }
    }.stateIn(applicationScope, SharingStarted.Eagerly, false)

    override suspend fun fetchPrograms(
        channelCode: String,
        dateOffset: Int,
    ): Result<List<Program>> = when (sourceSettingsStore.readMode()) {
        ChannelSourceMode.DIRECT_CTC -> ctcEpgProvider.fetchPrograms(channelCode, dateOffset)
        ChannelSourceMode.HOME_PROXY -> proxyEpgProvider.fetchPrograms(channelCode, dateOffset)
        ChannelSourceMode.M3U8 -> Result.failure(IllegalStateException("EPG not configured for M3U8 mode"))
    }
}
