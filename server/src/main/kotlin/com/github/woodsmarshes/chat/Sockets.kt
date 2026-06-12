package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
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
