package com.example.atv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.atv.data.local.db.AtvDatabase
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelCatalogRepository
import com.example.atv.domain.repository.ChannelRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChannelCatalogDatabaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var database: AtvDatabase
    @Inject lateinit var repository: ChannelRepository
    @Inject lateinit var catalog: ChannelCatalogRepository

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun failedReplacementRollsBackDeletionAndRetainsCatalogVersion() = runBlocking {
        val original = Channel(17, "original", "https://example.invalid/original")
        repository.savePlaylistChannels(listOf(original))
        val version = catalog.withSnapshot { it.version }
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_fixture BEFORE INSERT ON channels WHEN NEW.number = 999 " +
                "BEGIN SELECT RAISE(ABORT, 'fixture insert rejected'); END"
        )
        val failed = runCatching {
            repository.savePlaylistChannels(listOf(Channel(999, "rejected", "https://example.invalid/rejected")))
        }
        assertTrue(failed.isFailure)
        catalog.withSnapshot {
            assertEquals(version, it.version)
            assertEquals(listOf(original), it.channels)
        }
    }
}
