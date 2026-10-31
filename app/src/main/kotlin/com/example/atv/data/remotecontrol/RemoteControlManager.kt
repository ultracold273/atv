package com.example.atv.data.remotecontrol

import androidx.annotation.MainThread
import com.example.atv.domain.model.RemoteControlSettings
import com.example.atv.domain.repository.RemoteControlSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Foreground owner. Reconciliation is serialized; blocking storage/TLS/socket work runs on IO. */
@Singleton
class RemoteControlManager @Inject constructor(
    private val store: RemoteControlSettingsStore,
    private val identities: ControlTlsIdentityProvider,
    private val factory: ControlListenerFactory,
    private val access: RemoteControlAccess,
    private val network: ControlNetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(RemoteControlState())
    val state = mutableState.asStateFlow()

    private var settings: RemoteControlSettings? = null
    private var desiredEnabled: Boolean? = null
    private var foreground = false
    private var requestedRotation = 0L
    private var savedRotation = 0L
    private var identity: ControlTlsIdentity? = null
    private var listener: ControlListener? = null
    private var retireListener = false
    private var networkJob: Job? = null
    private val reconciler = scope.launch { for (ignored in signals) reconcileSafely() }

    init { signals.trySend(Unit) }

    @MainThread
    fun setForeground(value: Boolean) {
        foreground = value
        access.setForeground(value)
        if (value) {
            if (networkJob == null) networkJob = scope.launch {
                network.changes().catch { signals.trySend(Unit) }.collect { signals.trySend(Unit) }
            }
        } else {
            networkJob?.cancel()
            networkJob = null
            invalidateListener()
        }
        signals.trySend(Unit)
    }

    @MainThread
    fun setEnabled(value: Boolean) {
        desiredEnabled = value
        if (!value) {
            access.configure(false, "")
            invalidateListener()
        }
        mutableState.value = state.value.copy(enabled = value)
        signals.trySend(Unit)
    }

    @MainThread
    fun rotateCredential() {
        requestedRotation++
        access.configure(false, "")
        signals.trySend(Unit)
    }

    @MainThread
    fun retry() { signals.trySend(Unit) }

    // Settings boundary: expose a localized error, never secrets or exception details.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reconcileSafely() {
        try {
            reconcile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            access.configure(false, "")
            stopListener()
            mutableState.value = state.value.copy(status = ControlListenerStatus.ERROR)
        }
    }

    private suspend fun reconcile() {
        updateSettings()
        if (retireListener) stopListener()
        val endpoints = withContext(Dispatchers.IO) { network.endpoints() }
        startListenerIfNeeded()
        if (!shouldRun() || retireListener) stopListener()
        publish(endpoints)
    }

    private suspend fun startListenerIfNeeded() {
        if (!shouldRun() || listener != null) return
        mutableState.value = state.value.copy(status = ControlListenerStatus.STARTING)
        val tls = identity ?: identities.load().also { identity = it }
        if (shouldRun()) listener = withContext(Dispatchers.IO) {
            factory.create(tls).also { it.start() }
        }
    }

    private suspend fun updateSettings() {
        val stored = settings ?: store.read().also { settings = it }
        val rotation = requestedRotation
        val updated = RemoteControlSettings(
            desiredEnabled ?: stored.enabled,
            if (rotation != savedRotation) RemoteControlSettings.newToken() else stored.token,
        )
        if (updated != stored) store.save(updated)
        settings = updated
        savedRotation = rotation
        if (desiredEnabled == null) desiredEnabled = updated.enabled
    }

    private fun shouldRun(): Boolean = foreground && desiredEnabled == true && requestedRotation == savedRotation

    private fun publish(endpoints: List<String>) {
        val saved = settings ?: return
        val running = listener != null && shouldRun()
        access.configure(running, saved.token)
        mutableState.value = RemoteControlState(
            enabled = desiredEnabled == true,
            status = when {
                running -> ControlListenerStatus.RUNNING
                desiredEnabled != true -> ControlListenerStatus.DISABLED
                else -> ControlListenerStatus.PAUSED
            },
            endpoints = endpoints,
            token = saved.token,
            fingerprint = identity?.fingerprint.orEmpty(),
        )
    }

    private fun invalidateListener() {
        retireListener = true
        listener?.stopAccepting()
    }

    private suspend fun stopListener() {
        val current = listener
        listener = null
        retireListener = false
        if (current != null) withContext(Dispatchers.IO) { current.close() }
    }

    /** Allows deterministic owner teardown in integration tests or an embedding host. */
    suspend fun shutdown() {
        setForeground(false)
        signals.close()
        reconciler.join()
        stopListener()
        scope.cancel()
    }
}
