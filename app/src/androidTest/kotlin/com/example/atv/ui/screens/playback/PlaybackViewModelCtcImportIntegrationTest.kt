package com.example.atv.ui.screens.playback

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.atv.data.epg.CtcAuthClient
import com.example.atv.data.epg.CtcChannelFetcher
import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.local.db.ALL_MIGRATIONS
import com.example.atv.data.local.db.AtvDatabase
import com.example.atv.data.local.secure.IptvCredentialsStoreImpl
import com.example.atv.data.repository.ChannelRepositoryImpl
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class PlaybackViewModelCtcImportIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer
    private lateinit var db: AtvDatabase
    private lateinit var store: IptvCredentialsStoreImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(context, AtvDatabase::class.java)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        store = IptvCredentialsStoreImpl(context, prefsName = "iptv_creds_integration_test")
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
        db.close()
        server.shutdown()
    }

    @Test
    fun import_endToEnd_savesChannelsAndFlipsIsConfigured() = runBlocking {
        // 1. Seed credentials pointing at MockWebServer.
        val creds = IptvCredentials(
            userId = "1234567890123",
            password = "000000",
            stbId = "0".repeat(32),
            ip = "192.0.2.1",
            mac = "00:00:5E:00:53:01",
            authServerUrl = server.url("/").toString().removeSuffix("/"),
        )
        store.save(creds)

        // 2. Enqueue the 6-step login transcript + frameset_builder + mapping.
        enqueueHappyLoginAnd2ChannelImport()

        // 3. Construct collaborators directly (skipping Hilt for the integration test).
        val http = OkHttpClient.Builder().build()
        val authClient = CtcAuthClient(http)
        val fetcher = CtcChannelFetcher(http)
        val repo = ChannelRepositoryImpl(db.channelDao())
        val epgProvider = CtcEpgProvider(authClient, http, store, Clock.systemUTC())
        val useCase = ImportCtcChannelsUseCase(authClient, fetcher, repo, store, epgProvider)

        // 4. Run.
        val result = useCase()

        // 5. Assert.
        assertTrue("got $result", result is ImportResult.Success)
        assertEquals(2, (result as ImportResult.Success).importedCount)
        val channels = repo.getAllChannels().first()
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("ch00000000000000000001", channels[0].channelCode)
        assertTrue(epgProvider.isConfigured.value)
    }

    private fun enqueueHappyLoginAnd2ChannelImport() {
        // Step 1: GET /auth — encryToken
        server.enqueue(
            MockResponse().setBody(
                "<script>Authentication.CTCGetAuthInfo('abcdef0123456789');</script>"
            )
        )
        // Step 2: POST /uploadAuthInfo — UserToken
        server.enqueue(
            MockResponse().setBody(
                "Authentication.CTCSetConfig('UserToken','tok-XYZ');"
            )
        )
        // Step 3: GET /getServiceList — document.location redirect
        val lbUrl = server.url("/iptvepg/lb").toString()
        server.enqueue(MockResponse().setBody("document.location='$lbUrl';"))
        // Step 4: lb hop
        val nodeUrl = server.url("/iptvepg/function/index.jsp").toString()
        server.enqueue(MockResponse().setBody("document.location='$nodeUrl';"))
        // Step 5: node — JSESSIONID + portal HTML
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "JSESSIONID=ABC; Path=/iptvepg")
                .setBody("<html><body><input type='hidden' name='UserID' value='1234567890123'/></body></html>")
        )
        // Step 6: portal auth
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        // Channel fetch: frameset_builder.jsp
        server.enqueue(
            MockResponse().setBody(
                """
                <script>
                jsSetConfig('Channel','ChannelID=ch00000000000000000001,ChannelName="CCTV-1",UserChannelID=001,ChannelURL=igmp://239.0.0.1:8000');
                jsSetConfig('Channel','ChannelID=ch00000000000000000002,ChannelName="CCTV-2",UserChannelID=002,ChannelURL=igmp://239.0.0.2:8000');
                </script>
                """.trimIndent()
            )
        )
        // Mapping
        server.enqueue(MockResponse().setBody("""{"channelMixnoMapping":"1:001,2:002"}"""))
    }
}
