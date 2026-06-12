package com.github.woodsmarshes.chat.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

interface EventBus {
    val contactEvents: SharedFlow<ContactEvent>
    val conversationEvents: SharedFlow<ConversationEvent>
    val messageEvents: SharedFlow<MessageEvent>

    fun publishContactEvent(event: ContactEvent)
    fun publishConversationEvent(event: ConversationEvent)
    fun publishMessageEvent(event: MessageEvent)
}

class EventBusImpl : EventBus, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun close() {
        scope.cancel()
    }

    private val _contactEvents = MutableSharedFlow<ContactEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val contactEvents: SharedFlow<ContactEvent> = _contactEvents.asSharedFlow()

    private val _conversationEvents = MutableSharedFlow<ConversationEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val conversationEvents: SharedFlow<ConversationEvent> = _conversationEvents.asSharedFlow()

    private val _messageEvents = MutableSharedFlow<MessageEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val messageEvents: SharedFlow<MessageEvent> = _messageEvents.asSharedFlow()

    override fun publishContactEvent(event: ContactEvent) {
        scope.launch {
            _contactEvents.emit(event)
        }
    }

    override fun publishConversationEvent(event: ConversationEvent) {
        scope.launch {
            _conversationEvents.emit(event)
        }
    }

    override fun publishMessageEvent(event: MessageEvent) {
        scope.launch {
            _messageEvents.emit(event)
        }
    }
}
