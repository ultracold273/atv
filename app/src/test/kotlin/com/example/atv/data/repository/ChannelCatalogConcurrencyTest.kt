package com.example.atv.data.repository

import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.local.db.ChannelEntity
import com.example.atv.data.local.db.toDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelCatalogConcurrencyTest {
    @Test
    fun importCannotReassignAValidatedNumberBeforeDispatch() = runTest {
        val dao = mockk<ChannelDao>()
        val original = ChannelEntity(17, "CCTV-1", "old-stream", null, null)
        var stored = listOf(original)
        coEvery { dao.getSnapshot() } answers { stored }
        coEvery { dao.replacePlaylistChannels(any()) } answers { stored = firstArg() }
        val repository = ChannelRepositoryImpl(dao)
        val releaseDispatch = CompletableDeferred<Unit>()
        val initialVersion = repository.withSnapshot { it.version }
        val selection = async {
            repository.withSnapshot { snapshot ->
                releaseDispatch.await()
                snapshot.channels.single().streamUrl
            }
        }
        runCurrent()
        val imported = original.copy(streamUrl = "new-stream")
        val import = async { repository.savePlaylistChannels(listOf(imported.toDomain())) }
        runCurrent()
        assertFalse(import.isCompleted)
        coVerify(exactly = 0) { dao.replacePlaylistChannels(any()) }
        releaseDispatch.complete(Unit)
        assertEquals("old-stream", selection.await())
        import.await()
        assertNotEquals(initialVersion, repository.withSnapshot { it.version })
    }

    @Test
    fun snapshotWaitsUntilReplacementFinishes() = runTest {
        val dao = mockk<ChannelDao>()
        val committed = CompletableDeferred<Unit>()
        val record = ChannelEntity(21, "replacement", "stream", null, null)
        coEvery { dao.replacePlaylistChannels(any()) } coAnswers { committed.await() }
        coEvery { dao.getSnapshot() } returns listOf(record)
        val repository = ChannelRepositoryImpl(dao)
        val import = async { repository.savePlaylistChannels(listOf(record.toDomain())) }
        runCurrent()
        val reader = async { repository.withSnapshot { it.channels } }
        runCurrent()
        assertFalse(reader.isCompleted)
        coVerify(exactly = 0) { dao.getSnapshot() }
        committed.complete(Unit)
        import.await()
        assertEquals(listOf(record.toDomain()), reader.await())
    }
}
