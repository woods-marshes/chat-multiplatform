package com.github.woodsmarshes.chat.websocket

import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketRealtimeDeliveryTest {

    private val sessions = SessionIndex()
    private val participants = mockk<ConversationParticipantRepository>()
    private val logger = mockk<io.ktor.util.logging.Logger>(relaxUnitFun = true)

    // Unconfined so the dispatch launch runs before sendToConversation returns.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val sent = ConcurrentLinkedQueue<Pair<Uuid, RealtimeEvent>>()

    private val delivery = WebSocketRealtimeDelivery(
        sessions = sessions,
        participants = participants,
        logger = logger,
        scope = scope,
        sender = { session, event -> sent.add(session.coroutineContext[SessionIdKey]!!.id to event) },
    )

    /** Tags a fake session with an identity the fake sender can record. */
    class SessionIdKey(val id: Uuid) : AbstractCoroutineContextElement(SessionIdKey) {
        companion object Key : CoroutineContext.Key<SessionIdKey>
    }

    private class FakeSession(val id: Uuid, live: Boolean) : WebSocketServerSession {
        override val call: ApplicationCall get() = error("not needed")
        override val coroutineContext: CoroutineContext =
            Job().apply { if (!live) cancel() } + SessionIdKey(id)
        override var masking: Boolean = false
        override var maxFrameSize: Long = Long.MAX_VALUE
        override val incoming: ReceiveChannel<Frame> = Channel()
        override val outgoing: SendChannel<Frame> = Channel()
        override val extensions: List<WebSocketExtension<*>> = emptyList()
        override suspend fun send(frame: Frame) {}
        override suspend fun flush() {}
        override fun terminate() {}
    }

    private fun participant(userId: Uuid, conversationId: Uuid) = ConversationParticipant(
        conversationId = conversationId,
        userId = userId,
        role = ConversationRole.PARTICIPANT,
        lastReadMessageId = null,
        joinedAt = Clock.System.now(),
        settings = ParticipantSettings(),
    )

    private fun event() = mockk<RealtimeEvent>()

    @Test
    fun conversationEventsReachEveryOnlineMemberSession() {
        val conversationId = Uuid.random()
        val userA = Uuid.random()
        val userB = Uuid.random()
        val sessionA1 = FakeSession(Uuid.random(), live = true)
        val sessionA2 = FakeSession(Uuid.random(), live = true)
        val sessionB = FakeSession(Uuid.random(), live = true)
        sessions.add(userA, sessionA1)
        sessions.add(userA, sessionA2)
        sessions.add(userB, sessionB)
        coEvery { participants.getConversationParticipants(conversationId) } returns listOf(
            participant(userA, conversationId),
            participant(userB, conversationId),
        )
        val payload = event()

        runBlocking { delivery.sendToConversation(conversationId, payload) }

        assertEquals(
            setOf(sessionA1.id, sessionA2.id, sessionB.id),
            sent.map { it.first }.toSet(),
        )
        assertEquals(listOf(payload, payload, payload), sent.map { it.second })
    }

    @Test
    fun inactiveSessionsAreSkipped() {
        val conversationId = Uuid.random()
        val userId = Uuid.random()
        val deadSession = FakeSession(Uuid.random(), live = false)
        val liveSession = FakeSession(Uuid.random(), live = true)
        sessions.add(userId, deadSession)
        sessions.add(userId, liveSession)
        coEvery { participants.getConversationParticipants(conversationId) } returns listOf(
            participant(userId, conversationId)
        )

        runBlocking { delivery.sendToConversation(conversationId, event()) }

        assertEquals(setOf(liveSession.id), sent.map { it.first }.toSet())
    }

    @Test
    fun sendToUserIgnoresUnknownUsers() {
        runBlocking { delivery.sendToUser(Uuid.random(), event()) }

        assertEquals(emptyList(), sent.toList())
    }
}
