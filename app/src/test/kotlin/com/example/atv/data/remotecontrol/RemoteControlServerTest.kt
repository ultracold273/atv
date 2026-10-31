package com.example.atv.data.remotecontrol

import com.example.atv.domain.model.Channel
import com.example.atv.player.ChannelSwitchCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlServerTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val catalog = ControlTestCatalog()
    private val access = RemoteControlAccess()
    private val switches = ChannelSwitchCoordinator(catalog)
    private val api = RemoteControlApi(catalog, switches, access)
    private val played = CopyOnWriteArrayList<Channel>()
    private lateinit var binding: AutoCloseable
    private lateinit var server: RemoteControlServer
    private lateinit var client: OkHttpClient
    private lateinit var tls: HandshakeCertificates
    private var port = 0

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(main)
        access.configure(true, TOKEN)
        access.setForeground(true)
        binding = runBlocking(main) { switches.attach { channel, _ -> played.add(channel) } }
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .callTimeout(5, TimeUnit.SECONDS).build()
        server = RemoteControlServer(api, tls.sslContext(), InetSocketAddress("127.0.0.1", 0))
        port = server.start()
    }

    @AfterEach
    fun tearDown() {
        catalog.release?.complete(Unit)
        runBlocking(main) { binding.close() }
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        Dispatchers.resetMain()
        main.close()
    }

    private fun call(
        path: String = "/api/v1/channels",
        body: String? = null,
        token: String? = TOKEN,
        method: String = if (body == null) "GET" else "POST",
        contentType: String = "application/json",
    ): Pair<Int, String> {
        val request = Request.Builder().url("https://localhost:$port$path")
        token?.let { request.header("Authorization", "Bearer $it") }
        request.method(method, body?.toRequestBody(contentType.toMediaType()))
        return client.newCall(request.build()).execute().use {
            assertEquals("no-store", it.header("Cache-Control"))
            it.code to it.body!!.string()
        }
    }

    private fun body(number: Int = 17, version: String = catalog.version): String =
        """{"number":$number,"catalogVersion":"$version"}"""

    @Test
    fun completeCatalogContainsSelectionMetadataOnly() {
        val (status, body) = call()
        assertEquals(200, status)
        val result = Json.parseToJsonElement(body).jsonObject
        assertEquals(catalog.version, result.getValue("catalogVersion").jsonPrimitive.content)
        val channels = result.getValue("channels").jsonArray
        assertEquals(listOf("17", "21"), channels.map { it.jsonObject.getValue("number").jsonPrimitive.content })
        channels.forEach { assertEquals(setOf("number", "name", "groupTitle"), it.jsonObject.keys) }
        assertFalse(body.contains("never-return"))
        assertFalse(body.contains("streamUrl"))
    }

    @Test
    fun emptyCatalogStillHasAUsableVersion() {
        catalog.channels = emptyList()
        val result = Json.parseToJsonElement(call().second).jsonObject
        assertTrue(result.getValue("channels").jsonArray.isEmpty())
        assertEquals(catalog.version, result.getValue("catalogVersion").jsonPrimitive.content)
    }

    @Test
    fun switchAcknowledgesDispatchWithoutWaitingForPlayback() {
        val (status, response) = call("/api/v1/playback/channel", body())
        assertEquals(202, status)
        assertEquals(listOf(17), played.map { it.number })
        val acknowledgement = Json.parseToJsonElement(response).jsonObject
        assertEquals(setOf("status", "number", "catalogVersion"), acknowledgement.keys)
        assertEquals("accepted", acknowledgement.getValue("status").jsonPrimitive.content)
        assertFalse(response.contains("playing"))
    }

    @Test
    fun unauthorizedRequestsNeverReadTheCatalogOrSwitch() {
        assertEquals(401, call(token = null).first)
        assertEquals(401, call(token = "wrong-token").first)
        assertEquals(401, call("/api/v1/playback/channel", body(), "wrong-token").first)
        assertEquals(0, catalog.reads.get())
        assertTrue(played.isEmpty())
    }

    @Test
    fun validatesCatalogVersionBeforeChannelLookup() {
        val oldVersion = catalog.version
        catalog.channels = catalog.channels.map { it.copy(streamUrl = "replacement") }
        val stale = call("/api/v1/playback/channel", body(999, oldVersion))
        assertEquals(409, stale.first)
        assertTrue(stale.second.contains("CATALOG_CHANGED"))
        val missing = call("/api/v1/playback/channel", body(999))
        assertEquals(404, missing.first)
        assertTrue(missing.second.contains("CHANNEL_NOT_FOUND"))
        assertTrue(played.isEmpty())
    }

    @Test
    fun rejectsMalformedSelectionsAndUnsupportedSearch() {
        val invalid = listOf(
            "{}", "[]", "not json", """{"number":17}""",
            """{"number":"17","catalogVersion":"v"}""",
            """{"number":1.0,"catalogVersion":"v"}""",
            """{"number":0,"catalogVersion":"v"}""",
            """{"number":-1,"catalogVersion":"v"}""",
            """{"number":2147483648,"catalogVersion":"v"}""",
            """{"number":true,"catalogVersion":"v"}""",
            """{"number":17,"catalogVersion":null}""",
            """{"number":17,"catalogVersion":" "}""",
            """{"number":17,"catalogVersion":"v","name":"CCTV 1"}""",
            body(version = "v".repeat(129)),
        )
        invalid.forEach { assertEquals(400, call("/api/v1/playback/channel", it).first, it) }
        assertEquals(400, call("/api/v1/channels?query=cctv1").first)
        assertTrue(played.isEmpty())
    }

    @Test
    fun exposesOnlyTheTwoSpecifiedOperations() {
        assertEquals(404, call("/api/v1/playback").first)
        assertEquals(404, call("/health").first)
        assertEquals(405, call("/api/v1/channels", "{}", method = "POST").first)
        assertEquals(405, call("/api/v1/playback/channel").first)
        assertEquals(415, call("/api/v1/playback/channel", body(), contentType = "text/plain").first)
        assertEquals(413, call("/api/v1/playback/channel", "x".repeat(4097)).first)
    }

    @Test
    fun unavailablePlaybackDoesNotQueueARequest() {
        runBlocking(main) { binding.close() }
        assertEquals(503, call("/api/v1/playback/channel", body()).first)
        runBlocking(main) { binding = switches.attach { channel, _ -> played.add(channel) } }
        assertTrue(played.isEmpty())
        assertEquals(202, call("/api/v1/playback/channel", body()).first)
    }

    @Test
    fun credentialRotationRevokesAnAlreadyAuthenticatedRequest() = runBlocking {
        catalog.entered = CompletableDeferred()
        catalog.release = CompletableDeferred()
        val response = async(Dispatchers.IO) { call("/api/v1/playback/channel", body()) }
        withTimeout(2000) { catalog.entered!!.await() }
        access.configure(true, "replacement-test-token")
        catalog.release!!.complete(Unit)
        assertEquals(401, response.await().first)
        assertTrue(played.isEmpty())
        assertEquals(401, call(token = TOKEN).first)
    }

    @Test
    fun backgroundAndResumeInvalidatesOldWork() = runBlocking {
        catalog.entered = CompletableDeferred()
        catalog.release = CompletableDeferred()
        val response = async(Dispatchers.IO) { call("/api/v1/playback/channel", body()) }
        withTimeout(2000) { catalog.entered!!.await() }
        access.setForeground(false)
        access.setForeground(true)
        catalog.release!!.complete(Unit)
        assertEquals(503, response.await().first)
        assertTrue(played.isEmpty())
    }

    @Test
    fun concurrentRemoteSwitchesAreBounded() = runBlocking {
        catalog.entered = CompletableDeferred()
        catalog.release = CompletableDeferred()
        val first = async(Dispatchers.IO) { call("/api/v1/playback/channel", body()) }
        withTimeout(2000) { catalog.entered!!.await() }
        val busy = call("/api/v1/playback/channel", body(21))
        assertEquals(503, busy.first)
        assertTrue(busy.second.contains("CONTROL_BUSY"))
        catalog.release!!.complete(Unit)
        assertEquals(202, first.await().first)
        assertEquals(listOf(17), played.map { it.number })
    }

    @Test
    fun unexpectedFailuresAreSanitized() {
        catalog.failure = IOException("secret provider URL must not escape")
        val response = call()
        assertEquals(500, response.first)
        assertFalse(response.second.contains("secret"))
    }

    @Test
    fun disconnectedClientsDoNotLeaveADelayedSwitch() = runBlocking {
        catalog.entered = CompletableDeferred()
        catalog.release = CompletableDeferred()
        val request = Request.Builder().url("https://localhost:$port/api/v1/playback/channel")
            .header("Authorization", "Bearer $TOKEN")
            .post(body().toRequestBody("application/json".toMediaType())).build()
        val pending = client.newCall(request)
        val response = async(Dispatchers.IO) { runCatching { pending.execute().close() } }
        withTimeout(2000) { catalog.entered!!.await() }
        pending.cancel()
        assertTrue(response.await().isFailure)
        withTimeout(2000) { catalog.cancelled.await() }
        catalog.release!!.complete(Unit)
        assertTrue(played.isEmpty())
        assertEquals(202, call("/api/v1/playback/channel", body()).first)
    }

    @Test
    fun dispatchDeadlineCancelsPendingWorkAndReleasesTheSwitchGuard() = runBlocking {
        catalog.entered = CompletableDeferred()
        catalog.release = CompletableDeferred()
        val expired = call("/api/v1/playback/channel", body())
        assertEquals(503, expired.first)
        assertTrue(expired.second.contains("CONTROL_BUSY"))
        catalog.release!!.complete(Unit)
        assertTrue(played.isEmpty())
        assertEquals(202, call("/api/v1/playback/channel", body()).first)
    }

    @Test
    fun untrustedTlsIdentityIsRejectedBeforeSendingCredentials() {
        val untrusted = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        try {
            val request = Request.Builder().url("https://localhost:$port/api/v1/channels")
                .header("Authorization", "Bearer $TOKEN").build()
            assertThrows(IOException::class.java) { untrusted.newCall(request).execute().close() }
            assertEquals(0, catalog.reads.get())
        } finally {
            untrusted.connectionPool.evictAll()
            untrusted.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun bindFailureReleasesResourcesWithoutStoppingTheOriginalListener() {
        val conflicting = RemoteControlServer(api, tls.sslContext(), InetSocketAddress("127.0.0.1", port))
        assertThrows(Exception::class.java) { conflicting.start() }
        assertEquals(200, call().first)
    }

    @Test
    fun closingListenerClosesItsPortAndConnections() {
        assertEquals(200, call().first)
        server.close()
        assertThrows(IOException::class.java) { call() }
    }

    private companion object {
        const val TOKEN = "fixture-token-not-a-production-credential"
    }
}
