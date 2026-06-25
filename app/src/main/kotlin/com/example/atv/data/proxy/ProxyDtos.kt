package com.example.atv.data.proxy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProxyChannelResponseDto(
    val data: List<ProxyChannelDto> = emptyList(),
    val cache: ProxyCacheDto? = null,
)

@Serializable
data class ProxyEpgResponseDto(
    val data: List<ProxyProgramDto> = emptyList(),
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
data class ProxyProgramDto(
    val code: String,
    val name: String,
    val start: String,
    val end: String,
    val isLive: Boolean = false,
    val isReplayable: Boolean = false,
)

@Serializable
data class ProxyCacheDto(
    val stale: Boolean = false,
    val cachedAt: JsonElement? = null,
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
