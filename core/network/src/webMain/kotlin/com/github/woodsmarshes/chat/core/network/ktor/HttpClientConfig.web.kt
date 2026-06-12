package com.github.woodsmarshes.chat.core.network.ktor

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.config
import io.ktor.client.engine.js.Js

actual fun httpEngine(): HttpClientEngineFactory<*> = Js.config {

}