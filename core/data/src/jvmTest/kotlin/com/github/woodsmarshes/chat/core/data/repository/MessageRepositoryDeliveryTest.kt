package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.dao.ConversationDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.UserEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import io.mockk.coVerify
import io.mockk.clearMocks
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryDeliveryTest {
    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val serverId = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val nextId = Uuid.parse("00000000-0000-0000-0000-000000000021")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val sender = SimpleUser(userId, "user", null, null, now, now, null, UserRole.MEMBER)
    private fun message(id: Uuid, requestId: Uuid? = null, seq: Long? = null) = Message(
        id, conversationId, sender, MessageCategory.NORMAL, now,
        content = TextContent("hello"), seq = seq, clientRequestId = requestId,
    )

    private suspend fun TestScope.fixture(
        autoStart: Boolean = true,
        decorate: (MessageDao) -> MessageDao = { it },
        block: suspend (OfflineFirstMessageRepositoryImpl, MessageDao, RealtimeApi, MutableSharedFlow<RealtimeEvent>, ConversationApi) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dao = MessageDaoImpl({ db }, coroutineContext)
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null))
        val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
        val api = mockk<RealtimeApi>()
        every { api.events } returns events
        every { api.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { api.send(any()) } returns Unit
        val rest = mockk<ConversationApi>()
        coEvery { rest.syncMessages(any(), any(), any()) } returns emptyList()
        val settings = mockk<UserSettingDataSource>()
        every { settings.user } returns MutableStateFlow(User(userId, "user", null, null, null, null, now, now, null))
        val repo = OfflineFirstMessageRepositoryImpl(
            decorate(dao), UserDaoImpl({ db }, coroutineContext), ParticipantDaoImpl({ db }, coroutineContext),
            api, rest, ConversationDaoImpl({ db }, coroutineContext), settings, backgroundScope,
            mediatorFactory = io.mockk.mockk(),
        )
        try {
            if (autoStart) repo.startSession()
            block(repo, dao, api, events, rest)
        } finally {
            repo.stopSession()
            runCurrent()
            driver.close()
        }
    }

    @Test
    fun anotherUsersMessageCannotBeRetried() = runTest {
        fixture(decorate = { delegate ->
            object : MessageDao by delegate {
                override fun getMessageById(id: Uuid) = delegate.getMessageById(id).map {
                    it?.copy(user_id = nextId)
                }
                override suspend fun findMessageById(id: Uuid) =
                    delegate.findMessageById(id)?.copy(user_id = nextId)
            }
        }) { repo, dao, api, _, _ ->
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.FAILED))
            assertEquals(com.github.michaelbull.result.Err(com.github.woodsmarshes.chat.core.model.error.MessageError.PermissionDenied), repo.retryMessage(serverId))
            coVerify(exactly = 0) { api.send(any()) }
            assertEquals(MessageStatus.FAILED, dao.getMessageById(serverId).first()?.local_send_status)
        }
    }
    @Test
    fun concurrentManualAndAutomaticRetriesKeepIdentityAndReply() = runTest {
        fixture { repo, dao, api, _, _ ->
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.SENT))
            dao.insertMessage(message(nextId).copy(replyTo = message(serverId)).toMessageEntity(MessageStatus.FAILED))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { api.send(any()) } coAnswers {
                val request = firstArg<RealtimeEvent>() as MessageRequest.Send
                assertEquals(nextId.toString(), request.requestId)
                assertEquals(serverId, request.replyToMessageId)
                entered.complete(Unit)
                release.await()
            }
            val first = launch { repo.retryMessage(nextId) }
            entered.await()
            val second = launch { repo.retryMessage(nextId) }
            val automatic = launch { repo.retryPendingMessages() }
            runCurrent()
            release.complete(Unit)
            first.join()
            second.join()
            automatic.join()
            coVerify(exactly = 1) { api.send(any()) }
            assertEquals(now, dao.getMessageById(nextId).first()?.created_at)
            assertEquals(MessageStatus.SENDING, dao.getMessageById(nextId).first()?.local_send_status)
            assertEquals(2, dao.getMessagesPaged(conversationId, Instant.DISTANT_FUTURE, 100).first().size)
        }
    }

    @Test
    fun offlineRetryQueuesWithoutSendingAndSentRowCannotBeRetried() = runTest {
        fixture { repo, dao, api, _, _ ->
            every { api.connectionState } returns MutableStateFlow(ConnectionState.Idle)
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.FAILED))
            dao.insertMessage(message(nextId).toMessageEntity(MessageStatus.SENT))
            repo.retryMessage(serverId)
            repo.retryMessage(nextId)
            coVerify(exactly = 0) { api.send(any()) }
            assertEquals(MessageStatus.SENDING, dao.getMessageById(serverId).first()?.local_send_status)
            assertEquals(MessageStatus.SENT, dao.getMessageById(nextId).first()?.local_send_status)
        }
    }
    @Test
    fun immediateAckSeesDurableRowAndConverges() = runTest {
        fixture { repo, dao, api, events, _ ->
            var localId: Uuid? = null
            coEvery { api.send(any()) } coAnswers {
                val request = firstArg<RealtimeEvent>() as MessageRequest.Send
                val id = Uuid.parse(request.requestId)
                localId = id
                assertNotNull(dao.getMessageById(id).first())
                val ack = message(serverId, id)
                events.emit(MessageEventResponse.Received(ack, conversationId, userId, request.requestId))
                yield()
            }
            repo.sendMessage(conversationId, TextContent("hello"), null)
            runCurrent()
            assertNull(dao.getMessageById(localId!!).first())
            assertEquals(MessageStatus.SENT, dao.getMessageById(serverId).first()?.local_send_status)
            assertEquals(1, dao.getMessagesPaged(conversationId, Instant.DISTANT_FUTURE, 100).first().size)
        }
    }

    @Test
    fun nextEventDoesNotCancelSlowPersistence() = runTest {
        val gate = CompletableDeferred<Unit>()
        fixture(decorate = { delegate ->
            object : MessageDao by delegate {
                override suspend fun mergeServerMessage(message: MessageEntity, localId: Uuid?) {
                    if (message.id == serverId) gate.await()
                    delegate.mergeServerMessage(message, localId)
                }
            }
        }) { _, dao, _, events, _ ->
            events.emit(MessageEventResponse.Received(message(serverId), conversationId, userId, ""))
            runCurrent()
            events.emit(MessageEventResponse.Received(message(nextId), conversationId, userId, ""))
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertNotNull(dao.getMessageById(serverId).first())
            assertNotNull(dao.getMessageById(nextId).first())
        }
    }

    @Test
    fun failedPersistenceRepairsFromDurableCursorNotPreview() = runTest {
        var fail = true
        fixture(decorate = { delegate ->
            object : MessageDao by delegate {
                override suspend fun mergeServerMessage(message: MessageEntity, localId: Uuid?) {
                    if (message.id == serverId && fail) {
                        fail = false
                        error("Injected write failure")
                    }
                    delegate.mergeServerMessage(message, localId)
                }
            }
        }) { _, dao, _, events, rest ->
            coEvery { rest.syncMessages(conversationId, Uuid.NIL, any()) } returns listOf(message(serverId, seq = 10), message(nextId, seq = 11))
            events.emit(MessageEventResponse.Received(message(serverId, seq = 10), conversationId, userId, ""))
            events.emit(MessageEventResponse.Received(message(nextId, seq = 11), conversationId, userId, ""))
            runCurrent()
            assertNotNull(dao.getMessageById(serverId).first())
            assertNotNull(dao.getMessageById(nextId).first())
            assertEquals(nextId, dao.getSyncCursor(conversationId))
        }
    }

    @Test
    fun sessionDoesNotStartImplicitlyAndRepeatedStartIsIdempotent() = runTest {
        fixture(autoStart = false) { repo, dao, _, events, rest ->
            events.emit(MessageEventResponse.Received(message(serverId, seq = 1), conversationId, userId, ""))
            events.emit(MessageEventResponse.UserTyping(conversationId, userId, true, now))
            advanceTimeBy(20_000L)
            runCurrent()

            assertNull(dao.getMessageById(serverId).first(), "no events may be consumed before startSession()")
            assertEquals(Triple(0, 0, 0), repo.inMemorySessionStateCountsForTest())
            coVerify(exactly = 0) { rest.syncMessages(any(), any(), any()) }

            repo.startSession()
            repo.startSession()
            runCurrent()

            // Reconnect sync runs once on session start, never twice for a duplicate startSession().
            coVerify(exactly = 1) { rest.syncMessages(conversationId, Uuid.NIL, any()) }
        }
    }

    @Test
    fun stopSessionWaitsForInFlightWorkersAndClearsMemoryBeforeRestart() = runTest {
        fixture { repo, _, _, events, rest ->
            // Seed seq = 1 (via the initial gap repair) and a typing entry in memory.
            coEvery { rest.syncMessages(conversationId, any(), any()) } returns listOf(message(serverId, seq = 1))
            events.emit(MessageEventResponse.Received(message(serverId, seq = 1), conversationId, userId, ""))
            events.emit(MessageEventResponse.UserTyping(conversationId, userId, true, now))
            runCurrent()
            assertEquals(1, repo.getTypingUsersFlow(conversationId).first().size)

            val repairStarted = CompletableDeferred<Unit>()
            val allowRepairCleanupFinish = CompletableDeferred<Unit>()
            var repairCleanupFinished = false
            coEvery { rest.syncMessages(conversationId, any(), any()) } coAnswers {
                repairStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        allowRepairCleanupFinish.await()
                        repairCleanupFinished = true
                    }
                }
            }

            // Jump to seq = 3 to trigger a gap repair child job.
            events.emit(MessageEventResponse.Received(message(nextId, seq = 3), conversationId, userId, ""))
            runCurrent()
            repairStarted.await()
            assertEquals(Triple(1, 1, 1), repo.inMemorySessionStateCountsForTest())

            var firstStopReturned = false
            val firstStop = launch {
                repo.stopSession()
                firstStopReturned = true
            }
            var secondStopReturned = false
            val secondStop = launch {
                repo.stopSession()
                secondStopReturned = true
            }
            runCurrent()

            var restartReturned = false
            var stateCountsAtRestart: Triple<Int, Int, Int>? = null
            val restartJob = launch {
                repo.startSession()
                stateCountsAtRestart = repo.inMemorySessionStateCountsForTest()
                restartReturned = true
            }
            runCurrent()

            assertFalse(firstStopReturned, "stopSession must wait for in-flight gap repair cleanup")
            assertFalse(secondStopReturned, "concurrent stopSession must wait for the same stop")
            assertFalse(restartReturned, "startSession must wait until the stopping session finishes")

            coEvery { rest.syncMessages(conversationId, any(), any()) } returns emptyList()
            allowRepairCleanupFinish.complete(Unit)
            runCurrent()
            firstStop.join()
            secondStop.join()
            restartJob.join()

            assertTrue(repairCleanupFinished)
            assertTrue(firstStopReturned)
            assertTrue(secondStopReturned)
            assertTrue(restartReturned)
            assertEquals(
                Triple(0, 0, 0),
                stateCountsAtRestart,
                "in-memory seq, gap-repair, and typing state must be cleared before the new session starts",
            )
            assertTrue(repo.getTypingUsersFlow(conversationId).first().isEmpty())
        }
    }

    @Test
    fun stopSessionStopsPeriodicSweepReconnectSyncAndConsumerUntilRestarted() = runTest {
        val connectionStates = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        fixture { repo, dao, api, events, rest ->
            every { api.connectionState } returns connectionStates
            repo.stopSession()
            runCurrent()
            clearMocks(rest, answers = false, recordedCalls = true)

            // Flip connectionState while stopped: neither reconnect sync nor
            // periodic sweep nor event consumption may run.
            connectionStates.value = ConnectionState.Idle
            runCurrent()
            connectionStates.value = ConnectionState.Connected
            events.emit(MessageEventResponse.Received(message(serverId, seq = 1), conversationId, userId, ""))
            advanceTimeBy(35_000L)
            runCurrent()

            assertNull(dao.getMessageById(serverId).first(), "stopped session must not consume events")
            coVerify(exactly = 0) { rest.syncMessages(any(), any(), any()) }

            repo.startSession()
            runCurrent()
            // Starting a new session with Connected state triggers reconnect sync immediately.
            coVerify(exactly = 1) { rest.syncMessages(conversationId, any(), any()) }

            events.emit(MessageEventResponse.Received(message(serverId, seq = 1), conversationId, userId, ""))
            runCurrent()
            assertNotNull(dao.getMessageById(serverId).first(), "restarted session consumes events again")

            clearMocks(rest, answers = false, recordedCalls = true)
            advanceTimeBy(16_000L)
            runCurrent()
            coVerify(atLeast = 1) { rest.syncMessages(conversationId, any(), any()) }
        }
    }

    @Test
    fun startSessionGuaranteesSubscriptionReadyForImmediateFirstEvent() = runTest {
        fixture(autoStart = false) { repo, dao, _, events, _ ->
            // No runCurrent() between startSession() and events.emit(): the
            // subscription must already be registered when startSession() returns.
            repo.startSession()
            events.emit(MessageEventResponse.Received(message(serverId, seq = 1), conversationId, userId, ""))
            runCurrent()

            assertNotNull(
                dao.getMessageById(serverId).first(),
                "the very first event emitted right after startSession() must not be dropped by replay=0",
            )
        }
    }

    @Test
    fun publicWriteOperationsAreRefusedWhenStoppedAndCancelledOnStopSession() = runTest {
        val sendEntered = CompletableDeferred<Unit>()
        val allowSendCleanup = CompletableDeferred<Unit>()
        var sendCleanupFinished = false

        fixture(autoStart = false) { repo, dao, api, _, _ ->
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.FAILED))

            // Before startSession(), public write operations are refused without touching DB or WS.
            assertEquals(
                com.github.michaelbull.result.Err(com.github.woodsmarshes.chat.core.model.error.MessageError.PermissionDenied),
                repo.sendMessage(conversationId, TextContent("blocked"), null),
            )
            assertEquals(
                com.github.michaelbull.result.Err(com.github.woodsmarshes.chat.core.model.error.MessageError.PermissionDenied),
                repo.retryMessage(serverId),
            )
            coVerify(exactly = 0) { api.send(any()) }
            assertEquals(1, dao.getMessagesPaged(conversationId, Instant.DISTANT_FUTURE, 100).first().size)

            repo.startSession()
            coEvery { api.send(any()) } coAnswers {
                sendEntered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        allowSendCleanup.await()
                        sendCleanupFinished = true
                    }
                }
            }

            var sendCancelled: CancellationException? = null
            val inFlightSend = launch {
                try {
                    repo.sendMessage(conversationId, TextContent("in-flight"), null)
                } catch (e: CancellationException) {
                    sendCancelled = e
                }
            }
            runCurrent()
            sendEntered.await()

            var stopReturned = false
            val stopJob = launch {
                repo.stopSession()
                stopReturned = true
            }
            runCurrent()

            assertFalse(stopReturned, "stopSession must wait for in-flight sendMessage cleanup")
            // While stopping, new retry/send calls are refused immediately.
            assertEquals(
                com.github.michaelbull.result.Err(com.github.woodsmarshes.chat.core.model.error.MessageError.PermissionDenied),
                repo.retryMessage(serverId),
            )

            allowSendCleanup.complete(Unit)
            runCurrent()
            stopJob.join()
            inFlightSend.join()

            assertTrue(sendCleanupFinished)
            assertTrue(stopReturned)
            assertNotNull(sendCancelled, "in-flight sendMessage must propagate CancellationException on session stop")
        }
    }
}
