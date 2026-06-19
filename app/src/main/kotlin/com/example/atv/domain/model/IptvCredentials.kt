package com.example.atv.domain.model

import java.net.URI
import java.net.URISyntaxException

/**
 * User-entered CTC IPTV credentials. Stored encrypted via
 * [com.example.atv.data.local.secure.IptvCredentialsStore].
 *
 * `isComplete` mirrors the form-validation rules in spec 005 FR-011: every field
 * non-blank, STB ID exactly 32 chars, auth server URL parses as http(s).
 */
data class IptvCredentials(
    val userId: String,
    val password: String,
    val stbId: String,
    val ip: String,
    val mac: String,
    val authServerUrl: String,
) {
    val isComplete: Boolean
        get() = userId.isNotBlank() &&
            password.isNotBlank() &&
            stbId.length == 32 &&
            ip.isNotBlank() &&
            mac.isNotBlank() &&
            isHttpUrl(authServerUrl)

    private fun isHttpUrl(s: String): Boolean {
        if (s.isBlank()) return false
        return try {
            val u = URI(s)
            u.scheme in setOf("http", "https") && !u.host.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }
    }
}
