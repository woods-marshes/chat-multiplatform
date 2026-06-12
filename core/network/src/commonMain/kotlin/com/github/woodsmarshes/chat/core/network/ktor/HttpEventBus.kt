package com.github.woodsmarshes.chat.core.network.ktor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

interface HttpEventBus {
    suspend fun sendError(event: HttpErrorEvent)

    val errorEvents: Flow<HttpErrorEvent>
}

class HttpEventBusImpl : HttpEventBus {
    private val _channel = Channel<HttpErrorEvent>(Channel.UNLIMITED)

    override val errorEvents: Flow<HttpErrorEvent> = _channel.receiveAsFlow()

    override suspend fun sendError(event: HttpErrorEvent) {
        _channel.send(event)
    }
}