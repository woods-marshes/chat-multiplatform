package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.core.contract.serialization.ProjectProtobuf
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = 1024 * 1024 * 10
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(ProjectProtobuf)
    }
}
