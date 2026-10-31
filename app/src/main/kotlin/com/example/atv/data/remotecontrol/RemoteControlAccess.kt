package com.example.atv.data.remotecontrol

import com.example.atv.domain.model.ChannelControlFailure
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Synchronous revocation gate: queued work cannot survive a credential or lifecycle change. */
@Singleton
class RemoteControlAccess @Inject constructor() {
    class Permit internal constructor(internal val epoch: Long, internal val credentialEpoch: Long)

    private var epoch = 0L
    private var credentialEpoch = 0L
    private var token = ""
    private var enabled = false
    private var foreground = false

    @Synchronized
    fun configure(enabled: Boolean, token: String) {
        if (this.token == token && this.enabled == enabled) return
        if (this.token != token) credentialEpoch++
        this.token = token
        this.enabled = enabled
        epoch++
    }

    @Synchronized
    fun setForeground(value: Boolean) {
        if (foreground != value) epoch++
        foreground = value
    }

    @Synchronized
    fun authenticate(header: String?): Permit? {
        val supplied = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(BEARER_PREFIX_LENGTH)
        val matches = supplied != null && token.isNotEmpty() && MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8)
        )
        return if (matches) Permit(epoch, credentialEpoch) else null
    }

    @Synchronized
    fun check(permit: Permit): ChannelControlFailure? = when {
        permit.credentialEpoch != credentialEpoch -> ChannelControlFailure.UNAUTHORIZED
        !foreground || !enabled || permit.epoch != epoch -> ChannelControlFailure.PLAYBACK_UNAVAILABLE
        else -> null
    }

    private companion object {
        const val BEARER_PREFIX_LENGTH = 7
    }
}
