package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.dao.ConversationDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.conversation.UserInfo
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SyncAnchorEntry
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
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
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private val old1 = Uuid.parse("00000000-0000-0000-0000-000000000031")
    private val coldNewest = Uuid.parse("00000000-0000-0000-0000-000000000039")
    private val old2 = Uuid.parse("00000000-0000-0000-0000-000000000032")
    private val new1 = Uuid.parse("00000000-0000-0000-0000-000000000041")
    private val new2 = Uuid.parse("00000000-0000-0000-0000-000000000042")
    private val ancient = Uuid.parse("00000000-0000-0000-0000-000000000030")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val sender = SimpleUser(userId, "user", null, null, now, now, null, UserRole.MEMBER)
    private fun message(id: Uuid, requestId: Uuid? = null, seq: Long? = null) = Message(
        id, conversationId, sender, MessageCategory.NORMAL, now,
        content = TextContent("hello"), seq = seq, clientRequestId = requestId,
    )

    private fun detailResponse(id: Uuid = conversationId, unreadCount: Int = 0) = ConversationResponse(
        conversationId = id,
        type = ConversationType.PRIVATE,
        lastMessage = null,
        metadata = null,
        participant = ConversationParticipant(
            conversationId = id,
            userId = userId,
            role = ConversationRole.MEMBER,
            lastReadMessageId = null,
            joinedAt = now,
            settings = ParticipantSettings(),
        ),
        conversationInfo = UserInfo(
            id = ancient,
            username = "peer",
            displayName = null,
            avatarUrl = null,
            bio = null,
            createdAt = now,
            updatedAt = now,
            role = UserRole.MEMBER,
            deletedAt = null,
        ),
        unreadCount = unreadCount,
    )

    private suspend fun TestScope.fixture(
        autoStart: Boolean = true,
        observeConversations: (ConversationDaoImpl) -> Unit = {},
        observeConversationRepository: (ConversationRepository) -> Unit = {},
        decorate: (MessageDao) -> MessageDao = { it },
        block: suspend (OfflineFirstMessageRepositoryImpl, MessageDao, RealtimeApi, MutableSharedFlow<RealtimeEvent>, ConversationApi) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dao = MessageDaoImpl({ db }, coroutineContext)
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.usersQueries.upsertUser(UserEntity(ancient, "peer", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 0))
        observeConversations(ConversationDaoImpl({ db }, coroutineContext))
        val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
        val api = mockk<RealtimeApi>()
        every { api.events } returns events
        every { api.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { api.send(any()) } returns true
        val rest = mockk<ConversationApi>()
        coEvery { rest.syncMessages(any(), any(), any()) } returns emptyList()
        coEvery { rest.getDetail(any()) } answers { detailResponse(id = firstArg(), unreadCount = 0) }
        val conversationRepository = mockk<ConversationRepository>(relaxed = true)
        coEvery { conversationRepository.syncConversations() } returns com.github.michaelbull.result.Ok(Unit)
        observeConversationRepository(conversationRepository)
        val settings = mockk<UserSettingDataSource>()
        every { settings.user } returns MutableStateFlow(User(userId, "user", null, null, null, null, now, now, null))
        val coordinator = UnreadRefreshCoordinator()
        val repo = OfflineFirstMessageRepositoryImpl(
            decorate(dao), UserDaoImpl({ db }, coroutineContext), ParticipantDaoImpl({ db }, coroutineContext),
            api, rest, ConversationDaoImpl({ db }, coroutineContext), GroupJoinRequestDaoImpl({ db }, coroutineContext),
            conversationRepository, settings, backgroundScope,
            mediatorFactory = io.mockk.mockk(),
            groupProfileDao = GroupProfileDaoImpl({ db }, coroutineContext),
            unreadRefreshCoordinator = coordinator,
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
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.FAILED)!!)
            assertEquals(com.github.michaelbull.result.Err(com.github.woodsmarshes.chat.core.model.error.MessageError.PermissionDenied), repo.retryMessage(serverId))
            coVerify(exactly = 0) { api.send(any()) }
            assertEquals(MessageStatus.FAILED, dao.getMessageById(serverId).first()?.local_send_status)
        }
    }
    @Test
    fun concurrentManualAndAutomaticRetriesKeepIdentityAndReply() = runTest {
        fixture { repo, dao, api, _, _ ->
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.SENT)!!)
            dao.insertMessage(message(nextId).copy(replyTo = message(serverId)).toMessageEntity(MessageStatus.FAILED)!!)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { api.send(any()) } coAnswers {
                val request = firstArg<RealtimeEvent>() as MessageRequest.Send
                assertEquals(nextId.toString(), request.requestId)
                assertEquals(serverId, request.replyToMessageId)
                entered.complete(Unit)
                release.await()
                true
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
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.FAILED)!!)
            dao.insertMessage(message(nextId).toMessageEntity(MessageStatus.SENT)!!)
            assertEquals(com.github.michaelbull.result.Ok(Unit), repo.retryMessage(serverId))
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
                true
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
            assertEquals(nextId, dao.getSyncCursor(conversationId)?.after_id?.let(Uuid::parse))
        }
    }

    @Test
    fun firstMessageOfSessionEstablishesBaselineWithoutGapRepair() = runTest {
        fixture { _, dao, _, events, rest ->
            // The reconnect sync at startSession() has already run; forget its
            // recorded calls so only repair-triggered fetches count below.
            clearMocks(rest, answers = false, recordedCalls = true)

            // First live message of the session: establishes the baseline only.
            events.emit(MessageEventResponse.Received(message(serverId, seq = 10), conversationId, userId, ""))
            runCurrent()
            coVerify(exactly = 0) { rest.syncMessages(any(), any(), any()) }
            assertNotNull(dao.getMessageById(serverId).first())

            // A genuine jump (10 -> 12) still repairs exactly once.
            events.emit(MessageEventResponse.Received(message(nextId, seq = 12), conversationId, userId, ""))
            runCurrent()
            coVerify(exactly = 1) { rest.syncMessages(conversationId, any(), any()) }
            assertNotNull(dao.getMessageById(nextId).first())
        }
    }

    @Test
    fun anchorLargeGapJumpsWindowAndSeedsReplyTargetOutsideTimeline() = runTest {
        val old1 = Uuid.parse("00000000-0000-0000-0000-000000000031")
        val old2 = Uuid.parse("00000000-0000-0000-0000-000000000032")
        val new1 = Uuid.parse("00000000-0000-0000-0000-000000000041")
        val new2 = Uuid.parse("00000000-0000-0000-0000-000000000042")
        val ancient = Uuid.parse("00000000-0000-0000-0000-000000000030")
        fixture { repo, dao, _, events, rest ->
            // Legacy local tail: two confirmed messages, cursor parked at old2/seq 2.
            dao.insertMessage(message(old1, seq = 1).toMessageEntity(MessageStatus.SENT)!!)
            dao.insertMessage(message(old2, seq = 2).toMessageEntity(MessageStatus.SENT)!!)
            dao.setSyncCursor(conversationId, old2, 2L)

            // Server window: two fresh messages; the newest replies to an
            // ancient message the client has never cached.
            val freshReply = message(new2, seq = 602).copy(
                replyTo = message(ancient, seq = 5).copy(
                    sender = SimpleUser(userId, "user", null, null, now, now, null, UserRole.MEMBER)
                )
            )
            coEvery {
                rest.getMessages(conversationId, 100, null)
            } returns listOf(message(new1, seq = 601), freshReply)
            coEvery { rest.syncMessages(any(), any(), any()) } returns emptyList()

            events.emit(
                SyncStateResponse.Anchor(
                    entries = listOf(
                        SyncAnchorEntry(
                            conversationId = conversationId,
                            lastSeq = 602L,
                            unreadCount = 2,
                            lastMessageId = new2,
                        )
                    ),
                    timestamp = now,
                )
            )
            runCurrent()

            // Diagnose: the window fetch must have been issued exactly once.
            coVerify(exactly = 1) { rest.getMessages(conversationId, any(), any()) }

            // Window jump: confirmed history outside the window is DEMOTED
            // (rows survive for reply-preview joins, but leave the timeline).
            assertEquals(false, dao.getMessageById(old1).first()?.in_timeline)
            assertEquals(false, dao.getMessageById(old2).first()?.in_timeline)
            // …the fresh window is on disk with cursor at the anchor…
            assertNotNull(dao.getMessageById(new1).first())
            assertNotNull(dao.getMessageById(new2).first())
            assertEquals(true, dao.getMessageById(new1).first()?.in_timeline)
            assertEquals(602L, dao.getSyncCursor(conversationId)?.after_seq)

            // …and the reply target is present but OUTSIDE the timeline: it
            // must not appear in the paging window (no stray Paging anchor)
            // while staying available for the reply preview join.
            assertNotNull(dao.getMessageById(ancient).first())
            val timelineIds = dao.getMessagesWithRelationsByPage(conversationId, null, 100)
                .first()
                .map { it.id }
            assertEquals(listOf(new2, new1), timelineIds)
            assertTrue(ancient !in timelineIds)
        }
    }

    @Test
    fun anchorSmallGapFillsForwardWithoutWindowJump() = runTest {
        fixture { repo, dao, _, events, rest ->
            dao.insertMessage(message(old2, seq = 2).toMessageEntity(MessageStatus.SENT)!!)
            dao.setSyncCursor(conversationId, old2, 2L)

            coEvery {
                rest.syncMessages(conversationId, old2, any())
            } returns listOf(message(new1, seq = 3), message(new2, seq = 4))
            coEvery { rest.syncMessages(conversationId, new2, any()) } returns emptyList()

            events.emit(
                SyncStateResponse.Anchor(
                    entries = listOf(
                        SyncAnchorEntry(
                            conversationId = conversationId,
                            lastSeq = 4L,
                            unreadCount = 2,
                            lastMessageId = new2,
                        )
                    ),
                    timestamp = now,
                )
            )
            runCurrent()

            // Small gap: forward delta fills the cursor position up to the
            // anchor; nothing is deleted and the cursor lands on the anchor.
            assertNotNull(dao.getMessageById(new1).first())
            assertNotNull(dao.getMessageById(new2).first())
            assertEquals(4L, dao.getSyncCursor(conversationId)?.after_seq)
        }
    }

    @Test
    fun anchorColdConversationWithNoUnreadSkipsHistoryPrefetch() = runTest {
        fixture { repo, dao, _, events, rest ->
            // Fresh login, conversation never opened here: no cursor, zero
            // unread. The anchor must jump the cursor without fetching any
            // history — the list preview is already seeded by the list sync,
            // and opening the chat pulls the newest page via REFRESH.
            events.emit(
                SyncStateResponse.Anchor(
                    entries = listOf(
                        SyncAnchorEntry(
                            conversationId = conversationId,
                            lastSeq = 7L,
                            unreadCount = 0,
                            lastMessageId = coldNewest,
                        )
                    ),
                    timestamp = now,
                )
            )
            runCurrent()

            assertEquals(coldNewest, dao.getSyncCursor(conversationId)?.after_id?.let(Uuid::parse))
            assertEquals(7L, dao.getSyncCursor(conversationId)?.after_seq)
            // Zero history prefetch: the window fetch never fired.
            coVerify(exactly = 0) { rest.getMessages(any(), any(), any()) }
            coVerify(exactly = 0) { rest.syncMessages(any(), any(), any()) }
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

            // Content catch-up is anchor-driven now: a bare startSession() must
            // NOT sweep; only the anchor event (or explicit retry) triggers it.
            coVerify(exactly = 0) { rest.syncMessages(any(), any(), any()) }
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
    fun stopSessionStopsPeriodicOutboxRetryAndConsumerUntilRestarted() = runTest {
        val connectionStates = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        fixture { repo, dao, api, events, rest ->
            every { api.connectionState } returns connectionStates
            repo.stopSession()
            runCurrent()
            clearMocks(rest, answers = false, recordedCalls = true)

            // Flip connectionState while stopped: neither the outbox retry nor
            // the safety-net sweep nor event consumption may run.
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
            // Content catch-up is anchor-driven: a bare startSession() must
            // not sweep. The restarted session only consumes live events.
            coVerify(exactly = 0) { rest.syncMessages(any(), any(), any()) }

            events.emit(MessageEventResponse.Received(message(serverId, seq = 1), conversationId, userId, ""))
            runCurrent()
            assertNotNull(dao.getMessageById(serverId).first(), "restarted session consumes events again")

            // The safety-net sweep only covers conversations WITH a cursor
            // (first fill is anchor-owned), so establish one before driving
            // the virtual clock past SYNC_NET_TICKS × RETRY_POLL_INTERVAL.
            dao.setSyncCursor(conversationId, serverId, 1L)
            coEvery { rest.syncMessages(conversationId, any(), any()) } returns emptyList()
            clearMocks(rest, answers = false, recordedCalls = true)
            advanceTimeBy(620_000L)
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
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.FAILED)!!)

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
    @Test
    fun newIncomingMessageIncrementsUnreadExactlyOnce() = runTest {
        lateinit var conversations: ConversationDaoImpl
        fixture(observeConversations = { conversations = it }) { _, _, _, events, rest ->
            coEvery { rest.getDetail(conversationId) } returns detailResponse(conversationId, unreadCount = 1)
            val peer = sender.copy(id = ancient, username = "peer")
            val incoming = message(serverId, seq = 1).copy(sender = peer)
            repeat(2) {
                events.emit(MessageEventResponse.Received(incoming, conversationId, peer.id, ""))
                runCurrent()
            }
            assertEquals(1L, conversations.getConversationById(conversationId).first()?.unread_count)
        }
    }

    @Test
    fun slowWithdrawnDetailRefreshDoesNotBlockSubsequentReceivedPersistence() = runTest {
        lateinit var conversations: ConversationDaoImpl
        fixture(observeConversations = { conversations = it }) { _, dao, _, events, rest ->
            dao.insertMessage(message(serverId, seq = 1).toMessageEntity(MessageStatus.SENT)!!)
            val detailEntered = CompletableDeferred<Unit>()
            val releaseDetail = CompletableDeferred<Unit>()
            var detailCalls = 0
            coEvery { rest.getDetail(conversationId) } coAnswers {
                detailCalls++
                if (detailCalls == 1) {
                    detailEntered.complete(Unit)
                    releaseDetail.await()
                    detailResponse(conversationId, unreadCount = 0)
                } else {
                    detailResponse(conversationId, unreadCount = 1)
                }
            }

            events.emit(MessageEventResponse.Withdrawn(serverId, conversationId, ancient, now))
            runCurrent()
            detailEntered.await()

            val peer = sender.copy(id = ancient, username = "peer")
            val incoming = message(nextId, seq = 2).copy(sender = peer)
            events.emit(MessageEventResponse.Received(incoming, conversationId, peer.id, ""))
            runCurrent()

            assertNotNull(
                dao.getMessageById(nextId).first(),
                "a slow getDetail during Withdrawn must not hold syncMutex or block Received persistence",
            )

            releaseDetail.complete(Unit)
            runCurrent()
            assertEquals(1L, conversations.getConversationById(conversationId).first()?.unread_count)
        }
    }

    @Test
    fun consecutiveAndDuplicateWithdrawsCoalesceDetailRefreshAndReFetchWhenNewEventArrivesInFlight() = runTest {
        lateinit var conversations: ConversationDaoImpl
        fixture(observeConversations = { conversations = it }) { _, dao, _, events, rest ->
            dao.insertMessage(message(serverId, seq = 1).toMessageEntity(MessageStatus.SENT)!!)
            dao.insertMessage(message(nextId, seq = 2).toMessageEntity(MessageStatus.SENT)!!)

            val firstFetchEntered = CompletableDeferred<Unit>()
            val releaseFirstFetch = CompletableDeferred<Unit>()
            var fetchCount = 0
            coEvery { rest.getDetail(conversationId) } coAnswers {
                fetchCount++
                if (fetchCount == 1) {
                    firstFetchEntered.complete(Unit)
                    releaseFirstFetch.await()
                    detailResponse(conversationId, unreadCount = 2)
                } else {
                    detailResponse(conversationId, unreadCount = 0)
                }
            }

            events.emit(MessageEventResponse.Withdrawn(serverId, conversationId, ancient, now))
            runCurrent()
            firstFetchEntered.await()

            // Duplicate withdraw of serverId + consecutive withdraw of nextId while the first detail fetch is in flight.
            events.emit(MessageEventResponse.Withdrawn(serverId, conversationId, ancient, now))
            events.emit(MessageEventResponse.Withdrawn(nextId, conversationId, ancient, now))
            runCurrent()

            releaseFirstFetch.complete(Unit)
            runCurrent()

            assertEquals(2, fetchCount, "in-flight withdraw burst must coalesce into exactly one follow-up fetch")
            assertEquals(0L, conversations.getConversationById(conversationId).first()?.unread_count)
        }
    }

    @Test
    fun seededPreviewOrReplyTargetDoesNotSuppressAuthoritativeUnreadOnLiveReceived() = runTest {
        lateinit var conversations: ConversationDaoImpl
        fixture(observeConversations = { conversations = it }) { _, dao, _, events, rest ->
            val peer = sender.copy(id = ancient, username = "peer")
            val incoming = message(serverId, seq = 1).copy(sender = peer)
            // Pre-seed the message row as a preview/reply target (in_timeline = false) before the live Received arrives.
            dao.seedMessage(incoming.toMessageEntity(MessageStatus.SENT, inTimeline = false)!!)
            coEvery { rest.getDetail(conversationId) } returns detailResponse(conversationId, unreadCount = 1)

            events.emit(MessageEventResponse.Received(incoming, conversationId, peer.id, ""))
            runCurrent()

            assertEquals(true, dao.getMessageById(serverId).first()?.in_timeline)
            assertEquals(
                1L,
                conversations.getConversationById(conversationId).first()?.unread_count,
                "a pre-seeded preview/reply row must not suppress the authoritative unread refresh",
            )
        }
    }

    @Test
    fun staleDetailOrListSnapshotDoesNotClobberNewerMarkAsReadAndFailedSendDoesNotZeroBadge() = runTest {
        lateinit var conversations: ConversationDaoImpl
        fixture(observeConversations = { conversations = it }) { repo, _, api, events, rest ->
            conversations.setUnreadCount(conversationId, 3L)

            // 1. If messageApi.send(Read) returns false, local unread count must NOT be changed or refreshed.
            coEvery { api.send(any()) } returns false
            repo.markAsRead(conversationId, serverId)
            runCurrent()
            assertEquals(3L, conversations.getConversationById(conversationId).first()?.unread_count)

            // 2. Start an in-flight detail refresh triggered by an incoming message (returning stale unread = 5),
            // then call markAsRead(send = true) before it finishes. The coordinator must reject the stale response
            // and re-fetch the authoritative remaining unread count (e.g. 2 for a partial read) even if the server
            // suppresses a duplicate Read broadcast (UNCHANGED).
            coEvery { api.send(any()) } returns true
            val detailStarted = CompletableDeferred<Unit>()
            val releaseStaleDetail = CompletableDeferred<Unit>()
            var detailCalls = 0
            coEvery { rest.getDetail(conversationId) } coAnswers {
                detailCalls++
                if (detailCalls == 1) {
                    detailStarted.complete(Unit)
                    releaseStaleDetail.await()
                    detailResponse(conversationId, unreadCount = 5)
                } else {
                    detailResponse(conversationId, unreadCount = 2)
                }
            }

            val peer = sender.copy(id = ancient, username = "peer")
            events.emit(MessageEventResponse.Received(message(serverId, seq = 1).copy(sender = peer), conversationId, peer.id, ""))
            runCurrent()
            detailStarted.await()

            repo.markAsRead(conversationId, serverId)
            runCurrent()
            // While the first detail request is still in flight, markAsRead must NOT blindly zero the local badge.
            assertEquals(3L, conversations.getConversationById(conversationId).first()?.unread_count)

            // Releasing the stale first detail response must trigger a follow-up fetch that applies the authoritative remaining unread (2).
            releaseStaleDetail.complete(Unit)
            runCurrent()
            assertEquals(2, detailCalls)
            assertEquals(2L, conversations.getConversationById(conversationId).first()?.unread_count)

            // 3. When the server later broadcasts Read, it invalidates again and refreshes to the post-commit count (1).
            coEvery { rest.getDetail(conversationId) } returns detailResponse(conversationId, unreadCount = 1)
            events.emit(MessageEventResponse.Read(serverId, conversationId, userId, now, unreadCount = 0))
            runCurrent()
            assertEquals(1L, conversations.getConversationById(conversationId).first()?.unread_count)
        }
    }

    @Test
    fun staleConversationListSnapshotDoesNotOverwriteNewerReadOrIncomingRefresh() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dao = MessageDaoImpl({ db }, coroutineContext)
        val conversationDao = ConversationDaoImpl({ db }, coroutineContext)
        val userDao = UserDaoImpl({ db }, coroutineContext)
        val participantDao = ParticipantDaoImpl({ db }, coroutineContext)
        val groupProfileDao = GroupProfileDaoImpl({ db }, coroutineContext)
        val groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, coroutineContext)
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.usersQueries.upsertUser(UserEntity(ancient, "peer", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 4))

        val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
        val api = mockk<RealtimeApi>()
        every { api.events } returns events
        every { api.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { api.send(any()) } returns true

        val rest = mockk<ConversationApi>()
        coEvery { rest.syncMessages(any(), any(), any()) } returns emptyList()
        coEvery { rest.getDetail(conversationId) } returns detailResponse(conversationId, unreadCount = 1)

        val listEntered = CompletableDeferred<Unit>()
        val releaseList = CompletableDeferred<Unit>()
        val userApi = mockk<UserApi>()
        coEvery { userApi.getMyConversations() } coAnswers {
            listEntered.complete(Unit)
            releaseList.await()
            listOf(detailResponse(conversationId, unreadCount = 9))
        }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { db }, { 1L })
        val holder = mockk<DatabaseHolder>()
        every { holder.sessionGate } returns gate
        val settings = mockk<UserSettingDataSource>()
        every { settings.user } returns MutableStateFlow(User(userId, "user", null, null, null, null, now, now, null))

        val coordinator = UnreadRefreshCoordinator()
        val conversationRepo = ConversationRepositoryImpl(
            groupProfileDao = groupProfileDao,
            conversationDao = conversationDao,
            participantDao = participantDao,
            messageDao = dao,
            groupJoinRequestDao = groupJoinRequestDao,
            userDao = userDao,
            conversationApi = rest,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            databaseHolder = holder,
            userSettingDataSource = settings,
            unreadRefreshCoordinator = coordinator,
        )
        val repo = OfflineFirstMessageRepositoryImpl(
            dao, userDao, participantDao, api, rest, conversationDao, groupJoinRequestDao,
            conversationRepo, settings, backgroundScope,
            mediatorFactory = io.mockk.mockk(),
            groupProfileDao = groupProfileDao,
            unreadRefreshCoordinator = coordinator,
        )
        try {
            gate.start()
            repo.startSession()
            val listSyncJob = launch { conversationRepo.syncConversations() }
            runCurrent()
            listEntered.await()

            // While getMyConversations() is in flight with stale unreadCount = 9, the user reads partially (remaining authoritative unread = 1).
            repo.markAsRead(conversationId, serverId)
            runCurrent()
            assertEquals(1L, conversationDao.getConversationById(conversationId).first()?.unread_count)

            releaseList.complete(Unit)
            listSyncJob.join()
            runCurrent()

            assertEquals(
                1L,
                conversationDao.getConversationById(conversationId).first()?.unread_count,
                "a stale list snapshot captured before markAsRead must not clobber the newer authoritative count",
            )

            // Also verify cross-session fence: a list token captured before session stop is rejected after restart.
            val crossSessionToken = coordinator.captureListGeneration()
            repo.stopSession()
            repo.startSession()
            coordinator.applyListSnapshot(
                token = crossSessionToken,
                entries = listOf(conversationId to 7L),
                conversationDao = conversationDao,
            )
            assertEquals(
                1L,
                conversationDao.getConversationById(conversationId).first()?.unread_count,
                "a list snapshot token from a previous session must be rejected after clearSessionState",
            )
        } finally {
            repo.stopSession()
            gate.stop()
            runCurrent()
            driver.close()
        }
    }

    @Test
    fun outOfOrderConversationListSnapshotsAndDetailThenStaleListNeverRegressUnreadCount() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dao = MessageDaoImpl({ db }, coroutineContext)
        val conversationDao = ConversationDaoImpl({ db }, coroutineContext)
        val userDao = UserDaoImpl({ db }, coroutineContext)
        val participantDao = ParticipantDaoImpl({ db }, coroutineContext)
        val groupProfileDao = GroupProfileDaoImpl({ db }, coroutineContext)
        val groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, coroutineContext)
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.usersQueries.upsertUser(UserEntity(ancient, "peer", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 0))

        val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
        val api = mockk<RealtimeApi>()
        every { api.events } returns events
        every { api.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { api.send(any()) } returns true

        val rest = mockk<ConversationApi>()
        coEvery { rest.syncMessages(any(), any(), any()) } returns emptyList()
        coEvery { rest.getDetail(conversationId) } returns detailResponse(conversationId, unreadCount = 2)

        val firstListEntered = CompletableDeferred<Unit>()
        val releaseFirstList = CompletableDeferred<Unit>()
        val secondListEntered = CompletableDeferred<Unit>()
        val releaseSecondList = CompletableDeferred<Unit>()
        val thirdListEntered = CompletableDeferred<Unit>()
        val releaseThirdList = CompletableDeferred<Unit>()
        var listCallCount = 0
        val userApi = mockk<UserApi>()
        coEvery { userApi.getMyConversations() } coAnswers {
            when (++listCallCount) {
                1 -> {
                    firstListEntered.complete(Unit)
                    releaseFirstList.await()
                    listOf(detailResponse(conversationId, unreadCount = 8))
                }
                2 -> {
                    secondListEntered.complete(Unit)
                    releaseSecondList.await()
                    listOf(detailResponse(conversationId, unreadCount = 3))
                }
                3 -> {
                    // Failed list request must not falsely advance applied generation.
                    throw RuntimeException("transient list failure")
                }
                4 -> {
                    thirdListEntered.complete(Unit)
                    releaseThirdList.await()
                    listOf(detailResponse(conversationId, unreadCount = 6))
                }
                else -> listOf(detailResponse(conversationId, unreadCount = 5))
            }
        }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { db }, { 1L })
        val holder = mockk<DatabaseHolder>()
        every { holder.sessionGate } returns gate
        val settings = mockk<UserSettingDataSource>()
        every { settings.user } returns MutableStateFlow(User(userId, "user", null, null, null, null, now, now, null))

        val coordinator = UnreadRefreshCoordinator()
        val conversationRepo = ConversationRepositoryImpl(
            groupProfileDao = groupProfileDao,
            conversationDao = conversationDao,
            participantDao = participantDao,
            messageDao = dao,
            groupJoinRequestDao = groupJoinRequestDao,
            userDao = userDao,
            conversationApi = rest,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            databaseHolder = holder,
            userSettingDataSource = settings,
            unreadRefreshCoordinator = coordinator,
        )
        val repo = OfflineFirstMessageRepositoryImpl(
            dao, userDao, participantDao, api, rest, conversationDao, groupJoinRequestDao,
            conversationRepo, settings, backgroundScope,
            mediatorFactory = io.mockk.mockk(),
            groupProfileDao = groupProfileDao,
            unreadRefreshCoordinator = coordinator,
        )
        try {
            gate.start()
            repo.startSession()

            // 1. Two list syncs in the same session respond out of order: list #1 starts first (unread = 8),
            // list #2 starts second (unread = 3) and finishes first. List #1 arriving later must be rejected.
            val listJob1 = launch { conversationRepo.syncConversations() }
            runCurrent()
            firstListEntered.await()

            val listJob2 = launch { conversationRepo.syncConversations() }
            runCurrent()
            secondListEntered.await()

            releaseSecondList.complete(Unit)
            listJob2.join()
            runCurrent()
            assertEquals(3L, conversationDao.getConversationById(conversationId).first()?.unread_count)

            releaseFirstList.complete(Unit)
            listJob1.join()
            runCurrent()
            assertEquals(
                3L,
                conversationDao.getConversationById(conversationId).first()?.unread_count,
                "an older list response (8) completing after a newer list response (3) must be rejected",
            )

            // 2. A failed list request (#3) must not falsely advance applied generation.
            val failedRes = conversationRepo.syncConversations()
            assertTrue(failedRes.isErr)
            assertEquals(3L, conversationDao.getConversationById(conversationId).first()?.unread_count)

            // 3. List #4 starts before a detail refresh completes; after the detail refresh applies (unread = 2),
            // the older list #4 (unread = 6) arriving later must not overwrite the detail result.
            val listJob4 = launch { conversationRepo.syncConversations() }
            runCurrent()
            thirdListEntered.await()

            repo.markAsRead(conversationId, serverId)
            runCurrent()
            assertEquals(2L, conversationDao.getConversationById(conversationId).first()?.unread_count)

            releaseThirdList.complete(Unit)
            listJob4.join()
            runCurrent()
            assertEquals(
                2L,
                conversationDao.getConversationById(conversationId).first()?.unread_count,
                "a list snapshot started before a detail refresh applied must not overwrite the detail count",
            )

            // 4. A subsequent fresh list snapshot (#5) started after the detail refresh succeeds normally.
            val freshRes = conversationRepo.syncConversations()
            assertTrue(freshRes.isOk)
            assertEquals(5L, conversationDao.getConversationById(conversationId).first()?.unread_count)
        } finally {
            repo.stopSession()
            gate.stop()
            runCurrent()
            driver.close()
        }
    }

    @Test
    fun anchorListSyncErrorIsRecordedAndRetriedWithoutBlindlyApplyingStaleAnchorUnread() = runTest {
        lateinit var conversations: ConversationDaoImpl
        lateinit var convRepo: ConversationRepository
        fixture(
            observeConversations = { conversations = it },
            observeConversationRepository = { convRepo = it },
        ) { _, dao, _, events, _ ->
            conversations.setUnreadCount(conversationId, 0L)
            var syncAttempts = 0
            coEvery { convRepo.syncConversations() } coAnswers {
                syncAttempts++
                if (syncAttempts == 1) {
                    com.github.michaelbull.result.Err(ConversationError.Unknown("network down"))
                } else {
                    conversations.setUnreadCount(conversationId, 4L)
                    com.github.michaelbull.result.Ok(Unit)
                }
            }

            // Emit an anchor with unreadCount = 7 while syncConversations() fails on the first attempt.
            events.emit(
                SyncStateResponse.Anchor(
                    entries = listOf(SyncAnchorEntry(conversationId, lastSeq = 1L, unreadCount = 7, lastMessageId = serverId)),
                    timestamp = now,
                )
            )
            runCurrent()

            assertEquals(1, syncAttempts)
            assertEquals(
                0L,
                conversations.getConversationById(conversationId).first()?.unread_count,
                "anchor processing must not blindly apply anchor.unreadCount when syncConversations fails",
            )

            // Next retry tick retries the failed list sync and converges to the authoritative list count.
            advanceTimeBy(16_000L)
            runCurrent()
            assertEquals(2, syncAttempts)
            assertEquals(4L, conversations.getConversationById(conversationId).first()?.unread_count)
        }
    }

    @Test
    fun revokeReportsOnlyActualRowChanges() = runTest {
        fixture { _, dao, _, _, _ ->
            assertFalse(dao.revokeMessage(serverId, now))
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.SENT)!!)
            assertTrue(dao.revokeMessage(serverId, now))
            assertFalse(dao.revokeMessage(serverId, now))
        }
    }

    @Test
    fun demotionPreservesFailedReplyAndExcludesOldTimeline() = runTest {
        fixture { _, dao, _, _, _ ->
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.SENT)!!)
            dao.insertMessage(message(nextId).copy(replyTo = message(serverId)).toMessageEntity(MessageStatus.FAILED)!!)
            dao.demoteTimelineBefore(conversationId, new1)
            assertEquals(false, dao.getMessageById(serverId).first()?.in_timeline)
            val failed = dao.getMessageById(nextId).first()
            assertEquals(serverId, failed?.reply_to_message_id)
            assertEquals(true, failed?.in_timeline)
        }
    }

    @Test
    fun firstLiveMessageCannotSkipDurableBacklog() = runTest {
        fixture { _, dao, _, events, _ ->
            dao.setSyncCursor(conversationId, serverId, 1L)
            events.emit(MessageEventResponse.Received(message(nextId, seq = 50), conversationId, userId, ""))
            runCurrent()
            assertEquals(1L, dao.getSyncCursor(conversationId)?.after_seq)
        }
    }

    @Test
    fun delayedWindowCannotRegressNewerCursor() = runTest {
        fixture { _, dao, _, events, rest ->
            dao.setSyncCursor(conversationId, serverId, 1L)
            val release = CompletableDeferred<Unit>()
            coEvery { rest.getMessages(conversationId, 100, null) } coAnswers {
                release.await()
                listOf(message(new1, seq = 500))
            }
            events.emit(SyncStateResponse.Anchor(listOf(SyncAnchorEntry(conversationId, 500, 1, new1)), now))
            runCurrent()
            dao.insertMessage(message(new2, seq = 501).toMessageEntity(MessageStatus.SENT)!!)
            dao.setSyncCursor(conversationId, new2, 501L)
            release.complete(Unit)
            runCurrent()
            assertEquals(501L, dao.getSyncCursor(conversationId)?.after_seq)
            assertEquals(true, dao.getMessageById(new2).first()?.in_timeline)
        }
    }
    @Test
    fun liveMessageDuringWindowFetchIsReconciledBeforeNextDelivery() = runTest {
        fixture { _, dao, _, events, rest ->
            dao.setSyncCursor(conversationId, serverId, 1L)
            val release = CompletableDeferred<Unit>()
            coEvery { rest.getMessages(conversationId, 100, null) } coAnswers {
                release.await()
                listOf(message(new1, seq = 500))
            }
            coEvery { rest.syncMessages(conversationId, new1, any()) } returns listOf(message(new2, seq = 501))
            events.emit(SyncStateResponse.Anchor(listOf(SyncAnchorEntry(conversationId, 500, 1, new1)), now))
            runCurrent()
            events.emit(MessageEventResponse.Received(message(new2, seq = 501), conversationId, userId, ""))
            runCurrent()
            release.complete(Unit)
            runCurrent()
            assertEquals(501L, dao.getSyncCursor(conversationId)?.after_seq)
            assertEquals(new2.toString(), dao.getSyncCursor(conversationId)?.after_id)
            val last = Uuid.parse("00000000-0000-0000-0000-000000000043")
            events.emit(MessageEventResponse.Received(message(last, seq = 502), conversationId, userId, ""))
            runCurrent()
            assertEquals(502L, dao.getSyncCursor(conversationId)?.after_seq)
        }
    }

    @Test
    fun windowNewerThanAnchorKeepsMatchingIdAndSequence() = runTest {
        fixture { _, dao, _, events, rest ->
            dao.setSyncCursor(conversationId, serverId, 1L)
            coEvery { rest.getMessages(conversationId, 100, null) } returns
                listOf(message(new2, seq = 501), message(new1, seq = 500))
            events.emit(SyncStateResponse.Anchor(listOf(SyncAnchorEntry(conversationId, 500, 1, new1)), now))
            runCurrent()
            assertEquals(501L, dao.getSyncCursor(conversationId)?.after_seq)
            assertEquals(new2.toString(), dao.getSyncCursor(conversationId)?.after_id)
        }
    }
}
