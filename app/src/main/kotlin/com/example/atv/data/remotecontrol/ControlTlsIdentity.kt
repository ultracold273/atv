package com.example.atv.data.remotecontrol

import javax.net.ssl.SSLContext

data class ControlTlsIdentity(val context: SSLContext, val fingerprint: String)

fun interface ControlTlsIdentityProvider {
    suspend fun load(): ControlTlsIdentity
}

fun interface ControlListenerFactory {
    fun create(identity: ControlTlsIdentity): ControlListener
}
