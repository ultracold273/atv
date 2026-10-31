package com.example.atv.data.remotecontrol

import com.example.atv.domain.model.RemoteControlSettings
import com.example.atv.domain.repository.RemoteControlSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlManagerTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val access = RemoteControlAccess()
    private val store = MemorySettings()
    private val networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val listeners = CopyOnWriteArrayList<TestListener>()
    private var failStart = false
    private var startGate: CountDownLatch? = null
    private lateinit var manager: RemoteControlManager

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(main)
        manager = runBlocking(main) {
            RemoteControlManager(
                store,
                ControlTlsIdentityProvider { ControlTlsIdentity(SSLContext.getDefault(), "fixture-fingerprint") },
                ControlListenerFactory {
                    TestListener(failStart, startGate).also { listeners.add(it) }
                },
                access,
                object : ControlNetworkMonitor {
                    override fun changes() = networkChanges
                    override fun endpoints() = listOf("https://127.0.0.1:8765")
                },
            )
        }
    }

    @AfterEach
    fun tearDown() {
        startGate?.countDown()
        runBlocking(main) { manager.shutdown() }
        Dispatchers.resetMain()
        main.close()
    }

    private suspend fun awaitState(predicate: (RemoteControlState) -> Boolean): RemoteControlState =
        withTimeout(5000) { manager.state.first(predicate) }

    @Test
    fun optInIsPersistedAndListenerFollowsForegroundLifecycle() = runBlocking(main) {
        awaitState { it.token.isNotEmpty() }
        assertFalse(manager.state.value.enabled)
        manager.setForeground(true)
        manager.setEnabled(true)
        awaitState { it.status == ControlListenerStatus.RUNNING }
        assertTrue(store.value.enabled)
        assertEquals(1, listeners.size)
        val permit = access.authenticate("Bearer " + store.value.token)!!
        assertNull(access.check(permit))
        manager.setForeground(false)
        assertNotNull(access.check(permit))
        awaitState { it.status == ControlListenerStatus.PAUSED }
        assertTrue(listeners.first().closed)
        manager.setForeground(true)
        awaitState { it.status == ControlListenerStatus.RUNNING }
        assertEquals(2, listeners.size)
        manager.setEnabled(false)
        awaitState { it.status == ControlListenerStatus.DISABLED }
        assertFalse(store.value.enabled)
        assertTrue(listeners.last().closed)
    }

    @Test
    fun rotationImmediatelyRevokesAccessAndPersistsTheNewToken() = runBlocking(main) {
        manager.setForeground(true)
        manager.setEnabled(true)
        val initial = awaitState { it.status == ControlListenerStatus.RUNNING }
        manager.rotateCredential()
        assertNull(access.authenticate("Bearer " + initial.token))
        val rotated = awaitState { it.status == ControlListenerStatus.RUNNING && it.token != initial.token }
        assertEquals(rotated.token, store.value.token)
        assertNull(access.authenticate("Bearer " + initial.token))
        assertNotNull(access.authenticate("Bearer " + rotated.token))
        assertFalse(rotated.toString().contains(rotated.token))
        assertFalse(listeners.last().closed)
        assertTrue(listeners.dropLast(1).all { it.closed })
    }

    @Test
    fun repeatedNetworkNotificationsDoNotRevokeAnUnchangedGrant() = runBlocking(main) {
        manager.setForeground(true)
        manager.setEnabled(true)
        val state = awaitState { it.status == ControlListenerStatus.RUNNING }
        val permit = access.authenticate("Bearer " + state.token)!!
        access.configure(true, state.token)
        assertNull(access.check(permit))
        networkChanges.emit(Unit)
        manager.retry()
        assertNull(access.check(permit))
    }

    @Test
    fun bindFailureIsRecoverableWithoutExposingExceptionDetails() = runBlocking(main) {
        failStart = true
        manager.setForeground(true)
        manager.setEnabled(true)
        val failed = awaitState { it.status == ControlListenerStatus.ERROR }
        assertFalse(failed.toString().contains("private-detail"))
        assertTrue(listeners.first().closed)
        failStart = false
        manager.retry()
        awaitState { it.status == ControlListenerStatus.RUNNING }
        assertEquals(2, listeners.size)
    }

    @Test
    fun disablingDuringStartupClosesTheNewlyCreatedListener() = runBlocking(main) {
        startGate = CountDownLatch(1)
        manager.setForeground(true)
        manager.setEnabled(true)
        awaitState { it.status == ControlListenerStatus.STARTING }
        manager.setEnabled(false)
        startGate!!.countDown()
        awaitState { it.status == ControlListenerStatus.DISABLED }
        assertTrue(listeners.all { it.closed })
        val permit = access.authenticate("Bearer " + store.value.token)
        assertTrue(permit == null || access.check(permit) != null)
    }

    @Test
    fun failedCredentialPersistenceFailsClosedAndCanBeRetried() = runBlocking(main) {
        manager.setForeground(true)
        manager.setEnabled(true)
        val initial = awaitState { it.status == ControlListenerStatus.RUNNING }
        store.failWrites = true
        manager.rotateCredential()
        awaitState { it.status == ControlListenerStatus.ERROR }
        assertNull(access.authenticate("Bearer " + initial.token))
        store.failWrites = false
        manager.retry()
        val recovered = awaitState { it.status == ControlListenerStatus.RUNNING }
        assertNotEquals(initial.token, recovered.token)
    }

    private class MemorySettings : RemoteControlSettingsStore {
        var value = RemoteControlSettings(false, "initial-fixture-token")
        var failWrites = false
        override suspend fun read() = value
        override suspend fun save(settings: RemoteControlSettings) {
            if (failWrites) throw IOException("private-detail")
            value = settings
        }
    }

    private class TestListener(private val fail: Boolean, private val gate: CountDownLatch?) : ControlListener {
        @Volatile var closed = false
        override fun start(): Int {
            check(gate?.await(5, TimeUnit.SECONDS) != false)
            if (fail) {
                close()
                throw IOException("private-detail")
            }
            return 8765
        }
        override fun stopAccepting() { closed = true }
        override fun close() { closed = true }
    }
}
