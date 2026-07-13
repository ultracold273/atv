package com.example.atv

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.LoadPlaylistUseCase
import com.example.atv.testing.E2eDatabaseSeeder
import com.example.atv.testing.E2eFixtures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MockServerPlaylistImportSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var loadPlaylistUseCase: LoadPlaylistUseCase
    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var sourceSettingsStore: ChannelSourceSettingsStore

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()
        server = MockWebServer().apply { start() }
        runBlocking {
            val seeder = E2eDatabaseSeeder(channelDao)
            seeder.seedEmpty()
            seeder.seedSourceMode(sourceSettingsStore, ChannelSourceMode.M3U8)
            preferencesRepository.clear()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun m3u8PlaylistImportUsesMockServerAndPersistsChannels() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(E2eFixtures.mockPlaylistContent),
        )

        val playlistUrl = server.url("/playlist.m3u8").toString()
        val channels = loadPlaylistUseCase(playlistUrl.toUri()).getOrThrow()

        assertEquals(listOf("Mock News", "Mock Sports"), channels.map { it.name })
        assertEquals(2, channelDao.getChannelCount())
        assertEquals(playlistUrl, preferencesRepository.getPlaylistFilePath().first())
        assertEquals("/playlist.m3u8", server.takeRequest().path)
    }
}
