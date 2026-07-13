package com.example.atv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.ProxySettings
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.usecase.ImportResult
import com.example.atv.domain.usecase.UnifiedImportChannelsUseCase
import com.example.atv.testing.E2eDatabaseSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MockServerProxyImportSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var sourceSettingsStore: ChannelSourceSettingsStore
    @Inject lateinit var importChannelsUseCase: UnifiedImportChannelsUseCase

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()
        server = MockWebServer().apply { start() }
        runBlocking {
            val seeder = E2eDatabaseSeeder(channelDao)
            seeder.seedEmpty()
            seeder.seedSourceMode(sourceSettingsStore, ChannelSourceMode.HOME_PROXY)
            seeder.seedProxySettings(
                sourceSettingsStore,
                ProxySettings(
                    proxyBaseUrl = server.url("/").toString().removeSuffix("/"),
                    accessToken = "proxy-token",
                ),
            )
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun proxyImportUsesMockServerAndPersistsChannels() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "data": [
                        {
                          "number": 1,
                          "name": "Proxy News",
                          "streamUrl": "http://proxy.local/news.m3u8",
                          "channelCode": "proxy-news"
                        },
                        {
                          "number": 2,
                          "name": "Proxy Sports",
                          "streamUrl": "http://proxy.local/sports.m3u8",
                          "channelCode": "proxy-sports"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = importChannelsUseCase(ChannelSourceMode.HOME_PROXY)

        assertTrue("got $result", result is ImportResult.Success)
        assertEquals(2, (result as ImportResult.Success).importedCount)
        assertEquals(2, channelDao.getChannelCount())

        val request = server.takeRequest()
        assertEquals("/api/v1/channels", request.path)
        assertEquals("Bearer proxy-token", request.getHeader("Authorization"))
    }
}
