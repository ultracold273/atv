package com.example.atv.data.remotecontrol

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ChannelCatalogSnapshot
import com.example.atv.domain.repository.ChannelCatalogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

internal class ControlTestCatalog : ChannelCatalogRepository {
    val reads = AtomicInteger()
    var channels = listOf(
        Channel(17, "CCTV-1 综合", "https://example.invalid/private?token=never-return", "央视"),
        Channel(21, "CCTV-1 综合", "https://example.invalid/second", null),
    )
    var entered: CompletableDeferred<Unit>? = null
    var release: CompletableDeferred<Unit>? = null
    val cancelled = CompletableDeferred<Unit>()
    var failure: Exception? = null
    private val lock = Mutex()

    val version: String get() = ChannelCatalogSnapshot.from(channels).version

    override suspend fun <T> withSnapshot(block: suspend (ChannelCatalogSnapshot) -> T): T = lock.withLock {
        reads.incrementAndGet()
        entered?.complete(Unit)
        try {
            release?.await()
        } catch (failure: CancellationException) {
            cancelled.complete(Unit)
            throw failure
        }
        failure?.let { throw it }
        block(ChannelCatalogSnapshot.from(channels))
    }
}
