package com.example.atv.domain.util

/**
 * Rewrites multicast channel URLs (`igmp://`, `rtp://`) to an HTTP URL served by a
 * udpxy proxy, which relays the multicast UDP stream over HTTP so ExoPlayer can
 * play it:
 *
 *   igmp://239.49.0.1:8000  ->  http://<proxy>/udp/239.49.0.1:8000
 *
 * The `proxy` is a user-configured `host:port` (e.g. `openwrt:4022`). A leading
 * `http(s)://` and a trailing `/` on the proxy are tolerated. When `proxy` is
 * null/blank, or the URL is not a multicast scheme, the input is returned unchanged.
 */
object UdpxyUrlRewriter {

    private val MULTICAST_SCHEMES = listOf("igmp://", "rtp://")

    fun rewrite(streamUrl: String, proxy: String?): String {
        val normalizedProxy = proxy
            ?.trim()
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.trimEnd('/')
            .orEmpty()
        if (normalizedProxy.isEmpty()) return streamUrl

        val scheme = MULTICAST_SCHEMES.firstOrNull { streamUrl.startsWith(it, ignoreCase = true) }
            ?: return streamUrl

        val address = streamUrl.substring(scheme.length)
        return "http://$normalizedProxy/udp/$address"
    }
}
