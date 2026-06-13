package com.github.woodsmarshes.chat.core.network.ktor

import io.ktor.client.engine.config
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Protocol

// CIO engine still not support TLS 1.3
actual fun httpEngine(): io.ktor.client.engine.HttpClientEngineFactory<*> = OkHttp.config {
    duplexStreamingEnabled = true
    config {
        followRedirects(true)
        protocols(listOf(Protocol.HTTP_3, Protocol.QUIC, Protocol.HTTP_2, Protocol.HTTP_1_1))
    }
}