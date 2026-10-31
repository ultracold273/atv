package com.example.atv.player

import androidx.annotation.MainThread
import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ChannelControlFailure
import com.example.atv.domain.model.ChannelSwitchResult
import com.example.atv.domain.repository.ChannelCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Hands a validated selection to the active playback route, on the same thread as local controls. */
@Singleton
class ChannelSwitchCoordinator @Inject constructor(
    private val catalog: ChannelCatalogRepository,
) {
    private class Binding(val play: (Channel, List<Channel>) -> Unit)

    private var binding: Binding? = null
    private val requests = Mutex()

    @MainThread
    fun attach(play: (Channel, List<Channel>) -> Unit): AutoCloseable {
        val attached = Binding(play)
        binding = attached
        return AutoCloseable {
            if (binding === attached) binding = null
        }
    }

    suspend fun request(
        number: Int,
        version: String,
        checkAccess: () -> ChannelControlFailure?,
    ): ChannelSwitchResult {
        if (!requests.tryLock()) return ChannelSwitchResult.Rejected(ChannelControlFailure.CONTROL_BUSY)
        return try {
            catalog.withSnapshot { snapshot ->
                when {
                    version != snapshot.version -> ChannelSwitchResult.Rejected(ChannelControlFailure.CATALOG_CHANGED)
                    else -> {
                        val channel = snapshot.channels.firstOrNull { it.number == number }
                        if (channel == null) {
                            ChannelSwitchResult.Rejected(ChannelControlFailure.CHANNEL_NOT_FOUND)
                        } else {
                            dispatch(channel, snapshot.channels, version, checkAccess)
                        }
                    }
                }
            }
        } finally {
            requests.unlock()
        }
    }

    private suspend fun dispatch(
        channel: Channel,
        channels: List<Channel>,
        version: String,
        checkAccess: () -> ChannelControlFailure?,
    ): ChannelSwitchResult = withContext(Dispatchers.Main.immediate) {
        ensureActive()
        val denied = checkAccess()
        val active = binding
        when {
            denied != null -> ChannelSwitchResult.Rejected(denied)
            active == null -> ChannelSwitchResult.Rejected(ChannelControlFailure.PLAYBACK_UNAVAILABLE)
            else -> {
                active.play(channel, channels)
                ChannelSwitchResult.Accepted(channel.number, version)
            }
        }
    }
}
