package com.example.atv.data.remotecontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CatalogResponse(val catalogVersion: String, val channels: List<ChannelMetadata>)

@Serializable
internal data class ChannelMetadata(val number: Int, val name: String, val groupTitle: String?)

@Serializable
internal data class SwitchAcknowledgement(val number: Int, val catalogVersion: String, val status: String = "accepted")

@Serializable
private data class ErrorEnvelope(val error: ApiError)

@Serializable
private data class ApiError(val code: String, val message: String)

class ControlHttpRequest(
    val method: String,
    val uri: String,
    val authorization: String? = null,
    val contentType: String? = null,
    val body: String = "",
) {
    override fun toString(): String = "ControlHttpRequest(method=$method, credentials/body redacted)"
}

data class ControlHttpResponse(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) {
    companion object {
        fun error(status: Int, code: String, message: String): ControlHttpResponse =
            ControlHttpResponse(status, Json.encodeToString(ErrorEnvelope(ApiError(code, message))))
    }
}

internal object ControlProtocol {
    const val PORT = 8765
    const val MAX_BODY_BYTES = 4096
    const val MAX_VERSION_LENGTH = 128
    const val REQUEST_TIMEOUT_MS = 2000L
    const val MAX_CONNECTIONS = 8
    const val SOCKET_TIMEOUT_SECONDS = 5
}
