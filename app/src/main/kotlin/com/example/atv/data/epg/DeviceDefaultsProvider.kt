package com.example.atv.data.epg

import com.example.atv.domain.model.IptvCredentials
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates first-open defaults for the IPTV setup form. Auto-fills STB ID, IP,
 * MAC, and the operator auth server URL; UserID and Password start empty (the
 * user must enter them).
 */
interface DeviceDefaultsProvider {
    fun generate(): IptvCredentials
}

@Singleton
class DefaultDeviceDefaultsProvider @Inject constructor() : DeviceDefaultsProvider {

    // Test seam: inject a deterministic Random and a synthetic LAN source. Production
    // uses SecureRandom and a NetworkInterface scan, neither of which needs a Context.
    private var random: Random = SecureRandom()
    private var lanIpSource: () -> String? = ::detectLanIp

    internal constructor(
        random: Random,
        lanIpSource: () -> String?,
    ) : this() {
        this.random = random
        this.lanIpSource = lanIpSource
    }

    override fun generate(): IptvCredentials = IptvCredentials(
        userId = "",
        password = "",
        stbId = randomHex(random, STB_ID_HEX_CHARS),
        ip = lanIpSource() ?: FALLBACK_IP,
        mac = "00:00:5E:00:53:%02X".format(random.nextInt(MAC_LAST_BYTE_BOUND)),
        authServerUrl = DEFAULT_AUTH_SERVER_URL,
    )

    private fun randomHex(rnd: Random, lengthChars: Int): String {
        val bytes = ByteArray(lengthChars / 2)
        rnd.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun detectLanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList().asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull()
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    companion object {
        const val STB_ID_HEX_CHARS = 32
        const val MAC_LAST_BYTE_BOUND = 0x100 // exclusive upper bound → one random byte
        const val FALLBACK_IP = "192.0.2.1" // RFC 5737 TEST-NET-1
        const val DEFAULT_AUTH_SERVER_URL = "http://itv.jsinfo.net:8298"
    }
}
