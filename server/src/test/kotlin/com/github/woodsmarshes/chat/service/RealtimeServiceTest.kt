package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.events.ContactEvent
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.events.EventBusImpl
import com.github.woodsmarshes.chat.events.MessageEvent
import com.github.woodsmarshes.chat.websocket.RealtimeDelivery
import com.github.woodsmarshes.chat.websocket.WebSocketSessionManager
import io.ktor.util.logging.Logger
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Drives the three event consumers end to end over a real EventBus on an
 * unconfined scope, so a publish reaches the consumers synchronously and the
 * assertions are order-proof. What the service publishes is what clients
 * receive, and nothing leaks into the session manager (delivery routes via
 * the DB).
 */
class RealtimeServiceTest {

    private val delivery = mockk<RealtimeDelivery>(relaxUnitFun = true)
    private val sessionManager = mockk<WebSocketSessionManager>(relaxUnitFun = true)
    private val logger = mockk<Logger>(relaxUnitFun = true)
    private val bus: EventBus = EventBusImpl()

    private val conversationId = Uuid.random()
    private val senderId = Uuid.random()
    private val now = Clock.System.now()

    private fun newService(typingDebounceMs: Long = 1_000L): RealtimeService = RealtimeService(
        log = logger,
        eventBus = bus,
        delivery = delivery,
        sessionManager = sessionManager,
        scope = CoroutineScope(Dispatchers.Unconfined),
        typingDebounceMs = typingDebounceMs,
    )

    private fun message() = Message(
        id = Uuid.random(),
        conversationId = conversationId,
        category = MessageCategory.NORMAL,
        createdAt = now,
        content = TextContent("hello"),
        seq = 1L,
    )

    @Test
    fun sendMessageEventIsDeliveredAsReceivedWithItsSeq() {
        newService()
        val message = message()

        bus.publishMessageEvent(MessageEvent.SendMessage(message, conversationId, senderId, now, "req-1"))

        coVerify(exactly = 1) {
            delivery.sendToConversation(conversationId, match {
                it is MessageEventResponse.Received &&
                    it.message.id == message.id &&
                    it.message.seq == 1L &&
                    it.senderId == senderId &&
                    it.requestId == "req-1"
            })
        }
        verify { sessionManager wasNot Called }
    }

    @Test
    fun withdrawAndReadEventsAreDeliveredToTheConversation() {
        newService()
        val messageId = Uuid.random()

        bus.publishMessageEvent(MessageEvent.WithdrawMessage(messageId, conversationId, senderId, now))
        bus.publishMessageEvent(MessageEvent.ReadMessage(messageId, conversationId, senderId, now))

        coVerify(exactly = 1) {
            delivery.sendToConversation(conversationId, match { it is MessageEventResponse.Withdrawn })
        }
        coVerify(exactly = 1) {
            delivery.sendToConversation(conversationId, match { it is MessageEventResponse.Read })
        }
    }

    @Test
    fun typingBurstsDebounceToASingleDelivery() = runBlocking {
        // Shortened debounce so the test stays fast; the burst cancels and
        // reschedules it on every event, converging on one delivery.
        newService(typingDebounceMs = 50)

        repeat(5) {
            bus.publishMessageEvent(
                MessageEvent.UserTyping(conversationId, senderId, isTyping = true, timestamp = now)
            )
        }
        delay(200)

        coVerify(exactly = 1) {
            delivery.sendToConversation(conversationId, match { it is MessageEventResponse.UserTyping })
        }
    }

    @Test
    fun conversationEventsRoutePurelyThroughDelivery() {
        newService()
        val creatorId = Uuid.random()

        bus.publishConversationEvent(
            ConversationEvent.ConversationCreated(conversationId, ConversationType.PRIVATE, creatorId, now)
        )
        bus.publishConversationEvent(
            ConversationEvent.UserJoinedConversation(conversationId, listOf(creatorId), null, now)
        )
        bus.publishConversationEvent(
            ConversationEvent.ConversationDeleted(conversationId, creatorId, now)
        )

        // Three conversation events -> three conversation deliveries, and the
        // session manager is never touched for room bookkeeping.
        coVerify(exactly = 3) { delivery.sendToConversation(conversationId, any()) }
        verify { sessionManager wasNot Called }
    }

    @Test
    fun joinRequestVerdictsGoOnlyToTheApplicant() {
        newService()
        val applicantId = Uuid.random()

        bus.publishConversationEvent(
            ConversationEvent.GroupJoinRequestHandled(
                requestId = Uuid.random(),
                conversationId = conversationId,
                applicantId = applicantId,
                handlerId = Uuid.random(),
                approved = true,
                reason = null,
                timestamp = now,
            )
        )

        coVerify(exactly = 1) { delivery.sendToUser(applicantId, any()) }
        coVerify(exactly = 0) { delivery.sendToConversation(any(), any()) }
    }

    @Test
    fun contactEventsReachBothInvolvedUsers() {
        newService()
        val otherId = Uuid.random()

        bus.publishContactEvent(ContactEvent.ContactAdded(userId = senderId, contactId = otherId, timestamp = now))

        coVerify(exactly = 1) { delivery.sendToUsers(listOf(senderId, otherId), any()) }
    }
}
