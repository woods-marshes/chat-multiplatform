package com.github.woodsmarshes.chat.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicLong

interface EventBus {
    val contactEvents: SharedFlow<ContactEvent>
    val conversationEvents: SharedFlow<ConversationEvent>
    val messageEvents: SharedFlow<MessageEvent>

    fun publishContactEvent(event: ContactEvent)
    fun publishConversationEvent(event: ConversationEvent)
    fun publishMessageEvent(event: MessageEvent)
}

class EventBusImpl(private val meterRegistry: MeterRegistry? = null) : EventBus, AutoCloseable {
    private val logger = org.slf4j.LoggerFactory.getLogger(EventBusImpl::class.java)
    private val droppedEvents = AtomicLong()
    private val droppedCounter = meterRegistry?.counter("chat.eventbus.dropped")

    /** Events dropped at publish time because the buffer saturated. */
    fun droppedCount(): Long = droppedEvents.get()

    /** Current subscriber count on the message channel (ops/test visibility). */
    fun messageSubscriberCount(): Int = _messageEvents.subscriptionCount.value

    // tryEmit never suspends, so events can be published inline from the
    // caller — emitting from separate launched coroutines would reorder
    // events under load. With SUSPEND overflow a saturated buffer makes
    // tryEmit report the drop instead of silently discarding the oldest
    // event; lost events heal through the clients' seq-gap resync.
    private val _contactEvents = MutableSharedFlow<ContactEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val contactEvents: SharedFlow<ContactEvent> = _contactEvents.asSharedFlow()

    private val _conversationEvents = MutableSharedFlow<ConversationEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val conversationEvents: SharedFlow<ConversationEvent> = _conversationEvents.asSharedFlow()

    private val _messageEvents = MutableSharedFlow<MessageEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val messageEvents: SharedFlow<MessageEvent> = _messageEvents.asSharedFlow()

    override fun publishContactEvent(event: ContactEvent) {
        emitCountingDrops(_contactEvents, event, "contact")
    }

    override fun publishConversationEvent(event: ConversationEvent) {
        emitCountingDrops(_conversationEvents, event, "conversation")
    }

    override fun publishMessageEvent(event: MessageEvent) {
        emitCountingDrops(_messageEvents, event, "message")
    }

    private fun <T> emitCountingDrops(flow: MutableSharedFlow<T>, event: T, kind: String) {
        if (!flow.tryEmit(event)) {
            val total = droppedEvents.incrementAndGet()
            droppedCounter?.increment()
            // Warn per drop but never flood: a sustained flood logs every 100th.
            if (total % 100L == 1L) {
                logger.warn("EventBus buffer overflow: dropped {} event (total dropped: {})", kind, total)
            }
        }
    }

    override fun close() {
        val dropped = droppedEvents.get()
        if (dropped > 0) {
            logger.warn("EventBus shut down having dropped {} events in total", dropped)
        }
    }
}
