package com.example.atv.data.proxy

import kotlinx.serialization.Serializable

@Serializable
data class ProxyPairingCreateRequestDto(
    val deviceName: String,
    val deviceType: String = "android_tv",
    val appId: String,
    val appVersion: String,
    val clientNonce: String,
)

@Serializable
data class ProxyPairingCreateResponseDto(
    val sessionId: String,
    val pairingCode: String,
    val expiresAt: Long,
    val pollIntervalSeconds: Long = 2,
)

@Serializable
data class ProxyPairingPollResponseDto(
    val status: String,
    val expiresAt: Long? = null,
    val pollIntervalSeconds: Long? = null,
    val accessToken: String? = null,
    val tokenType: String? = null,
)
