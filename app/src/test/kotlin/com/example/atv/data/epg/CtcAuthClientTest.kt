package com.example.atv.data.epg

import com.example.atv.EpgFixtures
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CtcAuthClientTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var device: DeviceProfile

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder().build()
        device = DeviceProfile(
            userId = EpgFixtures.USER_ID,
            password = EpgFixtures.PASSWORD,
            stbId = EpgFixtures.STB_ID,
            ip = EpgFixtures.IP,
            mac = EpgFixtures.MAC,
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun authBase(): String = server.url("/").toString().removeSuffix("/")

    /** Enqueue a complete happy-path login transcript matching python's 6 steps. */
    private fun enqueueHappyPath() {
        // Step 1: GET /auth?UserID=...&Action=Login
        server.enqueue(
            MockResponse().setBody(
                "<script>Authentication.CTCGetAuthInfo('${EpgFixtures.ENCRY_TOKEN}');</script>"
            )
        )
        // Step 2-3: POST /uploadAuthInfo
        server.enqueue(
            MockResponse().setBody(
                """
                Authentication.CTCSetConfig('UserToken','tok-XYZ');
                Authentication.CTCSetConfig('EPGURL','http://does.not/');
                """.trimIndent()
            )
        )
        // Step 4: GET /getServiceList — redirect via document.location
        val lbUrl = server.url("/iptvepg/lb").toString()
        server.enqueue(MockResponse().setBody("document.location='$lbUrl';"))
        // Step 5a: lb hop — second redirect
        val nodeUrl = server.url("/iptvepg/function/index.jsp").toString()
        server.enqueue(MockResponse().setBody("document.location='$nodeUrl';"))
        // Step 5b: actual node — sets JSESSIONID and serves portal HTML
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "JSESSIONID=ABC123XYZ; Path=/iptvepg")
                .setBody(
                    """
                    <html><body>
                    <input type="hidden" name="UserID" value="${EpgFixtures.USER_ID}"/>
                    <input type="hidden" name="STBID" value="${EpgFixtures.STB_ID}"/>
                    </body></html>
                    """.trimIndent()
                )
        )
        // Step 6: POST /iptvepg/function/funcportalauth.jsp
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
    }

    @Test
    fun `login happy path returns Success with epgLbBase, jsessionId, config, userToken`() = runTest {
        enqueueHappyPath()
        val client = CtcAuthClient(http, authBase(), device)
        val result = client.login()
        assertTrue(result is LoginResult.Success, "got $result")
        result as LoginResult.Success
        assertEquals("tok-XYZ", result.userToken)
        assertEquals("ABC123XYZ", result.jsessionId)
        assertTrue(
            result.epgLbBase.endsWith("/iptvepg/function/"),
            "epgLbBase=${result.epgLbBase}",
        )
        assertEquals("tok-XYZ", result.config["UserToken"])
    }

    @Test
    fun `login step 1 sends UserID and Action=Login`() = runTest {
        enqueueHappyPath()
        CtcAuthClient(http, authBase(), device).login()
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.contains("UserID=${EpgFixtures.USER_ID}"))
        assertTrue(req.path!!.contains("Action=Login"))
    }

    @Test
    fun `login step 2 posts Authenticator UserID AccessMethod AccessUserName`() = runTest {
        enqueueHappyPath()
        CtcAuthClient(http, authBase(), device).login()
        server.takeRequest() // step 1
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/uploadAuthInfo", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("UserID=${EpgFixtures.USER_ID}"))
        assertTrue(body.contains("Authenticator="))
        assertTrue(body.contains("AccessMethod=dhcp"))
        assertTrue(body.contains("AccessUserName=${EpgFixtures.USER_ID}"))
    }

    @Test
    fun `login step 4 sends UserToken cookie`() = runTest {
        enqueueHappyPath()
        CtcAuthClient(http, authBase(), device).login()
        server.takeRequest() // step 1
        server.takeRequest() // step 2
        val req = server.takeRequest()
        assertEquals("/getServiceList", req.path)
        assertTrue(req.getHeader("Cookie").orEmpty().contains("UserToken=tok-XYZ"))
    }

    @Test
    fun `login step 6 posts hidden inputs to funcportalauth jsp with JSESSIONID`() = runTest {
        enqueueHappyPath()
        CtcAuthClient(http, authBase(), device).login()
        repeat(5) { server.takeRequest() }
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/iptvepg/function/funcportalauth.jsp"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("UserID=${EpgFixtures.USER_ID}"))
        assertTrue(body.contains("STBID=${EpgFixtures.STB_ID}"))
        assertTrue(req.getHeader("Cookie").orEmpty().contains("JSESSIONID=ABC123XYZ"))
    }

    @Test
    fun `login fails with NoEncryToken when login page is malformed`() = runTest {
        server.enqueue(MockResponse().setBody("<html>broken</html>"))
        val result = CtcAuthClient(http, authBase(), device).login()
        assertTrue(result is LoginResult.Failure)
        assertTrue((result as LoginResult.Failure).reason.contains("EncryToken"))
    }

    @Test
    fun `login fails with NoUserToken when uploadAuthInfo lacks it`() = runTest {
        server.enqueue(
            MockResponse().setBody("<script>Authentication.CTCGetAuthInfo('${EpgFixtures.ENCRY_TOKEN}');</script>")
        )
        server.enqueue(
            MockResponse().setBody("Authentication.CTCSetConfig('Other','1');")
        )
        val result = CtcAuthClient(http, authBase(), device).login()
        assertTrue(result is LoginResult.Failure)
        assertTrue((result as LoginResult.Failure).reason.contains("UserToken"))
    }

    @Test
    fun `login fails with NoJsessionId when no Set-Cookie`() = runTest {
        server.enqueue(
            MockResponse().setBody("<script>Authentication.CTCGetAuthInfo('${EpgFixtures.ENCRY_TOKEN}');</script>")
        )
        server.enqueue(MockResponse().setBody("Authentication.CTCSetConfig('UserToken','tok');"))
        val lbUrl = server.url("/iptvepg/lb").toString()
        server.enqueue(MockResponse().setBody("document.location='$lbUrl';"))
        val nodeUrl = server.url("/iptvepg/function/index.jsp").toString()
        server.enqueue(MockResponse().setBody("document.location='$nodeUrl';"))
        // No Set-Cookie header here.
        server.enqueue(MockResponse().setBody("<html/>"))
        val result = CtcAuthClient(http, authBase(), device).login()
        assertTrue(result is LoginResult.Failure)
        assertTrue((result as LoginResult.Failure).reason.contains("JSESSIONID"))
    }

    @Test
    fun `login returns Failure rather than throwing on network error`() = runTest {
        server.shutdown() // force connection refusal
        val result = CtcAuthClient(http, authBase(), device).login()
        assertTrue(result is LoginResult.Failure, "got $result")
        assertNotNull((result as LoginResult.Failure).reason)
    }
}
