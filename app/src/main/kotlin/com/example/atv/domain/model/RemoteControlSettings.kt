package com.example.atv.domain.model

import java.security.SecureRandom
import java.util.Base64

data class RemoteControlSettings(val enabled: Boolean, val token: String) {
    override fun toString(): String = "RemoteControlSettings(enabled=$enabled, token=redacted)"

    companion object {
        private const val TOKEN_BYTES = 32

        fun newToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        )
    }
}
