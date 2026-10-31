package com.example.atv.data.remotecontrol

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

interface ControlNetworkMonitor {
    fun changes(): Flow<Unit>
    fun endpoints(): List<String>
}

class AndroidControlNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ControlNetworkMonitor {
    override fun changes(): Flow<Unit> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(Unit) }
            override fun onLost(network: Network) { trySend(Unit) }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) { trySend(Unit) }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        trySend(Unit)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.flowOn(Dispatchers.IO)

    override fun endpoints(): List<String> = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
        .map { "https://" + it.hostAddress + ":" + ControlProtocol.PORT }
        .distinct().sorted()
}
