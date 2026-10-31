package com.example.atv.data.remotecontrol

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext

interface ControlListener : AutoCloseable {
    fun start(): Int
    fun stopAccepting()
}

/** Netty supplies HTTP parsing and framing; the platform JSSE provider supplies TLS. */
class RemoteControlServer(
    private val api: RemoteControlApi,
    private val tls: SSLContext,
    private val address: InetSocketAddress = InetSocketAddress("0.0.0.0", ControlProtocol.PORT),
) : ControlListener {
    init {
        // Android uses the platform logger, not Netty's optional desktop logging integrations.
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
    }

    private val acceptor = NioEventLoopGroup(1)
    private val workers = NioEventLoopGroup(1)
    private val channels = DefaultChannelGroup(workers.next())
    private val connections = AtomicInteger()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: Channel? = null

    @Suppress("TooGenericExceptionCaught") // Release both event loops on any bind/setup failure.
    override fun start(): Int {
        check(listener == null) { "Control listener is already started" }
        try {
            val bound = ServerBootstrap()
                .group(acceptor, workers)
                .channelFactory { NioServerSocketChannel() }
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) = configure(channel)
                })
                .bind(address).sync().channel()
            listener = bound
            channels.add(bound)
            return (bound.localAddress() as InetSocketAddress).port
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    private fun configure(channel: SocketChannel) {
        if (connections.incrementAndGet() > ControlProtocol.MAX_CONNECTIONS) {
            connections.decrementAndGet()
            channel.close()
            return
        }
        channels.add(channel)
        val lifetime = channel.eventLoop().schedule({ channel.close() }, MAX_CONNECTION_SECONDS, TimeUnit.SECONDS)
        channel.closeFuture().addListener {
            connections.decrementAndGet()
            lifetime.cancel(false)
        }
        val engine = tls.createSSLEngine().apply {
            useClientMode = false
            enabledProtocols = supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
        }
        val ssl = SslHandler(engine).apply {
            setHandshakeTimeout(ControlProtocol.SOCKET_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        }
        channel.pipeline()
            .addLast(ReadTimeoutHandler(ControlProtocol.SOCKET_TIMEOUT_SECONDS))
            .addLast(WriteTimeoutHandler(ControlProtocol.SOCKET_TIMEOUT_SECONDS))
            .addLast(ssl)
            .addLast(HttpServerCodec(MAX_INITIAL_LINE, MAX_HEADER_BYTES, ControlProtocol.MAX_BODY_BYTES))
            .addLast(object : HttpObjectAggregator(ControlProtocol.MAX_BODY_BYTES) {
                override fun handleOversizedMessage(ctx: ChannelHandlerContext, oversized: HttpMessage) {
                    sendResponse(ctx, ControlHttpResponse.error(
                        HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.code(),
                        "PAYLOAD_TOO_LARGE", "Limit bodies to 4 KiB."
                    ))
                }
            })
            .addLast(ControlHttpHandler(api, scope))
    }

    override fun stopAccepting() {
        scope.cancel()
        channels.close()
    }

    override fun close() {
        stopAccepting()
        channels.close().awaitUninterruptibly()
        acceptor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly()
        workers.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly()
    }

    private companion object {
        const val MAX_INITIAL_LINE = 4096
        const val MAX_HEADER_BYTES = 8192
        const val MAX_CONNECTION_SECONDS = 30L
    }
}
