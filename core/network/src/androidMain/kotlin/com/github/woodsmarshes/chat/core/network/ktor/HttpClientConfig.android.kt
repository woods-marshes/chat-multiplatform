package com.github.woodsmarshes.chat.core.network.ktor

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.config
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Protocol

actual fun httpEngine(): HttpClientEngineFactory<*> = OkHttp.config {
    duplexStreamingEnabled = true
    config {
        followRedirects(true)
        // HTTP/1.1 only for plain-text connections to the Ktor Netty server.
        // HTTP/2 (h2) is auto-negotiated via ALPN when TLS is enabled in production.
        // QUIC/HTTP_3 requires the okhttp-quic dependency + server-side UDP support,
        // neither of which is available in this setup.
        //protocols(listOf(Protocol.HTTP_1_1))
        protocols(listOf(Protocol.HTTP_3, Protocol.QUIC, Protocol.HTTP_2, Protocol.HTTP_1_1))
    }
}
