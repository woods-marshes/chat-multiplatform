package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.common.AppDispatchers
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.paging.MessageRemoteMediator
import com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.UserEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
class MessageRemoteMediatorTest {
    private val ownUserId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val peerUserId = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000003")

    private val seedId = Uuid.parse("00000000-0000-0000-0000-000000000005")
    private val localSendingOldestId = Uuid.parse("00000000-0000-0000-0000-000000000008")
    private val localFailedOldestId = Uuid.parse("00000000-0000-0000-0000-000000000009")
    private val olderPageMsg1 = Uuid.parse("00000000-0000-0000-0000-000000000011")
    private val olderPageMsg2 = Uuid.parse("00000000-0000-0000-0000-000000000012")
    private val confirmedOldestId = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val confirmedNewestId = Uuid.parse("00000000-0000-0000-0000-000000000025")
    private val windowJumpOldestId = Uuid.parse("00000000-0000-0000-0000-000000000090")

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val peerSender = SimpleUser(peerUserId, "peer", null, null, now, now, null, UserRole.MEMBER)

    private fun serverMessage(id: Uuid, replyTo: Message? = null) = Message(
        id = id,
        conversationId = conversationId,
        sender = peerSender,
        category = MessageCategory.NORMAL,
        createdAt = now,
        content = TextContent("msg-$id"),
        replyTo = replyTo,
    )

    private suspend fun TestScope.fixture(
        isGroup: Boolean = false,
        block: suspend (
            MessageRemoteMediator,
            MessageDaoImpl,
            ConversationApi,
            ManagedDatabaseSessionGate,
            ChatDatabase,
        ) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { put("foreign_keys", "true") },
        )
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { db }, { 1L })
        val holder = mockk<DatabaseHolder>()
        every { holder.sessionGate } returns gate
        val dispatchers = mockk<AppDispatchers>()
        every { dispatchers.io } returns dispatcher
        val api = mockk<ConversationApi>()

        db.usersQueries.upsertUser(
            UserEntity(ownUserId, "me", null, null, null, null, now, now, null, UserRole.MEMBER),
        )
        db.usersQueries.upsertUser(
            UserEntity(peerUserId, "peer", null, null, null, null, now, now, null, UserRole.MEMBER),
        )
        db.conversationsQueries.upsertConversation(
            ConversationEntity(
                id = conversationId,
                type = if (isGroup) ConversationType.GROUP else ConversationType.PRIVATE,
                last_message_id = null,
                metadata = null,
                created_at = now,
                updated_at = now,
                deleted_at = null,
                unread_count = 0,
            ),
        )

        val messageDao = MessageDaoImpl({ db }, dispatcher, gate)
        val userDao = UserDaoImpl({ db }, dispatcher, gate)
        val participantDao = ParticipantDaoImpl({ db }, dispatcher, gate)

        gate.start()
        val mediator = MessageRemoteMediator(
            ownUserId = ownUserId,
            conversationId = conversationId,
            isGroup = isGroup,
            appDispatchers = dispatchers,
            databaseHolder = holder,
            conversationApi = api,
            messageDao = messageDao,
            userDao = userDao,
            participantDao = participantDao,
        )
        try {
            block(mediator, messageDao, api, gate, db)
        } finally {
            gate.stop()
            driver.close()
        }
    }

    private suspend fun pagingStateFromDb(
        dao: MessageDaoImpl,
        pageSize: Int = 20,
    ): PagingState<Uuid, KeyedMessagesWithRelations> {
        val source = dao.pinForPaging().pagingSource(conversationId, pageSize.toLong())
        val loaded = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = pageSize,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page
        return PagingState(
            pages = listOf(loaded),
            anchorPosition = loaded.data.lastIndex.takeIf { it >= 0 },
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            leadingPlaceholderCount = 0,
        )
    }

    @Test
    fun appendUsesOldestConfirmedTimelineMessageAndIgnoresOutboxAndHiddenSeeds() = runTest {
        fixture { mediator, dao, api, _, _ ->
            // Hidden seed row (older than everything, in_timeline = false) must be ignored.
            dao.seedMessage(serverMessage(seedId).toMessageEntity(MessageStatus.SENT, inTimeline = false)!!)
            // Confirmed timeline rows.
            dao.insertMessage(serverMessage(confirmedNewestId).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)
            dao.insertMessage(serverMessage(confirmedOldestId).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)
            // Unacked outbox rows with smaller client UUIDs at the tail of PagingState.
            dao.insertMessage(serverMessage(localFailedOldestId).toMessageEntity(MessageStatus.FAILED, inTimeline = true)!!)
            dao.insertMessage(serverMessage(localSendingOldestId).toMessageEntity(MessageStatus.SENDING, inTimeline = true)!!)

            val state = pagingStateFromDb(dao, pageSize = 20)
            // Verify PagingState tail actually holds the temporary SENDING row.
            assertEquals(localSendingOldestId, state.lastItemOrNull()?.id)

            coEvery {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = confirmedOldestId)
            } returns listOf(serverMessage(olderPageMsg2), serverMessage(olderPageMsg1))

            val result = mediator.load(LoadType.APPEND, state)
            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertTrue(result.endOfPaginationReached)

            // Must query using confirmedOldestId, NOT localSendingOldestId or seedId.
            coVerify(exactly = 1) {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = confirmedOldestId)
            }
            assertNotNull(dao.getMessageById(olderPageMsg1).first())
            assertNotNull(dao.getMessageById(olderPageMsg2).first())
        }
    }

    @Test
    fun appendWithOnlyOutboxRowsRequestsNewestPageWithNullBeforeId() = runTest {
        fixture { mediator, dao, api, _, _ ->
            dao.insertMessage(serverMessage(localSendingOldestId).toMessageEntity(MessageStatus.SENDING, inTimeline = true)!!)
            dao.insertMessage(serverMessage(localFailedOldestId).toMessageEntity(MessageStatus.FAILED, inTimeline = true)!!)
            dao.seedMessage(serverMessage(seedId).toMessageEntity(MessageStatus.SENT, inTimeline = false)!!)

            val state = pagingStateFromDb(dao, pageSize = 20)
            assertEquals(localSendingOldestId, state.lastItemOrNull()?.id)
            assertNull(dao.getOldestConfirmedTimelineMessageId(conversationId))

            coEvery {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = null)
            } returns listOf(serverMessage(confirmedNewestId), serverMessage(confirmedOldestId))

            val result = mediator.load(LoadType.APPEND, state)
            assertTrue(result is RemoteMediator.MediatorResult.Success)
            coVerify(exactly = 1) {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = null)
            }
            assertEquals(true, dao.getMessageById(confirmedOldestId).first()?.in_timeline)
        }
    }

    @Test
    fun staleAppendAfterWindowDemoteIsDiscardedAndDoesNotReactivateDemotedSegment() = runTest {
        fixture { mediator, dao, api, _, _ ->
            // Initial timeline has confirmedOldestId and olderPageMsg2 (already on disk).
            dao.insertMessage(serverMessage(confirmedOldestId).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)
            dao.insertMessage(serverMessage(olderPageMsg2).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)

            val state = pagingStateFromDb(dao, pageSize = 20)
            val requestStarted = CompletableDeferred<Unit>()
            val allowResponse = CompletableDeferred<Unit>()

            coEvery {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = olderPageMsg2)
            } coAnswers {
                requestStarted.complete(Unit)
                allowResponse.await()
                // Returns both an already-cached demoted row and a brand-new ancient row.
                listOf(serverMessage(olderPageMsg2), serverMessage(olderPageMsg1))
            }

            var appendResult: RemoteMediator.MediatorResult? = null
            val appendJob = launch { appendResult = mediator.load(LoadType.APPEND, state) }
            requestStarted.await()

            // While the HTTP request is in flight, a reconnect anchor jumps the window
            // and demotes everything before windowJumpOldestId (bumping window_generation).
            dao.demoteTimelineBefore(conversationId, windowJumpOldestId)
            dao.insertMessage(serverMessage(windowJumpOldestId).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)
            assertEquals(1L, dao.getWindowGeneration(conversationId))
            assertEquals(false, dao.getMessageById(confirmedOldestId).first()?.in_timeline)
            assertEquals(false, dao.getMessageById(olderPageMsg2).first()?.in_timeline)

            allowResponse.complete(Unit)
            runCurrent()
            appendJob.join()

            assertTrue(appendResult is RemoteMediator.MediatorResult.Error, "Stale APPEND must return MediatorResult.Error")
            assertNull(dao.getMessageById(olderPageMsg1).first(), "Stale APPEND response must not be inserted")
            assertEquals(false, dao.getMessageById(olderPageMsg2).first()?.in_timeline, "Demoted row must stay out of timeline")

            // A subsequent APPEND now reads generation = 1 and beforeId = windowJumpOldestId, succeeding cleanly.
            coEvery {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = windowJumpOldestId)
            } returns listOf(serverMessage(confirmedNewestId))

            val retryResult = mediator.load(LoadType.APPEND, pagingStateFromDb(dao, pageSize = 20))
            assertTrue(retryResult is RemoteMediator.MediatorResult.Success)
            assertEquals(true, dao.getMessageById(confirmedNewestId).first()?.in_timeline)
        }
    }

    @Test
    fun staleRefreshAndStaleEmptyAppendReturnErrorInsteadOfSuccess() = runTest {
        fixture { mediator, dao, api, _, _ ->
            dao.insertMessage(serverMessage(confirmedOldestId).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)
            val state = pagingStateFromDb(dao, pageSize = 20)

            // 1. REFRESH whose window is demoted while HTTP is in flight returns MediatorResult.Error.
            coEvery {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = null)
            } coAnswers {
                dao.demoteTimelineBefore(conversationId, windowJumpOldestId)
                listOf(serverMessage(olderPageMsg1))
            }

            val refreshResult = mediator.load(LoadType.REFRESH, state)
            assertTrue(refreshResult is RemoteMediator.MediatorResult.Error, "Stale REFRESH must return Error to allow retry")
            assertNull(dao.getMessageById(olderPageMsg1).first(), "Stale REFRESH must not write rows")

            // 2. Empty APPEND whose window is demoted while HTTP is in flight returns Error, not endOfPaginationReached=true.
            dao.insertMessage(serverMessage(windowJumpOldestId).toMessageEntity(MessageStatus.SENT, inTimeline = true)!!)
            coEvery {
                api.getMessages(conversationId = conversationId, limit = 20, beforeId = windowJumpOldestId)
            } coAnswers {
                dao.demoteTimelineBefore(conversationId, Uuid.parse("00000000-0000-0000-0000-000000000099"))
                emptyList()
            }

            val emptyAppendResult = mediator.load(LoadType.APPEND, state)
            assertTrue(
                emptyAppendResult is RemoteMediator.MediatorResult.Error,
                "Stale empty APPEND must return Error rather than falsely terminating pagination",
            )
        }
    }
}
