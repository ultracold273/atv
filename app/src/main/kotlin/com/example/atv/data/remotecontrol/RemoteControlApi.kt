package com.example.atv.data.remotecontrol

import com.example.atv.domain.model.ChannelControlFailure
import com.example.atv.domain.model.ChannelSwitchResult
import com.example.atv.domain.repository.ChannelCatalogRepository
import com.example.atv.player.ChannelSwitchCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection.HTTP_ACCEPTED
import java.net.HttpURLConnection.HTTP_BAD_METHOD
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.net.HttpURLConnection.HTTP_UNSUPPORTED_TYPE
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteControlApi @Inject constructor(
    private val catalog: ChannelCatalogRepository,
    private val switches: ChannelSwitchCoordinator,
    private val access: RemoteControlAccess,
) {
    private val json = Json { encodeDefaults = true }

    @Suppress("TooGenericExceptionCaught") // HTTP boundary: never return provider URLs or exception messages.
    suspend fun handle(request: ControlHttpRequest, connected: () -> Boolean = { true }): ControlHttpResponse {
        val permit = access.authenticate(request.authorization) ?: return unauthorized()
        access.check(permit)?.let { return failure(it) }
        return try {
            route(request, permit, connected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ControlHttpResponse.error(HTTP_INTERNAL_ERROR, "INTERNAL_ERROR", "The request could not be dispatched.")
        }
    }

    private suspend fun route(
        request: ControlHttpRequest,
        permit: RemoteControlAccess.Permit,
        connected: () -> Boolean,
    ): ControlHttpResponse {
        val path = request.uri.substringBefore('?')
        val method = when (path) {
            "/api/v1/channels" -> "GET"
            "/api/v1/playback/channel" -> "POST"
            else -> return ControlHttpResponse.error(HTTP_NOT_FOUND, "NOT_FOUND", "Unknown control endpoint.")
        }
        return when {
            request.method != method -> ControlHttpResponse.error(
                HTTP_BAD_METHOD, "METHOD_NOT_ALLOWED", "Unsupported method."
            ).copy(headers = mapOf("Allow" to method))
            '?' in request.uri -> invalidRequest()
            method == "GET" -> readCatalog(request)
            else -> switchChannel(request, permit, connected)
        }
    }

    private suspend fun readCatalog(request: ControlHttpRequest): ControlHttpResponse =
        if (request.body.isNotEmpty()) {
            invalidRequest()
        } else {
            catalog.withSnapshot { snapshot ->
                val metadata = snapshot.channels.map { ChannelMetadata(it.number, it.name, it.groupTitle) }
                ControlHttpResponse(HTTP_OK, json.encodeToString(CatalogResponse(snapshot.version, metadata)))
            }
        }

    private suspend fun switchChannel(
        request: ControlHttpRequest,
        permit: RemoteControlAccess.Permit,
        connected: () -> Boolean,
    ): ControlHttpResponse {
        val mediaType = request.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (mediaType != "application/json") {
            return ControlHttpResponse.error(HTTP_UNSUPPORTED_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send application/json.")
        }
        val selection = parseSelection(request.body) ?: return invalidRequest()
        return when (val result = switches.request(selection.first, selection.second) {
            if (connected()) access.check(permit) else ChannelControlFailure.PLAYBACK_UNAVAILABLE
        }) {
            is ChannelSwitchResult.Accepted -> ControlHttpResponse(
                HTTP_ACCEPTED, json.encodeToString(SwitchAcknowledgement(result.number, result.catalogVersion))
            )
            is ChannelSwitchResult.Rejected -> failure(result.failure)
        }
    }

    private fun parseSelection(body: String): Pair<Int, String>? {
        val objectValue = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
        if (objectValue.keys != setOf("number", "catalogVersion")) return null
        val number = objectValue["number"] as? JsonPrimitive
        val version = objectValue["catalogVersion"] as? JsonPrimitive
        return parseValues(number, version)
    }

    private fun parseValues(number: JsonPrimitive?, version: JsonPrimitive?): Pair<Int, String>? {
        val validNumber = number?.takeUnless { it.isString }
            ?.content?.takeIf { CHANNEL_NUMBER.matches(it) }?.toIntOrNull()
        val validVersion = version?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotBlank() && it.length <= ControlProtocol.MAX_VERSION_LENGTH }
        return if (validNumber != null && validVersion != null) validNumber to validVersion else null
    }

    private fun failure(reason: ChannelControlFailure): ControlHttpResponse = when (reason) {
        ChannelControlFailure.CATALOG_CHANGED -> ControlHttpResponse.error(
            HTTP_CONFLICT, reason.name, "Retrieve the channel catalog again before requesting a switch."
        )
        ChannelControlFailure.CHANNEL_NOT_FOUND -> ControlHttpResponse.error(
            HTTP_NOT_FOUND, reason.name, "The channel is not in this catalog."
        )
        ChannelControlFailure.PLAYBACK_UNAVAILABLE -> ControlHttpResponse.error(
            HTTP_UNAVAILABLE, reason.name, "Open the ATV playback screen before switching."
        )
        ChannelControlFailure.CONTROL_BUSY -> ControlHttpResponse.error(
            HTTP_UNAVAILABLE, reason.name, "Another switch is being dispatched."
        )
        ChannelControlFailure.UNAUTHORIZED -> unauthorized()
    }

    private fun unauthorized(): ControlHttpResponse = ControlHttpResponse.error(
        HTTP_UNAUTHORIZED, "UNAUTHORIZED", "A current controller credential is required."
    ).copy(headers = mapOf("WWW-Authenticate" to "Bearer"))

    private fun invalidRequest(): ControlHttpResponse = ControlHttpResponse.error(
        HTTP_BAD_REQUEST, "INVALID_REQUEST", "Provide only an integer number and a catalogVersion from the catalog."
    )

    private companion object {
        val CHANNEL_NUMBER = Regex("[1-9][0-9]*")
    }
}
