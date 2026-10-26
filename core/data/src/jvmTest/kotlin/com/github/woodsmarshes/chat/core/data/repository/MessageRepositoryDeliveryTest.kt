package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.dao.ConversationDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ContactDao
import com.github.woodsmarshes.chat.core.database.dao.ContactDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ContactRequestDaoImpl
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
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.conversation.UserInfo
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SyncBatch
import com.github.woodsmarshes.chat.core.network.dto.events.SyncChange
import com.github.woodsmarshes.chat.core.network.dto.events.SyncHead
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.core.network.dto.events.SyncSnapshot
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.UserEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private fun message(id: Uuid, requestId: Uuid? = null) = Message(
        id, conversationId, sender, MessageCategory.NORMAL, now,
        content = TextContent("hello"), clientRequestId = requestId,
    )

    private fun conversationHeads(revision: Long) = SyncStateResponse.Heads(
        entries = listOf(SyncHead(SyncScope.CONVERSATION, conversationId, revision)),
        timestamp = now,
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
        coEvery { rest.getSyncHeads() } returns SyncStateResponse.Heads(timestamp = now)
        coEvery { rest.getDetail(any()) } answers { detailResponse(id = firstArg(), unreadCount = 0) }
        val conversationRepository = mockk<ConversationRepository>(relaxed = true)
        coEvery { conversationRepository.syncConversations() } returns com.github.michaelbull.result.Ok(Unit)
        observeConversationRepository(conversationRepository)
        val settings = mockk<UserSettingDataSource>()
        every { settings.user } returns MutableStateFlow(User(userId, "user", null, null, null, null, now, now, null))
        val coordinator = UnreadRefreshCoordinator(dao)
        val repo = OfflineFirstMessageRepositoryImpl(
            decorate(dao), UserDaoImpl({ db }, coroutineContext), ParticipantDaoImpl({ db }, coroutineContext),
            api, rest, ConversationDaoImpl({ db }, coroutineContext), GroupJoinRequestDaoImpl({ db }, coroutineContext),
            conversationRepository, settings, backgroundScope,
            mediatorFactory = io.mockk.mockk(),
            groupProfileDao = GroupProfileDaoImpl({ db }, coroutineContext),
            unreadRefreshCoordinator = coordinator,
            userSyncApplier = mockk(relaxed = true),
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

    /**
     * Fixture wired with the REAL [UserSyncApplier] and REAL
     * [ConversationRepositoryImpl] over the same database, so USER-scope
     * snapshot/delta application is exercised end to end: the conversation
     * list apply, the contact/contact-request apply, and the scope cursor
     * either all commit together or all roll back together.
     */
    private suspend fun TestScope.userSyncFixture(
        userApi: UserApi = mockk(),
        contactDaoOverride: (ContactDao) -> ContactDao = { it },
        block: suspend (
            repo: OfflineFirstMessageRepositoryImpl,
            dao: MessageDaoImpl,
            events: MutableSharedFlow<RealtimeEvent>,
            rest: ConversationApi,
            contactApi: ContactApi,
            db: ChatDatabase,
        ) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        // A real session gate so ConversationRepositoryImpl's sessionOperation
        // paths (syncConversations calibration) execute against this database.
        val gate = ManagedDatabaseSessionGate(UnconfinedTestDispatcher(testScheduler), { db }, { 1L })
        try {
            db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
            db.usersQueries.upsertUser(UserEntity(ancient, "peer", null, null, null, null, now, now, null, UserRole.MEMBER))
            db.conversationsQueries.upsertConversation(ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 0))
            val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
            val api = mockk<RealtimeApi>()
            every { api.events } returns events
            every { api.connectionState } returns MutableStateFlow(ConnectionState.Connected)
            coEvery { api.send(any()) } returns true
            val rest = mockk<ConversationApi>()
            val contactApi = mockk<ContactApi>()
            val settings = mockk<UserSettingDataSource>()
            every { settings.user } returns MutableStateFlow(User(userId, "user", null, null, null, null, now, now, null))
            val dao = MessageDaoImpl({ db }, coroutineContext)
            val userDao = UserDaoImpl({ db }, coroutineContext)
            val participantDao = ParticipantDaoImpl({ db }, coroutineContext)
            val conversationDao = ConversationDaoImpl({ db }, coroutineContext)
            val groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, coroutineContext)
            val groupProfileDao = GroupProfileDaoImpl({ db }, coroutineContext)
            val contactDao = contactDaoOverride(ContactDaoImpl({ db }, coroutineContext))
            val contactRequestDao = ContactRequestDaoImpl({ db }, coroutineContext)
            gate.start()
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
                databaseHolder = mockk { every { sessionGate } returns gate },
                userSettingDataSource = settings,
                unreadRefreshCoordinator = UnreadRefreshCoordinator(dao),
            )
            val applier = UserSyncApplier(
                contactApi = contactApi,
                conversationApi = rest,
                contactDao = contactDao,
                contactRequestDao = contactRequestDao,
                userDao = userDao,
                groupJoinRequestDao = groupJoinRequestDao,
                participantDao = participantDao,
                conversationDao = conversationDao,
                groupProfileDao = groupProfileDao,
                messageDao = dao,
            )
            val repo = OfflineFirstMessageRepositoryImpl(
                dao, userDao, participantDao, api, rest, conversationDao, groupJoinRequestDao,
                conversationRepo, settings, backgroundScope,
                mediatorFactory = io.mockk.mockk(),
                groupProfileDao = groupProfileDao,
                unreadRefreshCoordinator = UnreadRefreshCoordinator(dao),
                userSyncApplier = applier,
            )
            try {
                block(repo, dao, events, rest, contactApi, db)
            } finally {
                repo.stopSession()
                runCurrent()
            }
        } finally {
            gate.stop()
            driver.close()
        }
    }

    private fun userHeads(revision: Long) = SyncStateResponse.Heads(
        entries = listOf(SyncHead(SyncScope.USER, userId, revision)),
        timestamp = now,
    )

    @Test
    fun userSnapshotRollsBackConversationApplyWhenAStageFailsAndAppliesAtomicallyOnRetry() = runTest {
        val snapshotConversationId = Uuid.parse("00000000-0000-0000-0000-000000000043")
        var failContactInsert = true
        userSyncFixture(
            contactDaoOverride = { delegate ->
                object : ContactDao by delegate {
                    override suspend fun insertContacts(contacts: List<ContactEntity>) {
                        if (failContactInsert) error("Injected contact write failure")
                        delegate.insertContacts(contacts)
                    }
                }
            },
        ) { repo, dao, events, rest, contactApi, db ->
            coEvery { contactApi.getContacts() } returns listOf(
                Contact(userId, ancient, ContactStatus.FRIEND, null, null, now, now) to
                    User(ancient, "peer", null, null, null, null, now, now, null),
            )
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 2L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = snapshotConversationId)),
            )
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 2L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 2L, 2L, false, false, emptyList(),
            )
            coEvery { rest.getDetail(any()) } returns detailResponse(id = snapshotConversationId, unreadCount = 4)
            repo.startSession(); runCurrent()

            // The contact write failed inside the snapshot transaction: the
            // conversation list apply and the cursor must roll back with it.
            assertNull(
                db.conversationsQueries.getConversationById(snapshotConversationId).executeAsOneOrNull(),
                "a failed snapshot stage must not leave a half-applied conversation list",
            )
            assertNull(dao.getScopeCursor(SyncScope.USER.name, userId), "a failed snapshot must not checkpoint")
            assertNull(
                db.conversationsQueries.getConversationById(conversationId).executeAsOne().deleted_at,
                "the omission cleanup must roll back too, not just the new rows",
            )
            // A rolled-back snapshot must not schedule any unread refresh: the
            // coordinator must never treat uncommitted data as applied.
            coVerify(exactly = 0) { rest.getDetail(any()) }

            failContactInsert = false
            events.emit(userHeads(2L)); runCurrent()

            // Retry commits everything in one transaction.
            assertNotNull(db.conversationsQueries.getConversationById(snapshotConversationId).executeAsOneOrNull())
            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            assertEquals(
                listOf(ancient),
                db.contactsQueries.selectAllContactsForSync().executeAsList().map { it.contact_id },
            )
            // The pre-existing conversation is absent from the snapshot: the
            // same committed apply soft-deletes it and drops its membership.
            assertNotNull(db.conversationsQueries.getConversationById(conversationId).executeAsOne().deleted_at)
            // Post-commit, the sync worker schedules the authoritative unread
            // refresh for the restored conversation; it converges the badge.
            coVerify(exactly = 1) { rest.getDetail(snapshotConversationId) }
            assertEquals(
                4,
                db.conversationsQueries.getConversationById(snapshotConversationId).executeAsOne().unread_count,
            )
            // One failed pull + one successful retry pull, both snapshots.
            coVerify(exactly = 2) { rest.getSyncSnapshot(SyncScope.USER, userId, any()) }
        }
    }

    @Test
    fun journalDeltaRecoveryTriggersAuthoritativeUnreadRefreshWithoutRealtimeEvents() = runTest {
        var conversationDao: ConversationDaoImpl? = null
        fixture(observeConversations = { conversationDao = it }) { repo, dao, _, _, rest ->
            dao.setScopeCursor(SyncScope.CONVERSATION.name, conversationId, 1L)
            // Pure offline recovery: the message arrives ONLY through the
            // journal batch, no live Received event follows it.
            coEvery { rest.getSyncHeads() } returns conversationHeads(2)
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) } returns SyncBatch(
                SyncScope.CONVERSATION, conversationId, 1L, 2L, false, false,
                listOf(SyncChange(2L, MessageEventResponse.Received(message(serverId), conversationId, userId, ""))),
            )
            coEvery { rest.getDetail(conversationId) } returns detailResponse(unreadCount = 5)
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            // The coalesced authoritative refresh must have converged the badge
            // to the server value even without any realtime trigger.
            assertEquals(
                5,
                conversationDao?.getConversationById(conversationId)?.first()?.unread_count,
                "offline journal recovery must converge the unread count",
            )
        }
    }

    @Test
    fun userSnapshotRecoveryAppliesAuthoritativeUnreadCounts() = runTest {
        val snapshotConversationId = Uuid.parse("00000000-0000-0000-0000-000000000043")
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 2L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = snapshotConversationId, unreadCount = 99)),
            )
            coEvery { rest.getDetail(snapshotConversationId) } returns detailResponse(
                id = snapshotConversationId, unreadCount = 7,
            )
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            // The snapshot transaction must not touch the coordinator; the
            // post-commit refresh converges the badge to the server value.
            coVerify(exactly = 1) { rest.getDetail(snapshotConversationId) }
            assertEquals(
                7,
                db.conversationsQueries.getConversationById(snapshotConversationId).executeAsOne().unread_count,
                "offline USER snapshot recovery must converge the unread count via the post-commit refresh",
            )
        }
    }

    @Test
    fun userDeltaAppliesContactStateAndRecordsMissingGroupJoinDecision() = runTest {
        val requestId = Uuid.parse("00000000-0000-0000-0000-000000000051")
        val handledRequestId = Uuid.parse("00000000-0000-0000-0000-000000000052")
        userSyncFixture { repo, dao, events, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 0L)
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns listOf(
                ContactRequest(requestId, userId, ancient, "hi", RequestStatus.PENDING, now, now),
            )
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 0L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 0L, 2L, false, false,
                listOf(
                    SyncChange(1L, ContactEventResponse.FriendRequestSent(requestId, userId, ancient, now)),
                    SyncChange(2L, ContactEventResponse.ContactAdded(userId, ancient, now)),
                ),
            )
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            assertEquals(
                listOf(requestId),
                db.contactRequestsQueries.selectAllContactRequestsForSync().executeAsList().map { it.id },
            )
            coVerify(exactly = 1) { contactApi.getContacts() }

            // A handled group-join decision whose request row does not exist
            // locally must still be recorded (synthetic row), never dropped
            // with the cursor advancing past it.
            val heads3 = userHeads(3L)
            coEvery { rest.getSyncHeads() } returns heads3
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 2L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 2L, 3L, false, false,
                listOf(
                    SyncChange(
                        3L,
                        ConversationEventResponse.GroupJoinRequestHandled(
                            requestId = handledRequestId,
                            conversationId = conversationId,
                            applicantId = ancient,
                            handlerId = userId,
                            approved = false,
                            reason = null,
                            timestamp = now,
                        ),
                    ),
                ),
            )
            events.emit(heads3); runCurrent()

            assertEquals(3L, dao.getScopeCursor(SyncScope.USER.name, userId))
            val recorded = db.groupJoinRequestsQueries.selectGroupJoinRequestsByConversation(
                conversationId = conversationId,
                status = null,
            ).executeAsList().first { it.id == handledRequestId }
            assertEquals(RequestStatus.REJECTED, recorded.status)
            assertEquals(userId, recorded.handled_by)
            // Without a contact event in the batch, no second contact refetch.
            coVerify(exactly = 1) { contactApi.getContacts() }
        }
    }

    @Test
    fun userDeltaRecoveryOfOwnReadTriggersAuthoritativeUnreadRefresh() = runTest {
        var conversationDao: ConversationDaoImpl? = null
        fixture(observeConversations = { conversationDao = it }) { repo, dao, _, _, rest ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            // The other device read the conversation while this one was
            // offline; the Read is restored ONLY through the USER journal.
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, false, false,
                listOf(
                    SyncChange(
                        2L,
                        MessageEventResponse.Read(
                            messageId = serverId,
                            conversationId = conversationId,
                            readerId = userId,
                            timestamp = now,
                            unreadCount = 9,
                        ),
                    ),
                ),
            )
            // The event's own unreadCount is NOT trusted (it may be stale);
            // the coalesced authoritative refresh decides the final value.
            coEvery { rest.getDetail(conversationId) } returns detailResponse(unreadCount = 3)
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            assertEquals(
                3,
                conversationDao?.getConversationById(conversationId)?.first()?.unread_count,
                "a restored USER-scope Read must converge the unread count",
            )
        }
    }

    @Test
    fun userDeltaAppliesJoinedConversationFromDetail() = runTest {
        val joinedId = Uuid.parse("00000000-0000-0000-0000-000000000044")
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getDetail(joinedId) } returns detailResponse(id = joinedId, unreadCount = 2)
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, false, false,
                listOf(
                    SyncChange(
                        2L,
                        ConversationEventResponse.UserJoinedConversation(
                            conversationId = joinedId,
                            userId = listOf(userId),
                            inviterId = null,
                            timestamp = now,
                        ),
                    ),
                ),
            )
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            val conversation = db.conversationsQueries.getConversationById(joinedId).executeAsOneOrNull()
            assertNotNull(conversation, "a restored join must create the conversation row")
            assertNull(conversation.deleted_at)
            assertNotNull(db.participantsQueries.getParticipant(joinedId, userId).executeAsOneOrNull())
        }
    }

    @Test
    fun userDeltaAppliesOwnLeaveByHidingConversation() = runTest {
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            db.participantsQueries.upsertParticipant(
                io.github.woodsmarshes.chat.db.ParticipantEntity(
                    conversationId, userId, ConversationRole.MEMBER, null, now, null, ParticipantSettings(),
                ),
            )
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, false, false,
                listOf(
                    SyncChange(
                        2L,
                        ConversationEventResponse.UserLeftConversation(
                            conversationId = conversationId,
                            userId = userId,
                            timestamp = now,
                        ),
                    ),
                ),
            )
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            assertNull(
                db.participantsQueries.getParticipant(conversationId, userId).executeAsOneOrNull(),
                "a restored leave must drop the local membership",
            )
            assertNotNull(
                db.conversationsQueries.getConversationById(conversationId).executeAsOne().deleted_at,
                "a restored leave must hide the conversation",
            )
        }
    }

    @Test
    fun userDeltaAppliesPersonalSettingsWithoutTouchingReadCursor() = runTest {
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            db.participantsQueries.upsertParticipant(
                io.github.woodsmarshes.chat.db.ParticipantEntity(
                    conversationId, userId, ConversationRole.MEMBER, serverId, now, null, ParticipantSettings(),
                ),
            )
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, false, false,
                listOf(
                    SyncChange(
                        2L,
                        ConversationEventResponse.PersonalSettingsUpdated(
                            conversationId = conversationId,
                            userId = userId,
                            settings = ParticipantSettings(nickname = "alice"),
                            timestamp = now,
                        ),
                    ),
                ),
            )
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            val participant = db.participantsQueries.getParticipant(conversationId, userId).executeAsOne()
            assertEquals("alice", participant.settings.nickname)
            assertEquals(
                serverId,
                participant.last_read_message_id,
                "personal settings sync must not clobber the read cursor",
            )
        }
    }

    /** The server answers the joined conversation's detail with 403 once the
     *  offline join-then-leave history has lapsed the membership. The batch
     *  must recover through a controlled USER snapshot reset instead of
     *  retrying the unapplicable join forever. */
    @Test
    fun userDeltaJoinedThenLeftInSameBatchResetsThroughSnapshot() = runTest {
        val joinedId = Uuid.parse("00000000-0000-0000-0000-000000000044")
        userSyncFixture { repo, dao, events, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            val forbidden = mockk<HttpResponse>(relaxed = true) { every { status } returns HttpStatusCode.Forbidden }
            coEvery { rest.getDetail(joinedId) } throws ClientRequestException(forbidden, "")
            coEvery { rest.getSyncHeads() } returns userHeads(3L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 3L, false, false,
                listOf(
                    SyncChange(
                        2L,
                        ConversationEventResponse.UserJoinedConversation(
                            conversationId = joinedId, userId = listOf(userId), inviterId = null, timestamp = now,
                        ),
                    ),
                    SyncChange(
                        3L,
                        ConversationEventResponse.UserLeftConversation(
                            conversationId = joinedId, userId = userId, timestamp = now,
                        ),
                    ),
                ),
            )
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 3L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = conversationId)),
            )
            coEvery { rest.getDetail(conversationId) } returns detailResponse(id = conversationId, unreadCount = 0)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 3L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 3L, 3L, false, false, emptyList(),
            )
            repo.startSession(); runCurrent()

            assertEquals(3L, dao.getScopeCursor(SyncScope.USER.name, userId))
            // The lapsed join must not retry: the snapshot replaced the batch.
            coVerify(exactly = 1) { rest.getDetail(joinedId) }
            assertNull(db.conversationsQueries.getConversationById(joinedId).executeAsOneOrNull())
        }
    }

    @Test
    fun userDeltaJoinedOnFirstPageAndLeftOnNextRecoversThroughSnapshot() = runTest {
        val joinedId = Uuid.parse("00000000-0000-0000-0000-000000000044")
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            val forbidden = mockk<HttpResponse>(relaxed = true) { every { status } returns HttpStatusCode.Forbidden }
            coEvery { rest.getDetail(joinedId) } throws ClientRequestException(forbidden, "")
            coEvery { rest.getSyncHeads() } returns userHeads(3L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, true, false,
                listOf(
                    SyncChange(
                        2L,
                        ConversationEventResponse.UserJoinedConversation(
                            conversationId = joinedId, userId = listOf(userId), inviterId = null, timestamp = now,
                        ),
                    ),
                ),
            )
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 2L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 2L, 3L, false, false,
                listOf(
                    SyncChange(
                        3L,
                        ConversationEventResponse.UserLeftConversation(
                            conversationId = joinedId, userId = userId, timestamp = now,
                        ),
                    ),
                ),
            )
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 3L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = conversationId)),
            )
            coEvery { rest.getDetail(conversationId) } returns detailResponse(id = conversationId, unreadCount = 0)
            repo.startSession(); runCurrent()

            // The snapshot jumped the cursor past both pages; page two is never
            // pulled and the first page is never retried.
            assertEquals(3L, dao.getScopeCursor(SyncScope.USER.name, userId))
            coVerify(exactly = 1) { rest.getDetail(joinedId) }
            coVerify(exactly = 0) { rest.getSyncChanges(SyncScope.USER, userId, 2L, any()) }
            assertNull(db.conversationsQueries.getConversationById(joinedId).executeAsOneOrNull())
        }
    }

    /** Crash window: the cursor and its persisted refresh-pending marker commit
     *  together, the scheduled refresh never completes (session stopped with
     *  the detail request in flight), and the journal has nothing to replay
     *  afterwards. Reconnecting must restore the refresh from the marker and
     *  clear it once the authoritative value applied. */
    @Test
    fun unreadRefreshPendingMarkerSurvivesSessionRestart() = runTest {
        val detailGate = CompletableDeferred<Unit>()
        userSyncFixture { repo, dao, events, rest, contactApi, db ->
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 2L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = conversationId, unreadCount = 99)),
            )
            // The post-commit refresh hangs in flight until released.
            coEvery { rest.getDetail(conversationId) } coAnswers {
                detailGate.await()
                detailResponse(id = conversationId, unreadCount = 3)
            }
            repo.startSession(); runCurrent()
            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            assertEquals(
                listOf(conversationId),
                dao.getUnreadRefreshPendingConversations(),
                "the marker must be persisted in the cursor transaction",
            )

            // Simulate the crash: the refresh never completes and the session's
            // in-memory dirty set dies with it — the marker does not.
            repo.stopSession(); runCurrent()
            assertEquals(listOf(conversationId), dao.getUnreadRefreshPendingConversations())

            repo.startSession(); runCurrent()
            detailGate.complete(Unit); runCurrent()
            // The journal must NOT replay (cursor already committed); the
            // restored refresh converges the badge and clears the marker.
            coVerify(exactly = 1) { rest.getSyncSnapshot(SyncScope.USER, userId, any()) }
            assertEquals(
                3,
                db.conversationsQueries.getConversationById(conversationId).executeAsOne().unread_count,
                "the restored refresh must repair a badge lost in the crash window",
            )
            assertEquals(
                emptyList(),
                dao.getUnreadRefreshPendingConversations(),
                "a successfully applied refresh must clear its pending marker",
            )
        }
    }

    /**
     * P1 regression: marking unread-refresh pending for a conversation during
     * USER snapshot recovery must NEVER create or synthesize a ScopeSyncCursor
     * row for that conversation. Cursor presence decides whether conversation
     * sync requests a window snapshot (cursor == null) or replays from revision
     * (cursor != null); a fresh device must recover the conversation via
     * getSyncSnapshot, not by replaying all historical changes from revision 0.
     */
    @Test
    fun userSnapshotLeavesUnreadPendingWithoutInitializingConversationSyncCursor() = runTest {
        val convId = Uuid.parse("00000000-0000-0000-0000-000000000045")
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            // Server heads: USER head at rev 2, CONVERSATION head at rev 10.
            coEvery { rest.getSyncHeads() } returns SyncStateResponse.Heads(
                entries = listOf(
                    SyncHead(SyncScope.USER, userId, 2L),
                    SyncHead(SyncScope.CONVERSATION, convId, 10L),
                ),
                timestamp = now,
            )
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 2L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = convId, unreadCount = 5)),
            )
            coEvery { rest.getDetail(convId) } returns detailResponse(id = convId, unreadCount = 5)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 2L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 2L, 2L, false, false, emptyList(),
            )
            coEvery { rest.getSyncSnapshot(SyncScope.CONVERSATION, convId, any()) } returns SyncSnapshot(
                SyncScope.CONVERSATION, convId, 10L,
                messages = listOf(message(new1)),
            )
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, convId, any(), any()) } returns SyncBatch(
                SyncScope.CONVERSATION, convId, 0L, 10L, false, false, emptyList(),
            )

            repo.startSession(); runCurrent()

            // 1. The USER stream cursor is committed at rev 2.
            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            // 2. The CONVERSATION was recovered via snapshot, NOT by replaying from 0.
            coVerify(exactly = 1) { rest.getSyncSnapshot(SyncScope.CONVERSATION, convId, any()) }
            coVerify(exactly = 0) { rest.getSyncChanges(SyncScope.CONVERSATION, convId, any(), any()) }
            assertEquals(10L, dao.getScopeCursor(SyncScope.CONVERSATION.name, convId))
        }
    }

    /**
     * P1 regression: an in-flight detail response reading an older task
     * generation must not retire a newer task generation committed by a
     * concurrent journal batch. If a new journal commit advances the task
     * generation while detail is in flight, the old response must NOT clear
     * the task; the task must survive process restart and recover.
     */
    @Test
    fun staleDetailResponseDoesNotRetireNewerPersistedUnreadTask() = runTest {
        val firstDetailGate = CompletableDeferred<Unit>()
        val secondDetailGate = CompletableDeferred<Unit>()
        var detailCallCount = 0
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(1L)
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 1L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = conversationId, unreadCount = 1)),
            )
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 1L, false, false, emptyList(),
            )
            coEvery { rest.getDetail(conversationId) } coAnswers {
                when (++detailCallCount) {
                    1 -> {
                        firstDetailGate.await()
                        detailResponse(id = conversationId, unreadCount = 1)
                    }
                    else -> {
                        secondDetailGate.await()
                        detailResponse(id = conversationId, unreadCount = 9)
                    }
                }
            }

            repo.startSession(); runCurrent()
            assertEquals(1L, dao.getScopeCursor(SyncScope.USER.name, userId))
            // Task generation in DB is currently 0. Refresh #1 is parked waiting on firstDetailGate.
            assertEquals(0L, dao.getUnreadRefreshPendingGeneration(conversationId))

            // Now, while detail #1 is in flight, commit a new journal task (bumping generation to 1).
            dao.transaction {
                dao.markUnreadRefreshPending(conversationId)
            }
            assertEquals(1L, dao.getUnreadRefreshPendingGeneration(conversationId))

            // Release detail #1: it reads generation 0 from before fetch, which
            // no longer matches current DB generation 1. It must not retire the task;
            // the loop begins fetch #2 and parks on secondDetailGate.
            firstDetailGate.complete(Unit); runCurrent()

            // Stop the session to simulate an exit before refresh #2 finishes.
            repo.stopSession(); runCurrent()

            // The newer task (generation 1) must still be pending in the database!
            assertEquals(
                listOf(conversationId),
                dao.getUnreadRefreshPendingConversations(),
                "a stale detail response must never clear a newer persisted task generation",
            )
            assertEquals(1L, dao.getUnreadRefreshPendingGeneration(conversationId))

            // Re-start session: the surviving task is recovered, fetches detail #2, and retires generation 1.
            repo.startSession(); runCurrent()
            secondDetailGate.complete(Unit); runCurrent()

            assertEquals(
                9,
                db.conversationsQueries.getConversationById(conversationId).executeAsOne().unread_count,
            )
            assertEquals(
                emptyList(),
                dao.getUnreadRefreshPendingConversations(),
                "the recovered task must be retired once its matching detail applies",
            )
        }
    }

    /**
     * P2 regression: when an initial detail fetch fails with an exception, the
     * pending task must remain in UnreadRefreshPending. When retried (e.g. by
     * retryPending or reconnect), the worker must use the same confirmation
     * logic and clear the persisted task once the authoritative value applies.
     */
    @Test
    fun failedRefreshRetriedBySweepClearsPendingTaskOnSuccess() = runTest {
        var failDetail = true
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(1L)
            coEvery { rest.getSyncSnapshot(SyncScope.USER, userId, any()) } returns SyncSnapshot(
                SyncScope.USER, userId, 1L,
                events = emptyList(),
                conversations = listOf(detailResponse(id = conversationId, unreadCount = 2)),
            )
            coEvery { rest.getDetail(conversationId) } coAnswers {
                if (failDetail) throw RuntimeException("injected network error")
                detailResponse(id = conversationId, unreadCount = 4)
            }

            repo.startSession(); runCurrent()
            // The initial detail call failed: the task must remain pending in DB.
            assertEquals(
                listOf(conversationId),
                dao.getUnreadRefreshPendingConversations(),
                "a failed refresh must leave its task pending in the database",
            )

            // Allow the detail call to succeed and simulate the retry pass via reconnect.
            failDetail = false
            repo.stopSession(); runCurrent()
            repo.startSession(); runCurrent()

            assertEquals(
                4,
                db.conversationsQueries.getConversationById(conversationId).executeAsOne().unread_count,
            )
            assertEquals(
                emptyList(),
                dao.getUnreadRefreshPendingConversations(),
                "a successful retry must clear the persisted pending task",
            )
        }
    }

    /**
     * Lock-order regression: a USER-snapshot-shaped database transaction and a
     * coordinator detail refresh (coordinator mutex → database write) run
     * against the SAME real file database through TWO SEPARATE connections
     * from independent execution contexts. The snapshot transaction must never
     * take the coordinator lock while the transaction is open, or these two
     * interleave into a lock-order inversion that only manifests under real
     * cross-connection concurrency. Transient SQLite write contention is
     * tolerated (busy timeout + retry via requestRefresh); a lock-order
     * deadlock would blow the outer timeout instead.
     */
    @Test
    fun snapshotApplyAndDetailRefreshStayConcurrentOnFileDatabase() = runBlocking {
        val file = java.nio.file.Files.createTempFile("chat-unread-lock-", ".db")
        val fileUrl = "jdbc:sqlite:$file"
        val driverA = JdbcSqliteDriver(fileUrl, Properties().apply { put("foreign_keys", "true") })
        val driverB = JdbcSqliteDriver(fileUrl, Properties().apply { put("foreign_keys", "true") })
        driverA.execute(null, "PRAGMA busy_timeout=5000", 0).await()
        driverB.execute(null, "PRAGMA busy_timeout=5000", 0).await()
        val writerDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor()
            .asCoroutineDispatcher()
        try {
            createDatabase { schema -> schema.create(driverA).await(); driverA }
            val dbA = createDatabase { driverA }
            val dbB = createDatabase { driverB }
            dbA.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
            dbA.usersQueries.upsertUser(UserEntity(ancient, "peer", null, null, null, null, now, now, null, UserRole.MEMBER))
            // The snapshot writer and the refresh worker each own one connection;
            // the coordinator confirms its tasks on the WORKER's connection so
            // the unread write and the generation-confirmed retire share one
            // transaction.
            val writerDao = MessageDaoImpl({ dbA }, Dispatchers.Unconfined)
            val writerConversations = ConversationDaoImpl({ dbA }, Dispatchers.Unconfined)
            val writerParticipants = ParticipantDaoImpl({ dbA }, Dispatchers.Unconfined)
            val workerConversations = ConversationDaoImpl({ dbB }, Dispatchers.Unconfined)
            val coordinator = UnreadRefreshCoordinator(MessageDaoImpl({ dbB }, Dispatchers.Unconfined))
            val detailApi = mockk<ConversationApi>()
            coEvery { detailApi.getDetail(conversationId) } returns detailResponse(unreadCount = 6)
            val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                // Seed the conversation once; later snapshot-shaped transactions
                // only rewrite membership, which must never reset the badge.
                writerConversations.insertConversation(
                    ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 0),
                )
                withTimeout(30_000) {
                    var converged = false
                    while (!converged) {
                        val worker = workerScope.launch {
                            coordinator.requestRefresh(workerScope, conversationId, detailApi, workerConversations)
                        }
                        val snapshotApply = launch(writerDispatcher) {
                            writerDao.transaction {
                                writerParticipants.insertParticipant(
                                    io.github.woodsmarshes.chat.db.ParticipantEntity(
                                        conversationId, userId, ConversationRole.MEMBER, null, now, null, ParticipantSettings(),
                                    ),
                                )
                            }
                        }
                        snapshotApply.join()
                        worker.join()
                        // requestRefresh returns before the worker finishes:
                        // poll until the badge converges (or retry next round).
                        val roundDeadline = System.currentTimeMillis() + 2_000
                        while (System.currentTimeMillis() < roundDeadline) {
                            if (writerConversations.getConversationById(conversationId).first()?.unread_count == 6L) {
                                converged = true
                                break
                            }
                            delay(10)
                        }
                    }
                }
            } finally {
                workerScope.coroutineContext[Job]?.cancelAndJoin()
            }
        } finally {
            writerDispatcher.close()
            driverA.close()
            driverB.close()
            java.nio.file.Files.deleteIfExists(file)
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
            assertNull(dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId), "ACK must not advance the journal cursor")
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
    fun failedJournalBatchRollsBackMessagesAndCursorThenRetries() = runTest {
        var failWrite = true
        fixture(autoStart = false, decorate = { delegate ->
            object : MessageDao by delegate {
                override suspend fun mergeServerMessage(message: MessageEntity, localId: Uuid?) {
                    if (message.id == serverId && failWrite) error("Injected journal write failure")
                    delegate.mergeServerMessage(message, localId)
                }
            }
        }) { repo, dao, _, events, rest ->
            dao.setScopeCursor(SyncScope.CONVERSATION.name, conversationId, 1L)
            // Heads stay above the local cursor across both pulls: the first
            // delta fails on the injected write error, the wakeup-triggered
            // retry must re-pull and succeed against the same head.
            coEvery { rest.getSyncHeads() } returns conversationHeads(2)
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) } returns SyncBatch(
                SyncScope.CONVERSATION, conversationId, 1L, 2L, false, false,
                listOf(SyncChange(2L, MessageEventResponse.Received(message(serverId), conversationId, userId, ""))),
            )
            repo.startSession(); runCurrent()
            assertEquals(1L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            assertNull(dao.getMessageById(serverId).first())
            failWrite = false
            events.emit(conversationHeads(2)); runCurrent()
            assertNotNull(dao.getMessageById(serverId).first())
            assertEquals(2L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            coVerify(exactly = 2) { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) }
        }
    }

    @Test
    fun conversationSnapshotDemotesOldTimelineAndCommitsItsRevision() = runTest {
        fixture(autoStart = false) { repo, dao, _, _, rest ->
            dao.insertMessage(message(old1).toMessageEntity(MessageStatus.SENT)!!)
            dao.insertMessage(message(old2).toMessageEntity(MessageStatus.SENT)!!)
            coEvery { rest.getSyncHeads() } returns conversationHeads(5)
            coEvery { rest.getSyncSnapshot(SyncScope.CONVERSATION, conversationId, any()) } returns SyncSnapshot(
                SyncScope.CONVERSATION, conversationId, 5L, messages = listOf(message(new2), message(new1)),
            )
            repo.startSession(); runCurrent()
            assertEquals(false, dao.getMessageById(old1).first()?.in_timeline)
            assertEquals(false, dao.getMessageById(old2).first()?.in_timeline)
            assertEquals(true, dao.getMessageById(new1).first()?.in_timeline)
            assertEquals(true, dao.getMessageById(new2).first()?.in_timeline)
            assertEquals(5L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            coVerify(exactly = 1) { rest.getSyncSnapshot(SyncScope.CONVERSATION, conversationId, any()) }
        }
    }

    @Test
    fun journalDeltaAppliesChangesInRevisionOrderAndAdvancesCursor() = runTest {
        fixture(autoStart = false) { repo, dao, _, _, rest ->
            dao.setScopeCursor(SyncScope.CONVERSATION.name, conversationId, 1L)
            coEvery { rest.getSyncHeads() } returns conversationHeads(3)
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) } returns SyncBatch(
                SyncScope.CONVERSATION, conversationId, 1L, 3L, false, false,
                listOf(
                    SyncChange(2L, MessageEventResponse.Received(message(serverId), conversationId, userId, "")),
                    SyncChange(3L, MessageEventResponse.Received(message(nextId), conversationId, userId, "")),
                ),
            )
            repo.startSession(); runCurrent()
            assertNotNull(dao.getMessageById(serverId).first())
            assertNotNull(dao.getMessageById(nextId).first())
            assertEquals(3L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            coVerify(exactly = 1) { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) }
        }
    }

    @Test
    fun resetRequiredRecoversFromSnapshotAndStoresSnapshotRevision() = runTest {
        fixture(autoStart = false) { repo, dao, _, _, rest ->
            dao.setScopeCursor(SyncScope.CONVERSATION.name, conversationId, 1L)
            coEvery { rest.getSyncHeads() } returns conversationHeads(8)
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) } returns SyncBatch(
                SyncScope.CONVERSATION, conversationId, 1L, 1L, false, true,
            )
            coEvery { rest.getSyncSnapshot(SyncScope.CONVERSATION, conversationId, any()) } returns SyncSnapshot(
                SyncScope.CONVERSATION, conversationId, 8L, messages = listOf(message(new1)),
            )
            repo.startSession(); runCurrent()
            assertNotNull(dao.getMessageById(new1).first())
            assertEquals(8L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            coVerify(exactly = 1) { rest.getSyncSnapshot(SyncScope.CONVERSATION, conversationId, any()) }
        }
    }

    @Test
    fun repeatedHeadsWakeupsDoNotReapplyAnAlreadyCommittedDelta() = runTest {
        fixture(autoStart = false) { repo, dao, _, events, rest ->
            dao.setScopeCursor(SyncScope.CONVERSATION.name, conversationId, 1L)
            coEvery { rest.getSyncHeads() } coAnswers { if (dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId) == 1L) conversationHeads(2) else SyncStateResponse.Heads(timestamp = now) }
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) } returns SyncBatch(
                SyncScope.CONVERSATION, conversationId, 1L, 2L, false, false,
                listOf(SyncChange(2L, MessageEventResponse.Received(message(serverId), conversationId, userId, ""))),
            )
            repo.startSession(); runCurrent()
            events.emit(conversationHeads(2)); events.emit(conversationHeads(2)); runCurrent()
            assertNotNull(dao.getMessageById(serverId).first())
            assertEquals(2L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
            coVerify(exactly = 1) { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) }
        }
    }

    @Test
    fun sessionDoesNotStartImplicitlyAndRepeatedStartIsIdempotent() = runTest {
        fixture(autoStart = false) { repo, dao, _, events, rest ->
            // With no local cursor the first pull after start is a snapshot;
            // the journal must deliver the pre-session backlog through it.
            coEvery { rest.getSyncHeads() } returns conversationHeads(2)
            coEvery { rest.getSyncSnapshot(SyncScope.CONVERSATION, conversationId, any()) } returns SyncSnapshot(
                SyncScope.CONVERSATION, conversationId, 2L, messages = listOf(message(serverId)),
            )
            events.emit(MessageEventResponse.Received(message(serverId), conversationId, userId, ""))
            events.emit(MessageEventResponse.UserTyping(conversationId, userId, true, now))
            advanceTimeBy(20_000L)
            runCurrent()
            assertNull(dao.getMessageById(serverId).first(), "no events may be consumed before startSession()")
            assertEquals(Triple(0, 0, 0), repo.inMemorySessionStateCountsForTest())
            coVerify(exactly = 0) { rest.getSyncHeads() }
            repo.startSession()
            repo.startSession()
            runCurrent()
            assertNotNull(dao.getMessageById(serverId).first())
            coVerify(exactly = 1) { rest.getSyncHeads() }
        }
    }
    @Test
    fun stopSessionCancelsAndWaitsForInFlightJournalPull() = runTest {
        val pullStarted = CompletableDeferred<Unit>()
        val allowCleanupFinish = CompletableDeferred<Unit>()
        var cleanupFinished = false
        fixture(autoStart = false) { repo, dao, _, _, rest ->
            dao.setScopeCursor(SyncScope.CONVERSATION.name, conversationId, 1L)
            coEvery { rest.getSyncHeads() } returns conversationHeads(2)
            coEvery { rest.getSyncChanges(SyncScope.CONVERSATION, conversationId, 1L, any()) } coAnswers {
                pullStarted.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { allowCleanupFinish.await(); cleanupFinished = true }
                }
            }
            repo.startSession(); runCurrent(); pullStarted.await()
            var stopReturned = false
            val stopJob = launch { repo.stopSession(); stopReturned = true }
            runCurrent()
            assertFalse(stopReturned)
            allowCleanupFinish.complete(Unit); runCurrent(); stopJob.join()
            assertTrue(stopReturned)
            assertTrue(cleanupFinished)
            assertEquals(1L, dao.getScopeCursor(SyncScope.CONVERSATION.name, conversationId))
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
            // the sync-head pull nor event consumption may run.
            connectionStates.value = ConnectionState.Idle
            runCurrent()
            connectionStates.value = ConnectionState.Connected
            events.emit(MessageEventResponse.Received(message(serverId), conversationId, userId, ""))
            advanceTimeBy(35_000L)
            runCurrent()

            assertNull(dao.getMessageById(serverId).first(), "stopped session must not consume events")

            repo.startSession()
            runCurrent()
            // Content catch-up is journal-head-driven: a bare startSession() must
            // not sweep. The restarted session only consumes live events.

            events.emit(MessageEventResponse.Received(message(serverId), conversationId, userId, ""))
            runCurrent()
            assertNotNull(dao.getMessageById(serverId).first(), "restarted session consumes events again")

            clearMocks(rest, answers = false, recordedCalls = true)
            advanceTimeBy(35_000L)
            runCurrent()
            coVerify(atLeast = 1) { rest.getSyncHeads() }
        }
    }

    @Test
    fun startSessionGuaranteesSubscriptionReadyForImmediateFirstEvent() = runTest {
        fixture(autoStart = false) { repo, dao, _, events, _ ->
            // No runCurrent() between startSession() and events.emit(): the
            // subscription must already be registered when startSession() returns.
            repo.startSession()
            events.emit(MessageEventResponse.Received(message(serverId), conversationId, userId, ""))
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
            val incoming = message(serverId).copy(sender = peer)
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
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.SENT)!!)
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
            val incoming = message(nextId).copy(sender = peer)
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
            dao.insertMessage(message(serverId).toMessageEntity(MessageStatus.SENT)!!)
            dao.insertMessage(message(nextId).toMessageEntity(MessageStatus.SENT)!!)

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
            val incoming = message(serverId).copy(sender = peer)
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
            events.emit(MessageEventResponse.Received(message(serverId).copy(sender = peer), conversationId, peer.id, ""))
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
        coEvery { rest.getSyncHeads() } returns SyncStateResponse.Heads(timestamp = now)
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

        val coordinator = UnreadRefreshCoordinator(dao)
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
            userSyncApplier = mockk(relaxed = true),
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
        coEvery { rest.getSyncHeads() } returns SyncStateResponse.Heads(timestamp = now)
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

        val coordinator = UnreadRefreshCoordinator(dao)
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
            userSyncApplier = mockk(relaxed = true),
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
    fun conversationDeletedInUserSyncBatchSoftDeletesConversationAndAdvancesCursor() = runTest {
        userSyncFixture { repo, dao, _, rest, contactApi, db ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, false, false,
                listOf(
                    SyncChange(2L, ConversationEventResponse.ConversationDeleted(conversationId, userId, now)),
                ),
            )
            db.conversationsQueries.upsertConversation(ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 0))
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId))
            val conv = db.conversationsQueries.getConversationById(conversationId).executeAsOneOrNull()
            assertNotNull(conv?.deleted_at, "conversation must be soft-deleted by ConversationDeleted in USER stream")
        }
    }

    @Test
    fun unknownEventInUserSyncBatchIsSkippedAndCursorAdvances() = runTest {
        userSyncFixture { repo, dao, _, rest, contactApi, _ ->
            dao.setScopeCursor(SyncScope.USER.name, userId, 1L)
            coEvery { contactApi.getContacts() } returns emptyList()
            coEvery { contactApi.getContactRequests() } returns emptyList()
            coEvery { rest.getSyncHeads() } returns userHeads(2L)
            coEvery { rest.getSyncChanges(SyncScope.USER, userId, 1L, any()) } returns SyncBatch(
                SyncScope.USER, userId, 1L, 2L, false, false,
                listOf(
                    SyncChange(2L, com.github.woodsmarshes.chat.core.network.dto.events.UnknownRealtimeEvent),
                ),
            )
            repo.startSession(); runCurrent()

            assertEquals(2L, dao.getScopeCursor(SyncScope.USER.name, userId), "unknown event must not crash sync or prevent cursor advance")
        }
    }









}
