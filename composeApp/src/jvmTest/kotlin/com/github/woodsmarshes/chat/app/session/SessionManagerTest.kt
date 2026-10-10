package com.github.woodsmarshes.chat.app.session

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.data.repository.AuthRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.ContactRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.OfflineFirstMessageRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.UserRepositoryImpl
import com.github.woodsmarshes.chat.core.database.dao.ContactDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ConversationDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.datastore.di.createPreferenceDataSources
import com.github.woodsmarshes.chat.core.model.AuthSessionSnapshot
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.UserEntity
import io.github.woodsmarshes.chat.db.UsersQueries
import java.nio.file.Files
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies the [SessionManager] state machine and coordinator wiring:
 *  - Login opens the database, starts the message session (with subscription
 *    already registered), starts the [RealtimeApi] connection loop, and only
 *    then publishes [SessionState.Active] (`isLoggedIn == true`).
 *  - Logout enters [SessionState.Stopping] (`isLoggedIn == false`), stops the
 *    realtime producer and waits for it before stopping the message consumer,
 *    and closes the database using the database's own `resourceGeneration`
 *    (verified against a strict generation-checking holder so a mismatch
 *    between `stopTransitionGen` and `resourceGeneration` fails the test).
 *  - A stale `false` queued while [SessionManager] is busy does not tear down
 *    an already active session once the authoritative state is `true` again.
 *  - Startup failures at DB open, `messageRepository.startSession()`, or
 *    synchronous `realtimeApi.connect()` startup roll back partial resources,
 *    enter [SessionState.StartFailed] (`isLoggedIn == false`), keep the
 *    collector alive, and recover via [SessionManager.retrySessionStart].
 *  - Teardown failure (`disconnect()` or `stopSession()` throwing) does NOT
 *    close the database or switch to a new account's session while old workers
 *    cannot be confirmed stopped.
 *  - Target changes during suspended startup (logout or account switch before
 *    `Active` is committed) roll back the stale startup and reconcile into the
 *    latest target without publishing a stale `Active`.
 *  - In-flight `sendMessage` / `retryMessage` operations are cancelled and
 *    awaited on logout, and subsequent operations are refused while logged out.
 *  - Same-account fast re-login during the 500ms grace window advances the
 *    session generation so the stale logout does not close the active database.
 *  - Cancelled startup whose rollback fails preserves resource identity,
 *    attaches the rollback error, enters [SessionState.StartFailed] instead of
 *    [SessionState.Idle], still propagates the original [CancellationException],
 *    and blocks bypassing teardown into a new session until cleanup succeeds.
 *  - Passive logout uses the generation-guarded delayed database close with the
 *    database's `resourceGeneration`, catches database close failures into
 *    [SessionState.StartFailed] without killing the auth collector, and recovers
 *    on subsequent retry or re-login.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val secondUserId = Uuid.parse("00000000-0000-0000-0000-000000000009")
    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val firstMessageId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val secondMessageId = Uuid.parse("00000000-0000-0000-0000-000000000011")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val sender = SimpleUser(userId, "user", null, null, now, now, null, UserRole.MEMBER)
    private val currentUser = User(userId, "user", null, null, null, null, now, now, null)
    private val secondUser = User(secondUserId, "user2", null, null, null, null, now, now, null)

    /**
     * Mirrors [DatabaseHolder]'s exact `(userId, generation)` state machine so
     * tests fail if `closeDatabaseIfCurrent` is called with a mismatched
     * generation (such as passing `stopTransitionGen` instead of the DB's own
     * `resourceGeneration`).
     */
    private class StrictDatabaseStub(
        private val db: ChatDatabase,
        private val steps: MutableList<String>? = null,
    ) {
        var currentUserId: Uuid? = null
            private set
        var currentGeneration: Long = 0L
            private set
        var isOpen: Boolean = false
            private set
        var effectiveCloseCount: Int = 0
            private set
        val openedGenerations = mutableListOf<Pair<Uuid, Long?>>()
        val closeAttempts = mutableListOf<Pair<Uuid, Long?>>()

        val holder: DatabaseHolder = mockk()

        init {
            coEvery { holder.getOrCreateDatabase(any(), any()) } coAnswers {
                val u = firstArg<Uuid>()
                val g = secondArg<Long?>()
                openedGenerations += u to g
                if (g != null) currentGeneration = g
                if (currentUserId != u || !isOpen) {
                    currentUserId = u
                    isOpen = true
                }
                steps?.add("db.open")
                db
            }
            coEvery { holder.stopDatabaseSession() } returns Unit
            coEvery { holder.closeDatabaseIfCurrent(any(), any()) } coAnswers {
                val u = firstArg<Uuid>()
                val g = secondArg<Long?>()
                closeAttempts += u to g
                if (isOpen && currentUserId == u && (g == null || currentGeneration == g)) {
                    isOpen = false
                    currentUserId = null
                    currentGeneration = 0L
                    effectiveCloseCount++
                    steps?.add("db.close")
                } else {
                    steps?.add("db.close.ignored(expected=$currentUserId@$currentGeneration,got=$u@$g)")
                }
            }
        }
    }

    private fun receivedEvent(id: Uuid, seq: Long) = MessageEventResponse.Received(
        Message(
            id = id,
            conversationId = conversationId,
            sender = sender,
            category = MessageCategory.NORMAL,
            createdAt = now,
            content = TextContent("hello $seq"),
            seq = seq,
        ),
        conversationId,
        userId,
        "",
    )

    @Test
    fun loginStartsConsumerBeforeConnectAndPersistsImmediateFirstEvent() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(
            ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null)
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
        val dao = MessageDaoImpl({ db }, dispatcher)

        val loggedInFlow = MutableStateFlow(false)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 64)
        val connectionStates = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(db, steps)

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow
        coEvery { authRepository.logout() } coAnswers {
            steps += "auth.logout"
            loggedInFlow.value = false
        }

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.events } returns events
        every { realtimeApi.connectionState } returns connectionStates
        coEvery { realtimeApi.send(any()) } returns true
        coEvery { realtimeApi.connect() } coAnswers {
            steps += "ws.connect"
            connectionStates.value = ConnectionState.Connected
            events.emit(receivedEvent(firstMessageId, seq = 1L))
        }
        coEvery { realtimeApi.disconnect() } coAnswers {
            steps += "ws.disconnect"
            connectionStates.value = ConnectionState.Idle
        }

        val conversationApi = mockk<ConversationApi>()
        coEvery { conversationApi.syncMessages(any(), any(), any()) } returns emptyList()

        val realRepo = OfflineFirstMessageRepositoryImpl(
            mediatorFactory = io.mockk.mockk(),
            messageDao = dao,
            userDao = UserDaoImpl({ db }, dispatcher),
            participantDao = ParticipantDaoImpl({ db }, dispatcher),
            messageApi = realtimeApi,
            conversationApi = conversationApi,
            conversationDao = ConversationDaoImpl({ db }, dispatcher),
            groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, dispatcher),
            userSettingDataSource = userSettingDataSource,
            scope = repoScope,
        )
        val trackedRepo = object : MessageRepository by realRepo {
            override suspend fun startSession() {
                steps += "msg.startSession"
                realRepo.startSession()
            }

            override suspend fun stopSession() {
                steps += "msg.stopSession"
                realRepo.stopSession()
            }
        }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = trackedRepo,
                scope = managerScope,
            )
            runCurrent()
            assertEquals(SessionState.Idle, sessionManager.sessionState.value)
            assertEquals(false, sessionManager.isLoggedIn.value)
            steps.clear()

            loggedInFlow.value = true
            runCurrent()

            assertEquals(
                listOf("db.open", "msg.startSession", "ws.connect"),
                steps,
                "login must open the database, ready the message consumer, and only then connect",
            )
            assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(true, sessionManager.isLoggedIn.value)
            assertTrue(dbStub.isOpen)
            assertNotNull(
                dao.getMessageById(firstMessageId).first(),
                "an immediate first event emitted inside connect() must be persisted",
            )
        } finally {
            realRepo.stopSession()
            managerScope.cancel()
            repoScope.cancel()
            driver.close()
        }
    }

    @Test
    fun logoutWaitsForProducerThenConsumerAndActuallyClosesDatabaseByResourceGeneration() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(
            ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null)
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
        val dao = MessageDaoImpl({ db }, dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 64)
        val connectionStates = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(db, steps)
        val allowDisconnectFinish = CompletableDeferred<Unit>()

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow
        coEvery { authRepository.logout() } coAnswers {
            steps += "auth.logout"
            loggedInFlow.value = false
        }

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.events } returns events
        every { realtimeApi.connectionState } returns connectionStates
        coEvery { realtimeApi.send(any()) } returns true
        coEvery { realtimeApi.connect() } coAnswers {
            steps += "ws.connect"
            connectionStates.value = ConnectionState.Connected
        }
        coEvery { realtimeApi.disconnect() } coAnswers {
            steps += "ws.disconnect.start"
            allowDisconnectFinish.await()
            connectionStates.value = ConnectionState.Idle
            steps += "ws.disconnect.done"
        }

        val conversationApi = mockk<ConversationApi>()
        coEvery { conversationApi.syncMessages(any(), any(), any()) } returns emptyList()

        val realRepo = OfflineFirstMessageRepositoryImpl(
            mediatorFactory = io.mockk.mockk(),
            messageDao = dao,
            userDao = UserDaoImpl({ db }, dispatcher),
            participantDao = ParticipantDaoImpl({ db }, dispatcher),
            messageApi = realtimeApi,
            conversationApi = conversationApi,
            conversationDao = ConversationDaoImpl({ db }, dispatcher),
            groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, dispatcher),
            userSettingDataSource = userSettingDataSource,
            scope = repoScope,
        )
        val trackedRepo = object : MessageRepository by realRepo {
            override suspend fun startSession() {
                steps += "msg.startSession"
                realRepo.startSession()
            }

            override suspend fun stopSession() {
                steps += "msg.stopSession"
                realRepo.stopSession()
            }
        }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = trackedRepo,
                scope = managerScope,
            )
            runCurrent()
            val initialActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(listOf("db.open", "msg.startSession", "ws.connect"), steps)
            assertTrue(dbStub.isOpen)
            steps.clear()

            sessionManager.logout()
            runCurrent()
            assertIs<SessionState.Stopping>(sessionManager.sessionState.value)
            assertEquals(false, sessionManager.isLoggedIn.value, "UI must leave authenticated state as soon as Stopping begins")
            assertEquals(
                listOf("ws.disconnect.start"),
                steps,
                "message consumer must not be stopped before the realtime producer has exited",
            )

            allowDisconnectFinish.complete(Unit)
            runCurrent()
            advanceTimeBy(600L)
            runCurrent()

            assertTrue(
                steps.indexOf("ws.disconnect.done") < steps.indexOf("msg.stopSession"),
                "producer teardown must finish before consumer stop begins: $steps",
            )
            assertTrue(
                steps.indexOf("msg.stopSession") < steps.indexOf("db.close"),
                "consumer stop must finish before the database is closed: $steps",
            )
            // Strict generation check: the close call MUST pass the DB's resource
            // generation (initialActive.generation), NOT the stop transition generation.
            assertEquals<List<Pair<Uuid, Long?>>>(listOf(userId to initialActive.generation), dbStub.closeAttempts)
            assertEquals(1, dbStub.effectiveCloseCount, "normal logout must actually close the database")
            assertFalse(dbStub.isOpen, "database must be closed after normal logout grace window")
            assertEquals(SessionState.Idle, sessionManager.sessionState.value)

            events.emit(receivedEvent(firstMessageId, seq = 1L))
            runCurrent()
            assertNull(dao.getMessageById(firstMessageId).first())

            steps.clear()
            loggedInFlow.value = true
            runCurrent()
            assertEquals(listOf("db.open", "msg.startSession", "ws.connect"), steps)
            assertTrue(dbStub.isOpen)

            events.emit(receivedEvent(secondMessageId, seq = 2L))
            runCurrent()
            assertNotNull(
                dao.getMessageById(secondMessageId).first(),
                "re-login through SessionManager must restart message consumption",
            )
            assertEquals(1, dbStub.effectiveCloseCount, "re-login must not close the newly opened database")
        } finally {
            realRepo.stopSession()
            managerScope.cancel()
            repoScope.cancel()
            driver.close()
        }
    }

    @Test
    fun staleFalseQueuedWhileLockIsHeldDoesNotTearDownActiveSession() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val connectGate = CompletableDeferred<Unit>()
        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(mockk(relaxed = true), steps)

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers {
            steps += "ws.connect.start"
            connectGate.await()
            steps += "ws.connect.done"
        }
        coEvery { realtimeApi.disconnect() } coAnswers {
            steps += "ws.disconnect"
        }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.startSession" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stopSession" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()
            assertIs<SessionState.Starting>(sessionManager.sessionState.value)
            assertNull(sessionManager.isLoggedIn.value, "UI stays on loading gate while Starting")

            loggedInFlow.value = false
            runCurrent()
            loggedInFlow.value = true
            runCurrent()

            connectGate.complete(Unit)
            runCurrent()

            assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(true, sessionManager.isLoggedIn.value)
            assertEquals(
                listOf("db.open", "msg.startSession", "ws.connect.start", "ws.connect.done"),
                steps,
                "stale false queued before lock release must not trigger disconnect or stopSession",
            )
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun startupFailuresAtEachStageRollbackAndCanRecoverViaRetrySessionStart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()

        var failDbOpen = true
        var failMsgStart = false
        var failWsConnectStart = false

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val dbStub = StrictDatabaseStub(mockk(relaxed = true))
        val databaseHolder = mockk<DatabaseHolder>()
        coEvery { databaseHolder.stopDatabaseSession() } returns Unit
        coEvery { databaseHolder.getOrCreateDatabase(userId, any()) } coAnswers {
            if (failDbOpen) {
                steps += "db.open.fail"
                error("DB open failed")
            }
            steps += "db.open.ok"
            dbStub.holder.getOrCreateDatabase(firstArg<Uuid>(), secondArg<Long?>())
        }
        coEvery { databaseHolder.closeDatabaseIfCurrent(userId, any()) } coAnswers {
            dbStub.holder.closeDatabaseIfCurrent(firstArg<Uuid>(), secondArg<Long?>())
            if (!dbStub.isOpen) {
                steps += "db.rollback.close"
            }
        }

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers {
            if (failWsConnectStart) {
                steps += "ws.connect.fail"
                error("WS connect loop start failed")
            }
            steps += "ws.connect.ok"
        }
        coEvery { realtimeApi.disconnect() } coAnswers {
            steps += "ws.rollback.disconnect"
        }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers {
            if (failMsgStart) {
                steps += "msg.start.fail"
                error("Message session start failed")
            }
            steps += "msg.start.ok"
        }
        coEvery { messageRepository.stopSession() } coAnswers {
            steps += "msg.rollback.stop"
        }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = databaseHolder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()

            // Stage 1: DB open fails -> StartFailed, isLoggedIn == false.
            assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals(false, sessionManager.isLoggedIn.value)
            assertFalse(dbStub.isOpen)
            assertEquals(listOf("db.open.fail", "ws.rollback.disconnect"), steps)
            steps.clear()

            // Stage 2: DB open succeeds, messageRepository.startSession() fails -> rolls back DB with matching generation.
            failDbOpen = false
            failMsgStart = true
            sessionManager.retrySessionStart()
            runCurrent()
            assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals(false, sessionManager.isLoggedIn.value)
            assertFalse(dbStub.isOpen, "rolled back DB must actually be closed")
            assertEquals(
                listOf("db.open.ok", "msg.start.fail", "ws.rollback.disconnect", "db.rollback.close"),
                steps,
            )
            steps.clear()

            // Stage 3: DB and message session succeed, synchronous connect() loop start fails -> rolls back both.
            failMsgStart = false
            failWsConnectStart = true
            sessionManager.retrySessionStart()
            runCurrent()
            assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals(false, sessionManager.isLoggedIn.value)
            assertFalse(dbStub.isOpen, "rolled back DB must actually be closed")
            assertEquals(
                listOf(
                    "db.open.ok",
                    "msg.start.ok",
                    "ws.connect.fail",
                    "ws.rollback.disconnect",
                    "msg.rollback.stop",
                    "db.rollback.close",
                ),
                steps,
            )
            steps.clear()

            // Recovery: all stages succeed -> Active and isLoggedIn == true.
            failWsConnectStart = false
            sessionManager.retrySessionStart()
            runCurrent()
            val active = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, active.userId)
            assertEquals(true, sessionManager.isLoggedIn.value)
            assertTrue(dbStub.isOpen)
            assertEquals(listOf("db.open.ok", "msg.start.ok", "ws.connect.ok"), steps)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun teardownFailurePreventsDatabaseCloseAndAccountSwitchUntilWorkersStop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(mockk(relaxed = true), steps)
        var failStopSession = true

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow
        coEvery { authRepository.logout() } coAnswers {
            steps += "auth.logout"
            loggedInFlow.value = false
        }

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers {
            if (failStopSession) {
                steps += "msg.stop.fail"
                error("Consumer failed to stop")
            }
            steps += "msg.stop.ok"
        }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()
            assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertTrue(dbStub.isOpen)
            steps.clear()

            // Attempt account switch (userId -> secondUserId) while stopSession throws:
            // must NOT close the old database or open the new user's database.
            userFlow.value = secondUser
            runCurrent()

            assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals(false, sessionManager.isLoggedIn.value)
            assertTrue(dbStub.isOpen, "old database must not be closed while old consumer failed to stop")
            assertEquals(userId, dbStub.currentUserId, "must not switch database to secondUser while teardown failed")
            assertEquals(listOf("ws.disconnect", "msg.stop.fail"), steps)
            steps.clear()

            // Once stopSession succeeds, retrySessionStart cleanly closes the old DB and starts secondUser.
            failStopSession = false
            sessionManager.retrySessionStart()
            runCurrent()

            val active = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, active.userId)
            assertEquals(secondUserId, dbStub.currentUserId)
            assertEquals(1, dbStub.effectiveCloseCount, "old user DB is closed once teardown succeeds")
            assertEquals(listOf("ws.disconnect", "msg.stop.ok", "db.close", "db.open", "msg.start", "ws.connect"), steps)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun targetChangeDuringSuspendedStartupRollsBackWithoutPublishingStaleActive() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val connectGate = CompletableDeferred<Unit>()
        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(mockk(relaxed = true), steps)
        val observedActiveUsers = mutableListOf<Uuid>()

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        var firstConnect = true
        coEvery { realtimeApi.connect() } coAnswers {
            steps += "ws.connect"
            if (firstConnect) {
                firstConnect = false
                connectGate.await()
            }
        }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            val stateCollector = launch {
                sessionManager.sessionState.collect { state ->
                    if (state is SessionState.Active) observedActiveUsers += state.userId
                }
            }
            runCurrent()
            assertIs<SessionState.Starting>(sessionManager.sessionState.value)

            // Switch target user to secondUser while userId's startup is still suspended at connectGate.
            userFlow.value = secondUser
            runCurrent()

            connectGate.complete(Unit)
            runCurrent()

            val active = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, active.userId)
            assertEquals(
                listOf(secondUserId),
                observedActiveUsers,
                "stale startup for the first user must be rolled back before Active is ever published",
            )
            assertEquals(secondUserId, dbStub.currentUserId)
            assertEquals(1, dbStub.effectiveCloseCount, "rolled back first user DB must be closed")
            stateCollector.cancel()
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun inFlightSendAndManualRetryAreCancelledOnLogoutAndRefusedAfterwards() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(
            ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null)
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
        val dao = MessageDaoImpl({ db }, dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 64)
        val connectionStates = MutableStateFlow<ConnectionState>(ConnectionState.Connected)

        val sendGateEntered = CompletableDeferred<Unit>()
        val allowSendCleanup = CompletableDeferred<Unit>()
        val dbStub = StrictDatabaseStub(db)

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow
        coEvery { authRepository.logout() } coAnswers { loggedInFlow.value = false }

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.events } returns events
        every { realtimeApi.connectionState } returns connectionStates
        coEvery { realtimeApi.connect() } returns Unit
        coEvery { realtimeApi.disconnect() } coAnswers { connectionStates.value = ConnectionState.Idle }
        coEvery { realtimeApi.send(any()) } coAnswers {
            sendGateEntered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    allowSendCleanup.await()
                }
            }
        }

        val conversationApi = mockk<ConversationApi>()
        coEvery { conversationApi.syncMessages(any(), any(), any()) } returns emptyList()

        val repo = OfflineFirstMessageRepositoryImpl(
            mediatorFactory = io.mockk.mockk(),
            messageDao = dao,
            userDao = UserDaoImpl({ db }, dispatcher),
            participantDao = ParticipantDaoImpl({ db }, dispatcher),
            messageApi = realtimeApi,
            conversationApi = conversationApi,
            conversationDao = ConversationDaoImpl({ db }, dispatcher),
            groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, dispatcher),
            userSettingDataSource = userSettingDataSource,
            scope = repoScope,
        )

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = repo,
                scope = managerScope,
            )
            runCurrent()
            assertIs<SessionState.Active>(sessionManager.sessionState.value)

            dao.insertMessage(
                Message(firstMessageId, conversationId, sender, MessageCategory.NORMAL, now, content = TextContent("failed"))
                    .toMessageEntity(MessageStatus.FAILED)!!
            )

            var sendCancelled: CancellationException? = null
            val sendJob = launch {
                try {
                    repo.sendMessage(conversationId, TextContent("in-flight"), null)
                } catch (e: CancellationException) {
                    sendCancelled = e
                }
            }
            runCurrent()
            sendGateEntered.await()

            sessionManager.logout()
            runCurrent()
            assertIs<SessionState.Stopping>(sessionManager.sessionState.value)
            assertTrue(dbStub.isOpen)

            assertEquals(Err(MessageError.PermissionDenied), repo.retryMessage(firstMessageId))

            allowSendCleanup.complete(Unit)
            runCurrent()
            sendJob.join()
            assertNotNull(sendCancelled, "in-flight send must be cancelled by logout")
            assertEquals(SessionState.Idle, sessionManager.sessionState.value)

            val countAfterStop = dao.getMessagesPaged(conversationId, Instant.DISTANT_FUTURE, 100).first().size
            assertEquals(Err(MessageError.PermissionDenied), repo.sendMessage(conversationId, TextContent("late"), null))
            assertEquals(Err(MessageError.PermissionDenied), repo.retryMessage(firstMessageId))
            assertEquals(countAfterStop, dao.getMessagesPaged(conversationId, Instant.DISTANT_FUTURE, 100).first().size)
        } finally {
            repo.stopSession()
            managerScope.cancel()
            repoScope.cancel()
            driver.close()
        }
    }

    @Test
    fun sameAccountReloginAdvancesGenerationAndKeepsNewDatabaseOpen() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        db.usersQueries.upsertUser(UserEntity(userId, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
        db.conversationsQueries.upsertConversation(
            ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null)
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
        val dao = MessageDaoImpl({ db }, dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 64)
        val connectionStates = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val dbStub = StrictDatabaseStub(db)

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow
        coEvery { authRepository.logout() } coAnswers { loggedInFlow.value = false }

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.events } returns events
        every { realtimeApi.connectionState } returns connectionStates
        coEvery { realtimeApi.connect() } coAnswers { connectionStates.value = ConnectionState.Connected }
        coEvery { realtimeApi.disconnect() } coAnswers { connectionStates.value = ConnectionState.Idle }
        coEvery { realtimeApi.send(any()) } returns true

        val conversationApi = mockk<ConversationApi>()
        coEvery { conversationApi.syncMessages(any(), any(), any()) } returns emptyList()

        val repo = OfflineFirstMessageRepositoryImpl(
            mediatorFactory = io.mockk.mockk(),
            messageDao = dao,
            userDao = UserDaoImpl({ db }, dispatcher),
            participantDao = ParticipantDaoImpl({ db }, dispatcher),
            messageApi = realtimeApi,
            conversationApi = conversationApi,
            conversationDao = ConversationDaoImpl({ db }, dispatcher),
            groupJoinRequestDao = GroupJoinRequestDaoImpl({ db }, dispatcher),
            userSettingDataSource = userSettingDataSource,
            scope = repoScope,
        )

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = repo,
                scope = managerScope,
            )
            runCurrent()
            val firstActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)

            sessionManager.logout()
            runCurrent()
            assertEquals(SessionState.Idle, sessionManager.sessionState.value)

            // The previous database is already closed; the same user starts a fresh session.
            advanceTimeBy(100L)
            loggedInFlow.value = true
            runCurrent()

            val secondActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertNotEquals(firstActive.generation, secondActive.generation, "re-login must advance generation")
            assertEquals(2, dbStub.openedGenerations.size)

            // Advancing time must not close the reopened session.
            advanceTimeBy(600L)
            runCurrent()

            assertEquals(1, dbStub.effectiveCloseCount, "only the previous session database is closed; the reopened session stays active")
            assertTrue(dbStub.isOpen)
            assertEquals(secondActive, sessionManager.sessionState.value)

            events.emit(receivedEvent(secondMessageId, seq = 1L))
            runCurrent()
            assertNotNull(dao.getMessageById(secondMessageId).first())
        } finally {
            repo.stopSession()
            managerScope.cancel()
            repoScope.cancel()
            driver.close()
        }
    }

    @Test
    fun cancelledStartupWithRollbackFailureEntersStartFailedPropagatesCancellationAndBlocksBypass() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(false)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(mockk(relaxed = true), steps)

        var connectMode = "throw_error"
        var failStopSession = false
        val connectSuspended = CompletableDeferred<Unit>()

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers {
            steps += "ws.connect.$connectMode"
            when (connectMode) {
                "throw_error" -> error("connect failed")
                "suspend_for_cancel" -> {
                    connectSuspended.complete(Unit)
                    awaitCancellation()
                }
                else -> Unit
            }
        }
        coEvery { realtimeApi.disconnect() } coAnswers {
            steps += "ws.disconnect"
        }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers {
            steps += "msg.start"
        }
        coEvery { messageRepository.stopSession() } coAnswers {
            if (failStopSession) {
                steps += "msg.stop.fail"
                error("rollback stopSession failed")
            }
            steps += "msg.stop.ok"
        }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()
            assertEquals(SessionState.Idle, sessionManager.sessionState.value)
            steps.clear()

            // 1. Ordinary startup exception + rollback stop failure:
            //    must preserve both the primary startup cause and the suppressed rollback failure,
            //    and keep the database bound rather than closing it while workers failed to stop.
            failStopSession = true
            loggedInFlow.value = true
            runCurrent()

            val ordinaryFailed = assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals("connect failed", ordinaryFailed.cause.message)
            assertTrue(
                ordinaryFailed.cause.suppressedExceptions.any { it.message == "rollback stopSession failed" },
                "ordinary startup failure must preserve rollback failure as suppressed exception",
            )
            assertTrue(dbStub.isOpen, "DB must remain open when rollback stopSession failed")
            steps.clear()

            // Let cleanup succeed as we enter the next startup attempt, then cancel startup mid-flight
            // while making rollback stopSession fail again.
            failStopSession = false
            connectMode = "suspend_for_cancel"
            val expectedCancellation = CancellationException("caller cancelled startup")
            var propagatedCancellation: CancellationException? = null

            val reconcileJob = launch(dispatcher) {
                try {
                    sessionManager.reconcileNow(forceRetryAfterFailure = true)
                } catch (e: CancellationException) {
                    propagatedCancellation = e
                    throw e
                }
            }
            runCurrent()
            connectSuspended.await()
            assertIs<SessionState.Starting>(sessionManager.sessionState.value)
            val cancelledAttemptGen = (sessionManager.sessionState.value as SessionState.Starting).generation

            // Now fail rollback stopSession when the in-flight startup is cancelled.
            failStopSession = true
            reconcileJob.cancel(expectedCancellation)
            runCurrent()

            val caughtCancellation = assertNotNull(
                propagatedCancellation,
                "original CancellationException must still propagate to the caller",
            )
            assertEquals("caller cancelled startup", caughtCancellation.message)
            assertTrue(
                caughtCancellation.suppressedExceptions.any { it.message == "rollback stopSession failed" },
                "cancelled startup must attach rollback failure as suppressed on the CancellationException",
            )
            val cancelRollbackFailed = assertIs<SessionState.StartFailed>(
                sessionManager.sessionState.value,
                "startup cancellation with failed rollback must enter StartFailed, NOT Idle",
            )
            assertEquals(userId, cancelRollbackFailed.userId)
            assertEquals(cancelledAttemptGen, cancelRollbackFailed.generation)
            assertEquals("rollback stopSession failed", cancelRollbackFailed.cause.message)
            assertEquals(false, sessionManager.isLoggedIn.value)
            assertTrue(dbStub.isOpen, "DB must not be closed when rollback stopSession failed")
            steps.clear()

            // Attempting to switch accounts while rollback stopSession still fails must NOT
            // bypass StartFailed into a new session (which would have happened if state were Idle).
            userFlow.value = secondUser
            runCurrent()

            assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals(userId, dbStub.currentUserId, "must not switch DB to secondUser while old workers fail to stop")
            assertEquals(listOf("ws.disconnect", "msg.stop.fail"), steps)
            steps.clear()

            // Once stopSession succeeds, retrySessionStart closes the bound first-user DB by its
            // resource generation and starts the second user cleanly.
            failStopSession = false
            connectMode = "ok"
            sessionManager.retrySessionStart()
            runCurrent()

            val active = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, active.userId)
            assertEquals(secondUserId, dbStub.currentUserId)
            assertTrue(
                dbStub.closeAttempts.contains(userId to cancelledAttemptGen),
                "recovery must close the cancelled startup's DB using its resourceGeneration: ${dbStub.closeAttempts}",
            )
            assertEquals(
                listOf("ws.disconnect", "msg.stop.ok", "db.close", "db.open", "msg.start", "ws.connect.ok"),
                steps,
            )
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun passiveLogoutClosesAfterBarrierWithResourceGenerationAndRecoversFromFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(mockk(relaxed = true), steps)
        var failDbClose = true

        val databaseHolder = mockk<DatabaseHolder>()
        coEvery { databaseHolder.stopDatabaseSession() } returns Unit
        coEvery { databaseHolder.getOrCreateDatabase(any(), any()) } coAnswers {
            dbStub.holder.getOrCreateDatabase(firstArg<Uuid>(), secondArg<Long?>())
        }
        coEvery { databaseHolder.closeDatabaseIfCurrent(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>()
            if (failDbClose) {
                steps += "db.close.fail($u@$g)"
                error("database close failed")
            }
            dbStub.holder.closeDatabaseIfCurrent(u, g)
        }

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = databaseHolder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()
            val firstActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertTrue(dbStub.isOpen)
            steps.clear()

            // Passive logout (auth state flips to false without calling sessionManager.logout()):
            // workers stop immediately and state enters Idle, but DB close is delayed by the 500ms grace period.
            loggedInFlow.value = false
            runCurrent()

            // Close is attempted immediately after the database stop barrier.
            val closeFailed = assertIs<SessionState.StartFailed>(
                sessionManager.sessionState.value,
                "delayed DB close failure must transition to observable StartFailed state",
            )
            assertEquals(userId, closeFailed.userId)
            assertEquals("database close failed", closeFailed.cause.message)
            assertEquals(
                listOf("ws.disconnect", "msg.stop", "db.close.fail($userId@${firstActive.generation})"),
                steps,
            )
            assertTrue(dbStub.isOpen)
            steps.clear()

            // Recovery path 1: retrySessionStart while still logged out retries closing the DB
            // with the preserved resourceGeneration and settles into Idle.
            failDbClose = false
            sessionManager.retrySessionStart()
            runCurrent()

            assertEquals(SessionState.Idle, sessionManager.sessionState.value)
            assertFalse(dbStub.isOpen, "DB must be closed after retrySessionStart succeeds")
            assertEquals<List<Pair<Uuid, Long?>>>(listOf(userId to firstActive.generation), dbStub.closeAttempts)
            assertEquals(1, dbStub.effectiveCloseCount)
            steps.clear()

            // Recovery path 2: verify the long-lived auth collector coroutine is still alive after
            // the earlier close exception, and also recovers when re-logging in directly after a close failure.
            loggedInFlow.value = true
            runCurrent()
            val secondActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, secondActive.userId)
            steps.clear()

            failDbClose = true
            loggedInFlow.value = false
            runCurrent()
            advanceTimeBy(600L)
            runCurrent()
            assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            steps.clear()

            // Re-login as secondUser directly via auth/user flows (without calling retrySessionStart):
            // collector is alive, closes first user's DB with secondActive.generation, and starts secondUser.
            failDbClose = false
            userFlow.value = secondUser
            loggedInFlow.value = true
            runCurrent()

            val thirdActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, thirdActive.userId)
            assertEquals(secondUserId, dbStub.currentUserId)
            assertEquals<List<Pair<Uuid, Long?>>>(
                listOf(userId to firstActive.generation, userId to secondActive.generation),
                dbStub.closeAttempts,
            )
            assertEquals(2, dbStub.effectiveCloseCount)
            assertEquals(listOf("ws.disconnect", "msg.stop", "db.open", "msg.start", "ws.connect").let {
                listOf("ws.disconnect", "msg.stop", "db.close", "db.open", "msg.start", "ws.connect")
            }, steps)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun sameAccountNewCredentialGenerationRestartsSessionWhileTokenRefreshInSameGenerationDoesNot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val sessionSnapshotFlow = MutableStateFlow(
            AuthSessionSnapshot(
                generation = 1L,
                user = currentUser,
                jwtToken = "jwt-v1",
                refreshToken = "refresh-v1",
            )
        )
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()
        val dbStub = StrictDatabaseStub(mockk(relaxed = true), steps)

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthSession() } returns sessionSnapshotFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()

            val firstActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, firstActive.userId)
            assertEquals(listOf("db.open", "msg.start", "ws.connect"), steps)
            steps.clear()

            // 1. Token refresh within the SAME credential generation (generation == 1L):
            //    must NOT restart workers or advance SessionState.Active.generation.
            sessionSnapshotFlow.value = AuthSessionSnapshot(
                generation = 1L,
                user = currentUser,
                jwtToken = "jwt-v1-refreshed",
                refreshToken = "refresh-v1-rotated",
            )
            runCurrent()
            assertEquals(firstActive, sessionManager.sessionState.value)
            assertTrue(steps.isEmpty(), "token rotation within the same credential generation must not restart session: $steps")

            // 2. Same-account re-login (same userId, credential generation advances 1L -> 2L):
            //    must stop old workers and start a fresh session generation.
            sessionSnapshotFlow.value = AuthSessionSnapshot(
                generation = 2L,
                user = currentUser,
                jwtToken = "jwt-v2-new-login",
                refreshToken = "refresh-v2-new-login",
            )
            runCurrent()

            val secondActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, secondActive.userId)
            assertNotEquals(firstActive.generation, secondActive.generation)
            assertEquals(
                listOf("ws.disconnect", "msg.stop", "db.open", "msg.start", "ws.connect"),
                steps,
                "same-account new credential generation must tear down old workers and start a new session",
            )
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun loginSuccessForUserBWhileUserAWorkersStillStoppingDoesNotPrematurelyReplaceDatabaseAndSeedsUserOnNewSessionStart() = runTest {
        val driver1 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driver2 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db1 = createDatabase { schema -> schema.create(driver1).await(); driver1 }
        val db2 = createDatabase { schema -> schema.create(driver2).await(); driver2 }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val tempDir = Files.createTempDirectory("session-db-ownership-")
        val storeFile = tempDir.resolve("test.preferences_pb").toFile()
        val (authTokenDataSource, userSettingDataSource) = createPreferenceDataSources(
            filePath = storeFile.absolutePath,
            scope = storeScope,
            json = ProjectJson,
        )

        val steps = mutableListOf<String>()
        var activeDbUserId: Uuid? = null
        var activeDbGen: Long = 0L
        var isDbOpen = false
        val openedDatabases = mutableListOf<Pair<Uuid, Long?>>()

        val databaseHolder = mockk<DatabaseHolder>()
        coEvery { databaseHolder.stopDatabaseSession() } returns Unit
        coEvery { databaseHolder.getOrCreateDatabase(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>()
            openedDatabases += u to g
            activeDbUserId = u
            activeDbGen = g ?: 0L
            isDbOpen = true
            steps += "db.open($u)"
            if (u == userId) db1 else db2
        }
        coEvery { databaseHolder.closeDatabaseIfCurrent(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>()
            if (isDbOpen && activeDbUserId == u && (g == null || activeDbGen == g)) {
                isDbOpen = false
                activeDbUserId = null
                activeDbGen = 0L
                steps += "db.close($u)"
            }
        }

        val authApi = mockk<AuthApi>(relaxed = true)
        coEvery { authApi.login("alice@example.com", "pw1") } returns AuthResponse(
            accessToken = "jwt-alice",
            refreshToken = "refresh-alice",
            user = currentUser,
        )
        coEvery { authApi.login("bob@example.com", "pw2") } returns AuthResponse(
            accessToken = "jwt-bob",
            refreshToken = "refresh-bob",
            user = secondUser,
        )

        val authRepository = AuthRepositoryImpl(
            authTokenDataSource = authTokenDataSource,
            userSettingDataSource = userSettingDataSource,
            authApi = authApi,
        )

        val user1DisconnectEntered = CompletableDeferred<Unit>()
        val allowUser1StopToFinish = CompletableDeferred<Unit>()
        var blockDisconnect = false

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers {
            steps += "ws.disconnect.start"
            if (blockDisconnect) {
                user1DisconnectEntered.complete(Unit)
                allowUser1StopToFinish.await()
            }
            steps += "ws.disconnect.done"
        }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = databaseHolder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()
            assertEquals(SessionState.Idle, sessionManager.sessionState.value)
            assertNull(db1.usersQueries.getUserById(userId).executeAsOneOrNull())
            assertNull(db2.usersQueries.getUserById(secondUserId).executeAsOneOrNull())
            steps.clear()

            // 1. Alice logs in via real AuthRepositoryImpl: SessionManager opens db1 and seeds Alice's user row.
            assertEquals(Ok(currentUser), authRepository.login("alice@example.com", "pw1"))
            runCurrent()

            val aliceActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, aliceActive.userId)
            assertEquals(userId, activeDbUserId)
            assertNotNull(
                db1.usersQueries.getUserById(userId).executeAsOneOrNull(),
                "SessionManager must seed the authenticated user into the newly bound database",
            )
            steps.clear()

            // 2. While Alice's session workers are slow to stop, Bob logs in via real AuthRepositoryImpl.
            //    AuthRepositoryImpl must NOT touch DatabaseHolder or seed Bob before Alice's workers stop.
            blockDisconnect = true
            assertEquals(Ok(secondUser), authRepository.login("bob@example.com", "pw2"))
            runCurrent()
            user1DisconnectEntered.await()

            assertIs<SessionState.Stopping>(sessionManager.sessionState.value)
            assertEquals(
                userId,
                activeDbUserId,
                "Bob's login must NOT prematurely replace Alice's database while Alice's workers are still stopping",
            )
            assertTrue(isDbOpen)
            assertEquals<List<Pair<Uuid, Long?>>>(listOf(userId to aliceActive.generation), openedDatabases)
            assertNull(
                db2.usersQueries.getUserById(secondUserId).executeAsOneOrNull(),
                "Bob's database must not be opened or seeded before Alice's workers exit",
            )

            // 3. Once Alice's workers finish stopping, SessionManager closes Alice's DB, opens Bob's DB,
            //    and seeds Bob's user row into Bob's newly bound database.
            allowUser1StopToFinish.complete(Unit)
            runCurrent()

            val bobActive = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, bobActive.userId)
            assertEquals(secondUserId, activeDbUserId)
            assertNotNull(
                db2.usersQueries.getUserById(secondUserId).executeAsOneOrNull(),
                "Bob's user row must be seeded into Bob's database once SessionManager binds it",
            )
            assertEquals(
                listOf(
                    "ws.disconnect.start",
                    "ws.disconnect.done",
                    "msg.stop",
                    "db.close($userId)",
                    "db.open($secondUserId)",
                    "msg.start",
                    "ws.connect",
                ),
                steps,
            )
        } finally {
            managerScope.cancel()
            storeScope.cancel()
            driver1.close()
            driver2.close()
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    @Test
    fun newSessionUserInitializationFailureRollsBackDatabaseWithoutLeavingStaleBinding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()

        var failUserSeed = true
        val usersQueries = mockk<UsersQueries>(relaxed = true)
        coEvery { usersQueries.upsertUser(any()) } coAnswers {
            if (failUserSeed) {
                steps += "db.seedUser.fail"
                error("Failed to seed authenticated user into session database")
            }
            steps += "db.seedUser.ok"
            1L
        }
        val mockDb = mockk<ChatDatabase>(relaxed = true) {
            every { this@mockk.usersQueries } returns usersQueries
        }
        val dbStub = StrictDatabaseStub(mockDb, steps)

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = dbStub.holder,
                messageRepository = messageRepository,
                scope = managerScope,
            )
            runCurrent()

            // User seeding fails after DB open -> rolls back and closes the database, leaving no stale binding.
            val failed = assertIs<SessionState.StartFailed>(sessionManager.sessionState.value)
            assertEquals("Failed to seed authenticated user into session database", failed.cause.message)
            assertFalse(dbStub.isOpen, "database must be closed when user initialization fails")
            assertNull(dbStub.currentUserId, "no stale database binding may remain after failed initialization")
            assertEquals(1, dbStub.effectiveCloseCount)
            assertEquals(listOf("db.open", "db.seedUser.fail", "ws.disconnect", "db.close"), steps)
            steps.clear()

            // Once user seeding succeeds, retrySessionStart cleanly opens DB, seeds user, and enters Active.
            failUserSeed = false
            sessionManager.retrySessionStart()
            runCurrent()

            val active = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, active.userId)
            assertTrue(dbStub.isOpen)
            assertEquals(userId, dbStub.currentUserId)
            assertEquals(listOf("db.open", "db.seedUser.ok", "msg.start", "ws.connect"), steps)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun inFlightUserRepositoryFetchPreventsDatabaseCloseOnAccountSwitchAndDoesNotCrossWriteIntoNewUserDatabase() = runTest {
        val driver1 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driver2 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db1 = createDatabase { schema -> schema.create(driver1).await(); driver1 }
        val db2 = createDatabase { schema -> schema.create(driver2).await(); driver2 }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()

        var activeDb: ChatDatabase? = null
        var activeDbUserId: Uuid? = null
        var activeDbGen = 0L
        var db1CloseCount = 0

        val databaseHolder = mockk<DatabaseHolder>()
        coEvery { databaseHolder.stopDatabaseSession() } returns Unit
        coEvery { databaseHolder.getOrCreateDatabase(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>() ?: 0L
            activeDbUserId = u
            activeDbGen = g
            activeDb = if (u == userId) db1 else db2
            steps += "db.open($u)"
            activeDb!!
        }
        coEvery { databaseHolder.closeDatabaseIfCurrent(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>()
            if (activeDb != null && activeDbUserId == u && (g == null || activeDbGen == g)) {
                if (u == userId) db1CloseCount++
                activeDb = null
                activeDbUserId = null
                activeDbGen = 0L
                steps += "db.close($u)"
            }
        }

        // Dynamic process-singleton UserDao that resolves activeDb on use, matching DaosModule.
        val dynamicUserDao = UserDaoImpl(
            dbProvider = { activeDb ?: error("Database is not initialized! User is not logged in.") },
            ioContext = dispatcher,
        )

        val contactId = Uuid.parse("00000000-0000-0000-0000-000000000077")
        val contactForUser1 = User(contactId, "contact_from_u1", null, "From U1", null, null, now, now, null)
        val contactForUser2 = User(contactId, "contact_from_u2", null, "From U2", null, null, now, now, null)

        val fetchEntered = CompletableDeferred<Unit>()
        val allowFetchCleanup = CompletableDeferred<Unit>()
        var callCount = 0

        val userApi = mockk<UserApi>()
        coEvery { userApi.getUserById(contactId) } coAnswers {
            val call = ++callCount
            if (call == 1) {
                steps += "user.fetch.start"
                fetchEntered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        steps += "user.fetch.cleanup.wait"
                        allowFetchCleanup.await()
                        steps += "user.fetch.cleanup.done"
                    }
                }
                contactForUser1
            } else {
                steps += "user.fetch.u2"
                contactForUser2
            }
        }

        val userRepository = UserRepositoryImpl(
            userSettingDataSource = mockk<UserSettingDataSource>(relaxed = true),
            userDao = dynamicUserDao,
            userApi = userApi,
            fileApi = mockk<FileApi>(relaxed = true),
            scope = repoScope,
        )

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = databaseHolder,
                messageRepository = messageRepository,
                userRepository = userRepository,
                scope = managerScope,
            )
            runCurrent()
            assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(userId, activeDbUserId)
            steps.clear()

            // 1. Start an in-flight fetchUserDetail in User 1's session.
            var cancelledEx: CancellationException? = null
            val fetchJob = launch(dispatcher) {
                try {
                    userRepository.fetchUserDetail(contactId)
                } catch (e: CancellationException) {
                    cancelledEx = e
                }
            }
            runCurrent()
            fetchEntered.await()

            // 2. Switch account to secondUser while fetchUserDetail is still in flight.
            userFlow.value = secondUser
            runCurrent()

            // Must still be in Stopping, with User 1's database still open because fetchUserDetail's
            // cleanup has not finished yet.
            assertIs<SessionState.Stopping>(sessionManager.sessionState.value)
            assertEquals(0, db1CloseCount, "User 1 database must not be closed before in-flight UserRepository operation exits")
            assertEquals(userId, activeDbUserId, "must not open User 2 database while User 1 operation cleanup is still running")
            assertEquals(Err(UserError.PermissionDenied), userRepository.fetchUserDetail(contactId))

            // 3. Release cleanup gate: User 1 operation finishes -> db1 closes -> db2 opens -> User 2 session starts.
            allowFetchCleanup.complete(Unit)
            runCurrent()
            fetchJob.join()

            assertNotNull(cancelledEx, "in-flight fetchUserDetail must be cancelled by account switch")
            val active2 = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, active2.userId)
            assertEquals(1, db1CloseCount)
            assertEquals(secondUserId, activeDbUserId)
            assertTrue(
                steps.indexOf("user.fetch.cleanup.done") < steps.indexOf("db.close($userId)"),
                "in-flight UserRepository cleanup must finish before db1 is closed: $steps",
            )
            assertNull(db1.usersQueries.getUserById(contactId).executeAsOneOrNull())
            assertNull(db2.usersQueries.getUserById(contactId).executeAsOneOrNull())

            // 4. Now fetchUserDetail in User 2's session binds to db2 and writes only into db2.
            assertEquals(Ok(contactForUser2), userRepository.fetchUserDetail(contactId))
            assertEquals("contact_from_u2", db2.usersQueries.getUserById(contactId).executeAsOneOrNull()?.username)
            assertNull(db1.usersQueries.getUserById(contactId).executeAsOneOrNull())
        } finally {
            userRepository.stopSession()
            managerScope.cancel()
            repoScope.cancel()
            driver1.close()
            driver2.close()
        }
    }

    @Test
    fun inFlightContactRepositorySyncPreventsDatabaseCloseOnAccountSwitchAndSyncsIntoNewSessionDatabase() = runTest {
        val driver1 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val driver2 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db1 = createDatabase { schema -> schema.create(driver1).await(); driver1 }
        val db2 = createDatabase { schema -> schema.create(driver2).await(); driver2 }

        val dispatcher = StandardTestDispatcher(testScheduler)
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

        val loggedInFlow = MutableStateFlow(true)
        val userFlow = MutableStateFlow<User?>(currentUser)
        val steps = mutableListOf<String>()

        var activeDb: ChatDatabase? = null
        var activeDbUserId: Uuid? = null
        var activeDbGen = 0L
        var db1CloseCount = 0

        val databaseHolder = mockk<DatabaseHolder>()
        coEvery { databaseHolder.stopDatabaseSession() } returns Unit
        coEvery { databaseHolder.getOrCreateDatabase(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>() ?: 0L
            activeDbUserId = u
            activeDbGen = g
            activeDb = if (u == userId) db1 else db2
            steps += "db.open($u)"
            activeDb!!
        }
        coEvery { databaseHolder.closeDatabaseIfCurrent(any(), any()) } coAnswers {
            val u = firstArg<Uuid>()
            val g = secondArg<Long?>()
            if (activeDb != null && activeDbUserId == u && (g == null || activeDbGen == g)) {
                if (u == userId) db1CloseCount++
                activeDb = null
                activeDbUserId = null
                activeDbGen = 0L
                steps += "db.close($u)"
            }
        }

        val dbProvider: () -> ChatDatabase = {
            activeDb ?: error("Database is not initialized! User is not logged in.")
        }
        val dynamicContactDao = ContactDaoImpl(dbProvider = dbProvider, ioContext = dispatcher)
        val dynamicUserDao = UserDaoImpl(dbProvider = dbProvider, ioContext = dispatcher)

        val friendId = Uuid.parse("00000000-0000-0000-0000-000000000088")
        val friendForU1 = User(friendId, "friend_u1", null, "Friend U1", null, null, now, now, null)
        val friendForU2 = User(friendId, "friend_u2", null, "Friend U2", null, null, now, now, null)
        val contactEntryForU1 = Contact(userId, friendId, ContactStatus.FRIEND, "Nick", "Alias", now, now)
        val contactEntryForU2 = Contact(secondUserId, friendId, ContactStatus.FRIEND, "Nick", "Alias", now, now)

        val syncEntered = CompletableDeferred<Unit>()
        val allowSyncCleanup = CompletableDeferred<Unit>()
        var callCount = 0

        val contactApi = mockk<ContactApi>()
        coEvery { contactApi.getContacts() } coAnswers {
            val call = ++callCount
            if (call == 1) {
                steps += "contact.sync.start"
                syncEntered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        steps += "contact.sync.cleanup.wait"
                        allowSyncCleanup.await()
                        steps += "contact.sync.cleanup.done"
                    }
                }
                listOf(contactEntryForU1 to friendForU1)
            } else {
                steps += "contact.sync.u2"
                listOf(contactEntryForU2 to friendForU2)
            }
        }

        val contactRepository = ContactRepositoryImpl(
            contactApi = contactApi,
            contactDao = dynamicContactDao,
            userDao = dynamicUserDao,
            scope = repoScope,
        )

        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeIsLoggedIn() } returns loggedInFlow

        val userSettingDataSource = mockk<UserSettingDataSource>()
        every { userSettingDataSource.user } returns userFlow

        val realtimeApi = mockk<RealtimeApi>()
        every { realtimeApi.connectionState } returns MutableStateFlow(ConnectionState.Idle)
        coEvery { realtimeApi.connect() } coAnswers { steps += "ws.connect" }
        coEvery { realtimeApi.disconnect() } coAnswers { steps += "ws.disconnect" }

        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.startSession() } coAnswers { steps += "msg.start" }
        coEvery { messageRepository.stopSession() } coAnswers { steps += "msg.stop" }

        try {
            val sessionManager = SessionManager(
                authRepository = authRepository,
                userSettingDataSource = userSettingDataSource,
                realtimeApi = realtimeApi,
                databaseHolder = databaseHolder,
                messageRepository = messageRepository,
                contactRepository = contactRepository,
                scope = managerScope,
            )
            runCurrent()
            assertIs<SessionState.Active>(sessionManager.sessionState.value)
            steps.clear()

            var cancelledEx: CancellationException? = null
            val syncJob = launch(dispatcher) {
                try {
                    contactRepository.syncFriends()
                } catch (e: CancellationException) {
                    cancelledEx = e
                }
            }
            runCurrent()
            syncEntered.await()

            // Switch account to secondUser while contactRepository.syncFriends() is still in flight.
            userFlow.value = secondUser
            runCurrent()

            assertIs<SessionState.Stopping>(sessionManager.sessionState.value)
            assertEquals(0, db1CloseCount, "User 1 database must not close while ContactRepository sync cleanup is running")
            assertEquals(userId, activeDbUserId)
            assertEquals(Err(ContactError.PermissionDenied), contactRepository.syncFriends())

            allowSyncCleanup.complete(Unit)
            runCurrent()
            syncJob.join()

            assertNotNull(cancelledEx)
            val active2 = assertIs<SessionState.Active>(sessionManager.sessionState.value)
            assertEquals(secondUserId, active2.userId)
            assertEquals(1, db1CloseCount)
            assertTrue(
                steps.indexOf("contact.sync.cleanup.done") < steps.indexOf("db.close($userId)"),
                "in-flight ContactRepository cleanup must finish before db1 is closed: $steps",
            )
            assertNull(db1.contactsQueries.getContactById(friendId).executeAsOneOrNull())
            assertNull(db2.contactsQueries.getContactById(friendId).executeAsOneOrNull())

            // Now syncFriends() in User 2's session writes both UserEntity and ContactEntity into db2.
            assertEquals(Ok(Unit), contactRepository.syncFriends())
            assertEquals("friend_u2", db2.usersQueries.getUserById(friendId).executeAsOneOrNull()?.username)
            assertNotNull(db2.contactsQueries.getContactById(friendId).executeAsOneOrNull())
            assertNull(db1.contactsQueries.getContactById(friendId).executeAsOneOrNull())
        } finally {
            contactRepository.stopSession()
            managerScope.cancel()
            repoScope.cancel()
            driver1.close()
            driver2.close()
        }
    }
}
