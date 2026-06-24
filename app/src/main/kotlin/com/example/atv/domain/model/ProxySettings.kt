package com.example.atv.domain.model

import java.net.URI
import java.net.URISyntaxException

data class ProxySettings(
    val proxyBaseUrl: String,
    val accessToken: String,
) {
    val isComplete: Boolean
        get() = accessToken.isNotBlank() && isHttpUrl(proxyBaseUrl)

    private fun isHttpUrl(value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val uri = URI(value)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }
    }
}

