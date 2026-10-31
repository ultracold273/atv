package com.example.atv.data.remotecontrol

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal class ControlHttpHandler(
    private val api: RemoteControlApi,
    private val scope: CoroutineScope,
) : SimpleChannelInboundHandler<FullHttpRequest>() {
    private var inFlight = false
    private var requestJob: Job? = null

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        // Do not queue pipelined commands or send replies out of order.
        if (inFlight) {
            ctx.close()
            return
        }
        val parsed = readRequest(request)
        if (parsed == null) {
            sendResponse(ctx, ControlHttpResponse.error(
                HttpResponseStatus.BAD_REQUEST.code(), "INVALID_REQUEST", "Invalid HTTP or UTF-8 request."
            ))
            return
        }
        inFlight = true
        val keepAlive = HttpUtil.isKeepAlive(request)
        requestJob = scope.launch {
            val response = try {
                withTimeout(ControlProtocol.REQUEST_TIMEOUT_MS) { api.handle(parsed) { ctx.channel().isActive } }
            } catch (_: TimeoutCancellationException) {
                ControlHttpResponse.error(
                    HttpResponseStatus.SERVICE_UNAVAILABLE.code(), "CONTROL_BUSY", "The request timed out."
                )
            }
            sendResponse(ctx, response, keepAlive) {
                inFlight = false
            }
        }
    }

    private fun readRequest(request: FullHttpRequest): ControlHttpRequest? {
        if (!request.decoderResult().isSuccess || request.headers().getAll(HttpHeaderNames.AUTHORIZATION).size > 1) {
            return null
        }
        val body = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(request.content().nioBuffer()).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        return ControlHttpRequest(
            request.method().name(), request.uri(), request.headers()[HttpHeaderNames.AUTHORIZATION],
            request.headers()[HttpHeaderNames.CONTENT_TYPE], body
        )
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        requestJob?.cancel()
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        // Protocol/TLS errors and timeouts must never escape into logs with request credentials.
        ctx.close()
    }
}

internal fun sendResponse(
    ctx: ChannelHandlerContext,
    response: ControlHttpResponse,
    keepAlive: Boolean = false,
    completed: () -> Unit = {},
) {
    val bytes = response.body.toByteArray(Charsets.UTF_8)
    val message = DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.status), Unpooled.wrappedBuffer(bytes)
    )
    message.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
    message.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store")
    message.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
    val connection = if (keepAlive) HttpHeaderValues.KEEP_ALIVE else HttpHeaderValues.CLOSE
    message.headers().set(HttpHeaderNames.CONNECTION, connection)
    response.headers.forEach { (name, value) -> message.headers().set(name, value) }
    val written = ctx.writeAndFlush(message)
    if (!keepAlive) written.addListener(ChannelFutureListener.CLOSE)
    written.addListener { completed() }
}
