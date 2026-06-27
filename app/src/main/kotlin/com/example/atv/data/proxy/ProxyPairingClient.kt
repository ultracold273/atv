package com.example.atv.data.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyPairingClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }

    suspend fun createSession(
        proxyBaseUrl: String,
        requestDto: ProxyPairingCreateRequestDto,
    ): Result<ProxyPairingCreateResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(proxyBaseUrl.trimEnd('/') + "/api/v1/pairing/sessions")
                .post(json.encodeToString(requestDto).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(proxyErrorMessage(response.code, body))
                }
                json.decodeFromString<ProxyPairingCreateResponseDto>(body)
            }
        }.onFailure { Timber.d(it, "Proxy pairing create failed") }
    }

    suspend fun pollSession(
        proxyBaseUrl: String,
        sessionId: String,
        clientNonce: String,
    ): Result<ProxyPairingPollResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(proxyBaseUrl.trimEnd('/') + "/api/v1/pairing/sessions/$sessionId")
                .header("X-Client-Nonce", clientNonce)
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(proxyErrorMessage(response.code, body))
                }
                json.decodeFromString<ProxyPairingPollResponseDto>(body)
            }
        }.onFailure { Timber.d(it, "Proxy pairing poll failed") }
    }

    private fun proxyErrorMessage(code: Int, body: String): String {
        val parsed = runCatching { json.decodeFromString<ProxyErrorResponseDto>(body) }
            .getOrNull()
        return when {
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> "proxy pairing authorization failed"
            parsed != null -> "${parsed.error.code}: ${parsed.error.message}"
            else -> "proxy pairing HTTP $code"
        }
    }
}
