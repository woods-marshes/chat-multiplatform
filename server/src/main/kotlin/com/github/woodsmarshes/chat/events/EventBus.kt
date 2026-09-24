package com.github.woodsmarshes.chat.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface EventBus {
    val contactEvents: SharedFlow<ContactEvent>
    val conversationEvents: SharedFlow<ConversationEvent>
    val messageEvents: SharedFlow<MessageEvent>

    fun publishContactEvent(event: ContactEvent)
    fun publishConversationEvent(event: ConversationEvent)
    fun publishMessageEvent(event: MessageEvent)
}

class EventBusImpl : EventBus, AutoCloseable {
    // DROP_OLDEST guarantees tryEmit never suspends, so events can be published
    // inline from the caller — emitting from separate launched coroutines would
    // reorder events under load.
    private val _contactEvents = MutableSharedFlow<ContactEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val contactEvents: SharedFlow<ContactEvent> = _contactEvents.asSharedFlow()

    private val _conversationEvents = MutableSharedFlow<ConversationEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val conversationEvents: SharedFlow<ConversationEvent> = _conversationEvents.asSharedFlow()

    private val _messageEvents = MutableSharedFlow<MessageEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val messageEvents: SharedFlow<MessageEvent> = _messageEvents.asSharedFlow()

    override fun publishContactEvent(event: ContactEvent) {
        _contactEvents.tryEmit(event)
    }

    override fun publishConversationEvent(event: ConversationEvent) {
        _conversationEvents.tryEmit(event)
    }

    override fun publishMessageEvent(event: MessageEvent) {
        _messageEvents.tryEmit(event)
    }

    override fun close() {
        // SharedFlows hold no resources; kept for AutoCloseable symmetry.
    }
}
