package com.example.atv.data.proxy

import kotlinx.serialization.Serializable

@Serializable
data class ProxyChannelResponseDto(
    val data: List<ProxyChannelDto> = emptyList(),
    val cache: ProxyCacheDto? = null,
)

@Serializable
data class ProxyChannelDto(
    val number: Int,
    val name: String,
    val streamUrl: String,
    val channelCode: String? = null,
)

@Serializable
data class ProxyCacheDto(
    val stale: Boolean = false,
    val cachedAt: String? = null,
    val ttlSeconds: Long? = null,
)

@Serializable
data class ProxyErrorResponseDto(
    val error: ProxyErrorDto,
)

@Serializable
data class ProxyErrorDto(
    val code: String,
    val message: String,
)

