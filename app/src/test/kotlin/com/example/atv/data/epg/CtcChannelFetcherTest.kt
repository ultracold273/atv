package com.example.atv.data.epg

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CtcChannelFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var fetcher: CtcChannelFetcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder().build()
        fetcher = CtcChannelFetcher(http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun successLogin(): LoginResult.Success = LoginResult.Success(
        epgLbBase = server.url("/iptvepg/function/").toString(),
        jsessionId = "JS-1",
        config = mapOf("UserToken" to "tok"),
        userToken = "tok",
    )

    private val twoChannelsHtml = """
        <script>
        jsSetConfig('Channel','ChannelID=ch00000000000000000001,ChannelName="CCTV-1",UserChannelID=001,ChannelURL=igmp://239.0.0.1:8000,ChannelSDP="",TimeShift=0,TimeShiftURL="",TimeShiftLength=0,ChannelType=0');
        jsSetConfig('Channel','ChannelID=ch00000000000000000002,ChannelName="CCTV-2",UserChannelID=002,ChannelURL=igmp://239.0.0.2:8000,ChannelSDP="",TimeShift=0,TimeShiftURL="",TimeShiftLength=0,ChannelType=0');
        </script>
    """.trimIndent()

    private val mappingJson = """
        {"channelMixnoMapping":"1:001,2:002","other":"ignored"}
    """.trimIndent()

    @Test
    fun `fetch returns mapped channels on happy path`() = runTest {
        server.enqueue(MockResponse().setBody(twoChannelsHtml))
        server.enqueue(MockResponse().setBody(mappingJson))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isSuccess)
        val channels = result.getOrNull()!!
        assertEquals(2, channels.size)
        assertEquals("ch00000000000000000001", channels[0].channelId)
        assertEquals("CCTV-1", channels[0].channelName)
        assertEquals(1, channels[0].displayNumber)
        assertEquals("CCTV-2", channels[1].channelName)
        assertEquals(2, channels[1].displayNumber)
    }

    @Test
    fun `fetch posts to frameset_builder jsp with required form fields and JSESSIONID`() = runTest {
        server.enqueue(MockResponse().setBody(twoChannelsHtml))
        server.enqueue(MockResponse().setBody(mappingJson))

        fetcher.fetch(successLogin())

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/iptvepg/function/frameset_builder.jsp"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("BUILD_ACTION=FRAMESET_BUILDER"))
        assertTrue(body.contains("MAIN_WIN_SRC="))
        assertTrue(body.contains("NEED_UPDATE_STB=1"))
        assertTrue(req.getHeader("Cookie").orEmpty().contains("JSESSIONID=JS-1"))
    }

    @Test
    fun `fetch returns success with empty list when channels payload has no entries`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>no channels here</body></html>"))
        server.enqueue(MockResponse().setBody("""{"channelMixnoMapping":""}"""))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun `fetch falls back to index+1 numbering when mapping is missing for a channel`() = runTest {
        server.enqueue(MockResponse().setBody(twoChannelsHtml))
        server.enqueue(MockResponse().setBody("""{"channelMixnoMapping":""}"""))

        val result = fetcher.fetch(successLogin())
        val channels = result.getOrNull()!!
        assertEquals(1, channels[0].displayNumber)
        assertEquals(2, channels[1].displayNumber)
    }

    @Test
    fun `fetch returns failure on network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetch returns failure on HTTP 5xx`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = fetcher.fetch(successLogin())
        assertTrue(result.isFailure)
    }
}
