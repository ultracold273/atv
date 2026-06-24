package com.example.atv.data.proxy

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.ProxySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyChannelClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
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

    private fun proxyErrorMessage(code: Int, body: String): String {
        val parsed = runCatching { json.decodeFromString<ProxyErrorResponseDto>(body) }
            .getOrNull()
        return when {
            code == 401 -> "proxy authorization failed"
            parsed != null -> "${parsed.error.code}: ${parsed.error.message}"
            body.isBlank() -> "proxy HTTP $code"
            else -> "proxy HTTP $code"
        }
    }
}

