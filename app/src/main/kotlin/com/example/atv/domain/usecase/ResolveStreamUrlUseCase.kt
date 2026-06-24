package com.example.atv.domain.usecase

import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.util.UdpxyUrlRewriter
import javax.inject.Inject

class ResolveStreamUrlUseCase @Inject constructor() {
    operator fun invoke(
        streamUrl: String,
        sourceMode: ChannelSourceMode,
        udpxyProxy: String?,
    ): String = when (sourceMode) {
        ChannelSourceMode.HOME_PROXY -> streamUrl
        ChannelSourceMode.M3U8,
        ChannelSourceMode.DIRECT_CTC -> UdpxyUrlRewriter.rewrite(streamUrl, udpxyProxy)
    }
}

