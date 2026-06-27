package com.example.atv.data.proxy

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.Program
import com.example.atv.domain.model.ProxySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyChannelClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val MIN_DATE_OFFSET = -1
        const val MAX_DATE_OFFSET = 1
    }

    suspend fun fetchChannels(settings: ProxySettings): Result<List<Channel>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = settings.proxyBaseUrl.trimEnd('/') + "/api/v1/channels"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${settings.accessToken}")
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(proxyErrorMessage(response.code, body))
                }
                val dto = json.decodeFromString<ProxyChannelResponseDto>(body)
                dto.data.map { channel ->
                    Channel(
                        number = channel.number,
                        name = channel.name,
                        streamUrl = channel.streamUrl,
                        groupTitle = null,
                        logoUrl = null,
                        channelCode = channel.channelCode,
                    )
                }
            }
        }.onFailure { Timber.d(it, "Proxy channel fetch failed") }
    }

    suspend fun fetchPrograms(
        settings: ProxySettings,
        channelCode: String,
        dateOffset: Int,
    ): Result<List<Program>> = withContext(Dispatchers.IO) {
        runCatching {
            require(dateOffset in MIN_DATE_OFFSET..MAX_DATE_OFFSET) { "dateOffset must be -1, 0, or 1" }
            val url = buildString {
                append(settings.proxyBaseUrl.trimEnd('/'))
                append("/api/v1/epg/day?channelCode=")
                append(urlEncode(channelCode))
                append("&dateOffset=")
                append(dateOffset)
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${settings.accessToken}")
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(proxyErrorMessage(response.code, body))
                }
                val dto = json.decodeFromString<ProxyEpgResponseDto>(body)
                dto.data.map { program ->
                    Program(
                        code = program.code,
                        name = program.name,
                        start = parseProgramInstant(program.start),
                        end = parseProgramInstant(program.end),
                        isLive = program.isLive,
                        isReplayable = program.isReplayable,
                    )
                }
            }
        }.onFailure { Timber.d(it, "Proxy EPG fetch failed") }
    }

    private fun proxyErrorMessage(code: Int, body: String): String {
        val parsed = runCatching { json.decodeFromString<ProxyErrorResponseDto>(body) }
            .getOrNull()
        return when {
            code == HTTP_UNAUTHORIZED -> "proxy authorization failed"
            parsed != null -> "${parsed.error.code}: ${parsed.error.message}"
            body.isBlank() -> "proxy HTTP $code"
            else -> "proxy HTTP $code"
        }
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder
        .encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")

    private fun parseProgramInstant(value: String): Instant = runCatching { Instant.parse(value) }
        .getOrElse { OffsetDateTime.parse(value).toInstant() }
}
