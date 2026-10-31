package com.example.atv

import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.remotecontrol.ControlListenerStatus
import com.example.atv.data.remotecontrol.RemoteControlManager
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.player.AtvPlayerController
import com.example.atv.testing.E2eDatabaseSeeder
import com.example.atv.testing.E2eFixtures
import com.example.atv.testing.pinnedControlClient
import com.example.atv.testing.fakes.FakeAtvPlayer
import com.example.atv.testing.robots.PlaybackRobot
import com.example.atv.ui.testing.UiTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RemoteControlSmokeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var repository: ChannelRepository
    @Inject lateinit var player: AtvPlayerController
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var control: RemoteControlManager
    private lateinit var playback: PlaybackRobot

    @Before
    fun setup() {
        hiltRule.inject()
        playback = PlaybackRobot(compose)
        runBlocking {
            E2eDatabaseSeeder(channelDao).apply {
                resetPlaybackState(player, preferences)
                seedChannels(E2eFixtures.playbackChannels)
            }
        }
    }

    @Test
    fun realTlsSwitchSharesPlaybackAndForegroundLifecycle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            playback.assertPlaybackVisible().waitForChannelInfoHidden().pressOk().assertNumberPadVisible()
            scenario.onActivity { control.setEnabled(true) }
            awaitStatus(ControlListenerStatus.RUNNING)
            val client = pinnedControlClient(control.state.value.fingerprint)
            try {
                val catalog = request(client, "/api/v1/channels")
                assertEquals(200, catalog.first)
                val version = JSONObject(catalog.second).getString("catalogVersion")
                assertEquals(3, JSONObject(catalog.second).getJSONArray("channels").length())
                assertEquals(202, requestSwitch(client, 2, version).first)
                playback.assertChannelInfoVisible("Sports Two")
                compose.onNodeWithTag(UiTestTags.NumberPadOverlay).assertDoesNotExist()
                assertEquals(2, player.playerState.value.currentChannel?.number)
                assertEquals(2, runBlocking { preferences.getLastChannelNumber().first() })

                scenario.onActivity { (player as FakeAtvPlayer).failPlayback("Fixture stream unavailable") }
                compose.waitUntil(5_000) { player.playerState.value.hasError }
                compose.onNodeWithText("Fixture stream unavailable").assertExists()

                val previous = control.state.value.token
                scenario.onActivity { control.rotateCredential() }
                compose.waitUntil(10_000) {
                    control.state.value.token != previous && control.state.value.status == ControlListenerStatus.RUNNING
                }
                assertEquals(401, request(client, "/api/v1/channels", token = previous).first)
                scenario.moveToState(Lifecycle.State.CREATED)
                awaitStatus(ControlListenerStatus.PAUSED)
                assertThrows(IOException::class.java) { request(client, "/api/v1/channels") }
                // ATV intentionally finishes on user leave; foreground restoration creates a new activity.
                ActivityScenario.launch(MainActivity::class.java).use { reopened ->
                    playback.assertPlaybackVisible()
                    awaitStatus(ControlListenerStatus.RUNNING)
                    assertEquals(202, requestSwitch(client, 3, version).first)
                    playback.assertChannelInfoVisible("Movies Three")
                    reopened.onActivity { control.setEnabled(false) }
                    awaitStatus(ControlListenerStatus.DISABLED)
                    assertThrows(IOException::class.java) { request(client, "/api/v1/channels") }
                }
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun settingsProvisioningKeepsFullScreenSettingsOutOfRemoteDispatch() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            playback.assertPlaybackVisible().waitForChannelInfoHidden().pressMenu().assertSettingsMenuVisible()
            pressTv(compose.onNodeWithTag(UiTestTags.SettingsMenuItemPrefix + "-all-settings"))
            try {
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithTag("remote_control_toggle").fetchSemanticsNodes().isNotEmpty()
                }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                compose.onRoot(useUnmergedTree = true).printToLog("AtvControlUi")
                throw failure
            }
            pressTv(compose.onNodeWithTag("remote_control_toggle").performScrollTo())
            awaitStatus(ControlListenerStatus.RUNNING)
            val client = pinnedControlClient(control.state.value.fingerprint)
            try {
                val version = JSONObject(request(client, "/api/v1/channels").second).getString("catalogVersion")
                assertEquals(503, requestSwitch(client, 2, version).first)
                pressTv(compose.onNodeWithTag("remote_control_connection").performScrollTo())
                compose.onNodeWithTag("remote_control_address").assertIsFocused().performKeyInput {
                    pressKey(Key.DirectionDown)
                }
                compose.onNodeWithTag("remote_control_token").assertIsFocused().performKeyInput {
                    pressKey(Key.DirectionDown)
                }
                compose.onNodeWithTag("remote_control_fingerprint").assertIsFocused().assertIsDisplayed()
                compose.onNodeWithText(control.state.value.fingerprint).assertExists()
                pressTv(compose.onNodeWithText("Close").performScrollTo())
                pressTv(compose.onNodeWithTag("remote_control_toggle").performScrollTo())
                awaitStatus(ControlListenerStatus.DISABLED)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun portConflictIsVisibleAndRetryRecovers() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            playback.assertPlaybackVisible()
            ServerSocket().use { occupied ->
                occupied.bind(InetSocketAddress("127.0.0.1", 8765))
                scenario.onActivity { control.setEnabled(true) }
                awaitStatus(ControlListenerStatus.ERROR)
                playback.assertPlaybackVisible()
            }
            scenario.onActivity { control.retry() }
            awaitStatus(ControlListenerStatus.RUNNING)
            scenario.onActivity { control.setEnabled(false) }
            awaitStatus(ControlListenerStatus.DISABLED)
        }
    }

    @Test
    fun thousandChannelWarmRequestsMeetLatencyTarget() {
        runBlocking {
            repository.savePlaylistChannels((1..1000).map { Channel(it, "Fixture $it", "https://example.invalid/$it") })
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            playback.assertPlaybackVisible()
            scenario.onActivity { control.setEnabled(true) }
            awaitStatus(ControlListenerStatus.RUNNING)
            val client = pinnedControlClient(control.state.value.fingerprint)
            try {
                val initial = JSONObject(request(client, "/api/v1/channels").second)
                assertEquals(1000, initial.getJSONArray("channels").length())
                val version = initial.getString("catalogVersion")
                val catalogTimes = mutableListOf<Long>()
                val switchTimes = mutableListOf<Long>()
                repeat(40) { index ->
                    var start = System.nanoTime()
                    assertEquals(200, request(client, "/api/v1/channels").first)
                    catalogTimes.add((System.nanoTime() - start) / 1_000_000)
                    start = System.nanoTime()
                    assertEquals(202, requestSwitch(client, index + 1, version).first)
                    switchTimes.add((System.nanoTime() - start) / 1_000_000)
                }
                val catalogP95 = catalogTimes.sorted()[37]
                val switchP95 = switchTimes.sorted()[37]
                Log.i("AtvControlBenchmark", "1000 channels: catalog p95=$catalogP95 ms, switch p95=$switchP95 ms")
                assertTrue("Catalog p95=$catalogP95 ms", catalogP95 < 200)
                assertTrue("Switch p95=$switchP95 ms", switchP95 < 200)
                scenario.onActivity { control.setEnabled(false) }
                awaitStatus(ControlListenerStatus.DISABLED)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    private fun awaitStatus(status: ControlListenerStatus) {
        compose.waitUntil(15_000) { control.state.value.status == status }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun pressTv(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun requestSwitch(client: OkHttpClient, number: Int, version: String): Pair<Int, String> =
        request(client, "/api/v1/playback/channel", JSONObject().put("number", number).put("catalogVersion", version).toString())

    private fun request(
        client: OkHttpClient,
        path: String,
        body: String? = null,
        token: String = control.state.value.token,
    ): Pair<Int, String> {
        val request = Request.Builder().url("https://127.0.0.1:8765$path").header("Authorization", "Bearer $token")
        if (body != null) request.post(body.toRequestBody("application/json".toMediaType()))
        return client.newCall(request.build()).execute().use { it.code to it.body!!.string() }
    }
}
