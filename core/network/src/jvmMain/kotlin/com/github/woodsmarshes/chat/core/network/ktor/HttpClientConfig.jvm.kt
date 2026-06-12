package com.github.woodsmarshes.chat.core.network.ktor

import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.config

actual fun httpEngine(): io.ktor.client.engine.HttpClientEngineFactory<*> = CIO.config {

}