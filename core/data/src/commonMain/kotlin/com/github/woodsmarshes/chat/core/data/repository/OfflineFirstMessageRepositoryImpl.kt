package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.common.session.ResourceSession
import com.github.woodsmarshes.chat.core.common.session.SessionExecutor
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.data.model.toConversation
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupOwnerUserEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupProfileEntity
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toPeerParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toUiModel
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.data.paging.MessageMediatorFactory
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDao
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.Normal
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.System
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.SyncBatch
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.GroupJoinRequestEntity
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
class OfflineFirstMessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val participantDao: ParticipantDao,
    private val messageApi: RealtimeApi,
    private val conversationApi: ConversationApi,
    private val conversationDao: ConversationDao,
    private val groupJoinRequestDao: GroupJoinRequestDao,

    // Conversation snapshots are applied before message streams.
    private val conversationRepository: ConversationRepository,
    private val userSettingDataSource: UserSettingDataSource,
    private val scope: CoroutineScope,
    private val mediatorFactory: MessageMediatorFactory,
    private val groupProfileDao: GroupProfileDao? = null,
    private val unreadRefreshCoordinator: UnreadRefreshCoordinator,
    private val userSyncApplier: UserSyncApplier,
) : MessageRepository {
    override val connectionState get() = messageApi.connectionState

    private val log = KotlinLogging.logger {}

    private companion object {
        /** Wait this long after sending before a retry is considered. */
        val RETRY_DELAY = 10.seconds

        /** Typing entries older than this are dropped defensively. */
        const val TYPING_ENTRY_TTL_MS = 60_000L

        /** Periodic sweep interval for the outbox. */
        val RETRY_POLL_INTERVAL = 15.seconds

        /** Give up on unacked messages older than this. */
        val MESSAGE_TTL = 24.hours

        const val SYNC_WINDOW_MESSAGES = 100

        const val SYNC_PAGE_SIZE = 100
        const val MAX_SYNC_PAGES = 40
        val SYNC_POLL_INTERVAL = 30.seconds
    }
    val ownUser = userSettingDataSource.user

    private val sendMutex = Mutex()
    private val syncMutex = Mutex()
    private val syncWakeups = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private val lifecycle = ResourceSession(
        dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
            ?: Dispatchers.Default,
        create = {
            RunningSession(this@OfflineFirstMessageRepositoryImpl,
                scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Default
            )
        },
        prepare = { session ->
            startMessageConsumption(session)
            startOutboxRetryLoop(session)
            startSyncWorker(session)
        },
        beforeStop = { it.job.cancel() },
        dispose = { session ->
            session.job.join()
            unreadRefreshCoordinator.clearSessionState()
            _typingUsers.value = emptyMap()
        },
    )

    private class SessionElement(
        val owner: OfflineFirstMessageRepositoryImpl,
    ) : AbstractCoroutineContextElement(SessionElement) {
        companion object Key : CoroutineContext.Key<SessionElement>
    }

    /**
     * One active session lifecycle. Its [job] is an independent [SupervisorJob]
     * controlled explicitly via [startSession] and [stopSession] (which cancels
     * and awaits [job] before returning), rather than an `invokeOnCompletion`
     * callback on [scope] that would neither propagate cancellation immediately
     * nor wait for session cleanup to finish.
     */
    private class RunningSession(
        repository: OfflineFirstMessageRepositoryImpl,
        dispatcher: CoroutineContext,
    ) {
        val job: CompletableJob = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher + SessionElement(repository))
    }

    private fun isActiveSession(session: RunningSession): Boolean = lifecycle.isCurrent(session)

    override suspend fun startSession() = lifecycle.start()

    override suspend fun stopSession() {
        check(currentCoroutineContext()[SessionElement]?.owner !== this) {
            "stopSession() must not be called from inside a message worker"
        }
        lifecycle.stop()
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getMessages(
        ownUserId: Uuid,
        conversationId: Uuid,
        isGroup: Boolean,
        limit: Int
    ): Flow<PagingData<MessageUiModel>> {
        log.info { "[getMessages] called, conversationId=$conversationId ownUserId=$ownUserId" }
        val pagingDao = (messageDao as? com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl)?.pinForPaging() ?: messageDao
        return Pager(
            config = PagingConfig(
                pageSize = limit,
                initialLoadSize = limit,
                enablePlaceholders = false,
            ),
            remoteMediator = mediatorFactory.create(ownUserId, conversationId, isGroup),
            pagingSourceFactory = {
                pagingDao.pagingSource(
                    conversationId = conversationId,
                    pageSize = limit.toLong()
                )
            }
        ).flow
            .onEach {
                log.info { "[getMessages] Pager emitted new PagingData" }
            }
            .map {
                log.info { "[getMessages] mapping PagingData to UiModel" }
                it.map(KeyedMessagesWithRelations::toUiModel)
            }
    }

    override suspend fun sendMessage(
        conversationId: Uuid,
        content: MessageContent,
        replyToMessageId: Uuid?,
    ): Result<Unit, MessageError> {
        return try {
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull()
                    ?: return@execute Err(MessageError.PermissionDenied).also {
                        log.warn { "[sendMessage] PermissionDenied: ownUser is null" }
                    }

                val requestId = Uuid.generateV7()
                log.info { "[sendMessage] sending, requestId=$requestId conversationId=$conversationId" }

                val request = MessageRequest.Send(
                    senderId = currentUser.id,
                    conversationId = conversationId,
                    content = content,
                    requestId = requestId.toString(),
                    replyToMessageId = replyToMessageId,
                )

                messageDao.transaction {
                    messageDao.insertMessage(
                        message = MessageEntity(
                            id = requestId,
                            conversation_id = conversationId,
                            user_id = currentUser.id,
                            category = when (content) {
                                is System -> MessageCategory.SYSTEM
                                is Normal -> MessageCategory.NORMAL
                            },
                            render_type = determineRenderType(content),
                            in_timeline = true,
                            content = content,
                            reply_to_message_id = replyToMessageId,
                            created_at = Clock.System.now(),
                            revoked_at = null,
                            local_send_status = MessageStatus.SENDING
                        )
                    )
                    messageDao.startAttempt(requestId, Clock.System.now())
                    conversationDao.updateLastMessage(
                        id = conversationId,
                        lastMessageId = requestId,
                        updatedAt = Clock.System.now()
                    )
                }
                // The durable outbox row must exist before any acknowledgement.
                sendMutex.withLock {
                    if (messageApi.connectionState.value is ConnectionState.Connected) {
                        messageApi.send(request)
                    }
                }
                Ok(Unit)
            }
        } catch (e: SessionStoppedException) {
            log.warn { "[sendMessage] refused: session is not active" }
            Err(MessageError.PermissionDenied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[sendMessage] exception: ${e.message}" }
            Err(MessageError.Unknown(e.message))
        }
    }

    override suspend fun revokeMessage(messageId: Uuid): Result<Unit, MessageError> {
        return try {
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull()
                    ?: return@execute Err(MessageError.RevokeFailed)

                val request = MessageRequest.Withdraw(
                    senderId = currentUser.id,
                    messageId = messageId
                )

                // Not retried automatically: the caller reports the failure
                // and the user can revoke again once the connection is back.
                if (messageApi.send(request)) Ok(Unit) else Err(MessageError.RevokeFailed)
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[revokeMessage] refused: session is not active" }
            Err(MessageError.PermissionDenied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send realtime request" }
            Err(MessageError.RevokeFailed)
        }
    }

    override suspend fun markAsRead(conversationId: Uuid, messageId: Uuid) {
        try {
            lifecycle.execute { session ->
                val currentUser = ownUser.firstOrNull() ?: return@execute

                val request = MessageRequest.Read(
                    senderId = currentUser.id,
                    conversationId = conversationId,
                    messageId = messageId
                )

                // Receipts are not retried: re-entering the chat re-reports
                // the current last message, so a dropped one only costs a
                // stale unread state until then. Do not touch the local badge
                // when the socket rejected the receipt.
                if (!messageApi.send(request)) {
                    log.warn { "[markAsRead] receipt for $messageId was not delivered" }
                    return@execute
                }
                // Sending over the WebSocket does not mean the server has
                // committed the read position yet, and reading up to messageId
                // may leave newer messages unread. Schedule an authoritative
                // detail refresh (which also covers duplicate UNCHANGED reads
                // where the server suppresses the Read broadcast) and let any
                // subsequent Read broadcast invalidate and refresh again.
                if (session.job.isActive && isActiveSession(session)) {
                    unreadRefreshCoordinator.requestRefresh(
                        sessionScope = session.scope,
                        conversationId = conversationId,
                        conversationApi = conversationApi,
                        conversationDao = conversationDao,
                    )
                }
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[markAsRead] refused: session is not active" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send realtime request" }
        }
    }

    // conversationId -> (userId -> last typing-event epoch millis)
    private val _typingUsers = MutableStateFlow<Map<Uuid, Map<Uuid, Long>>>(emptyMap())

    override fun getTypingUsersFlow(conversationId: Uuid): Flow<Map<Uuid, Long>> =
        _typingUsers.map { it[conversationId].orEmpty() }

    override suspend fun sendTyping(conversationId: Uuid, isTyping: Boolean) {
        try {
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull() ?: return@execute

                val request = MessageRequest.Typing(
                    senderId = currentUser.id,
                    conversationId = conversationId,
                    isTyping = isTyping,
                )

                messageApi.send(request)
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[sendTyping] refused: session is not active" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send typing state" }
        }
    }

    private fun handleUserTyping(session: RunningSession, event: MessageEventResponse.UserTyping) {
        if (!session.job.isActive || !isActiveSession(session)) return
        val now = Clock.System.now().toEpochMilliseconds()
        _typingUsers.update { all ->
            // Drop stale entries in case a "stopped typing" event was lost.
            val perConversation = all[event.conversationId].orEmpty()
                .filterValues { now - it < TYPING_ENTRY_TTL_MS }
                .toMutableMap()
            if (event.isTyping) {
                perConversation[event.userId] = now
            } else {
                perConversation.remove(event.userId)
            }
            if (perConversation.isEmpty()) {
                all - event.conversationId
            } else {
                all + (event.conversationId to perConversation)
            }
        }
    }

    private suspend fun startMessageConsumption(session: RunningSession) {
        val subscriptionReady = CompletableDeferred<Unit>()
        val job = session.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            log.info { "[ws-consume] starting event consumption" }
            try {
                messageApi.events
                    .onSubscription { subscriptionReady.complete(Unit) }
                    .collect { event ->
                        log.info { "[ws-consume] received event: ${event::class.simpleName}" }
                        when (event) {
                            is MessageEventResponse.Received -> {
                                log.info { "[ws-consume] handling Received: requestId=${event.requestId} senderId=${event.senderId} msgId=${event.message.id}" }
                                handleReceivedMessage(session, event)
                            }
                            is MessageEventResponse.Withdrawn -> {
                                handleWithdrawnMessage(session, event)
                            }
                            is MessageEventResponse.Read -> {
                                handleReadMessage(session, event)
                            }
                            is MessageEventResponse.UserTyping -> {
                                handleUserTyping(session, event)
                            }
                            is ConversationEventResponse.ConversationDeleted -> {
                                runCatching {
                                    conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                                    groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                                }
                            }
                            is ConversationEventResponse.UserLeftConversation -> {
                                runCatching {
                                    val meId = ownUser.firstOrNull()?.id
                                    participantDao.removeParticipant(event.conversationId, event.userId)
                                    if (meId != null && event.userId == meId) {
                                        conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                                        groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                                    }
                                }
                            }
                            is ConversationEventResponse.ConversationCreated -> {
                                syncWakeups.tryEmit(Unit)
                            }
                            is ConversationEventResponse.UserJoinedConversation -> {
                                syncWakeups.tryEmit(Unit)
                            }
                            is ConversationEventResponse.GroupProfileUpdated -> {
                                syncWakeups.tryEmit(Unit)
                            }
                            is ConversationEventResponse.PersonalSettingsUpdated -> {
                                syncWakeups.tryEmit(Unit)
                            }
                            is ConversationEventResponse.GroupJoinRequest -> {
                                // The event payload is complete (id, applicant, message):
                                // record it directly so admin badges update without polling.
                                // Seed semantics: a replayed event must not downgrade an
                                // already-handled row or clobber its message.
                                runCatching {
                                    groupJoinRequestDao.seedGroupJoinRequest(
                                        GroupJoinRequestEntity(
                                            id = event.requestId,
                                            conversation_id = event.conversationId,
                                            applicant_id = event.applicantId,
                                            message = event.message,
                                            status = RequestStatus.PENDING,
                                            handled_by = null,
                                            created_at = event.timestamp,
                                            updated_at = event.timestamp,
                                        )
                                    )
                                }.onFailure { log.warn(it) { "[ws-consume] failed to record GroupJoinRequest ${event.requestId}" } }
                            }
                            is ConversationEventResponse.GroupJoinRequestHandled -> {
                                // Sent only to the applicant: refresh their pending
                                // state in place.
                                runCatching {
                                    groupJoinRequestDao.updateGroupJoinRequestStatus(
                                        id = event.requestId,
                                        status = if (event.approved) RequestStatus.ACCEPTED else RequestStatus.REJECTED,
                                        handledBy = event.handlerId,
                                        updatedAt = event.timestamp,
                                    )
                                }.onFailure { log.warn(it) { "[ws-consume] failed to record GroupJoinRequestHandled ${event.requestId}" } }
                            }
                            is SyncStateResponse.Heads -> syncWakeups.tryEmit(Unit)
                            else -> {
                                log.debug { "[ws-consume] unknown event: ${event::class.simpleName}" }
                            }
                        }
                    }
            } catch (e: CancellationException) {
                subscriptionReady.cancel(e)
                throw e
            } catch (e: Exception) {
                subscriptionReady.completeExceptionally(e)
                log.error(e) { "[ws-consume] error in event consumption: ${e.message}" }
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !subscriptionReady.isCompleted) {
                subscriptionReady.completeExceptionally(cause)
            }
        }
        subscriptionReady.await()
    }

    private fun startSyncWorker(session: RunningSession) {
        session.scope.launch {
        messageApi.connectionState.collectLatest { state ->
            if (state !is ConnectionState.Connected) return@collectLatest
            syncWakeups.tryEmit(Unit)
            // Crash-safe badge recovery: a committed cursor whose refresh died
            // with the previous process left a persisted pending marker (same
            // transaction as the cursor). Re-schedule those refreshes; the
            // marker is cleared once the authoritative value applies.
            try {
                messageDao.getUnreadRefreshPendingConversations().forEach { conversationId ->
                    requestUnreadRefresh(session, conversationId)
                }
            } catch (e: Exception) {
                log.warn(e) { "[sync] failed to restore pending unread refreshes; they stay persisted" }
            }
            val poll = session.scope.launch {
                while (true) {
                    delay(SYNC_POLL_INTERVAL)
                    syncWakeups.emit(Unit)
                }
            }
            try {
                syncWakeups.collect {
                    try {
                        syncHeads(session)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn(e) { "[sync] pull failed; waiting for next wakeup" }
                    }
                }
            } finally {
                poll.cancel()
            }
        }
    }
}

    private suspend fun syncHeads(session: RunningSession) = syncMutex.withLock {
    val me = ownUser.firstOrNull()?.id ?: return@withLock
    val heads = conversationApi.getSyncHeads().entries
        .filter { it.scope != SyncScope.USER || it.id == me }
        .filter { head -> messageDao.getScopeCursor(head.scope.name, head.id) != head.revision }
        .sortedBy { if (it.scope == SyncScope.USER) 0 else 1 }
    val userHead = heads.firstOrNull { it.scope == SyncScope.USER }
    if (userHead != null) {
        // User-scoped entities can seed foreign-key rows needed by conversation sync.
        try {
            syncStream(session, userHead.scope, userHead.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn(failure) { "[sync] user stream failed for ${userHead.id}; continuing with conversation streams" }
        }
    }
    heads.asSequence()
        .filter { it.scope == SyncScope.CONVERSATION }
        .forEach { head ->
            try {
                syncStream(session, head.scope, head.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn(failure) { "[sync] conversation stream failed for ${head.id}; continuing" }
            }
        }
}

    private suspend fun syncStream(session: RunningSession, scope: SyncScope, id: Uuid) {
        val storedRevision = messageDao.getScopeCursor(scope.name, id)
        if (storedRevision == null) {
            syncSnapshot(session, scope, id)
            if (scope == SyncScope.USER) syncDeltaAfterSnapshot(session, scope, id)
            return
        }
        syncDeltaAfterSnapshot(session, scope, id, storedRevision)
    }

    private suspend fun syncDeltaAfterSnapshot(
        session: RunningSession,
        scope: SyncScope,
        id: Uuid,
        initialRevision: Long? = null,
    ) {
        var revision = initialRevision ?: messageDao.getScopeCursor(scope.name, id)
            ?: error("Snapshot did not persist a cursor for $scope/$id")
        repeat(MAX_SYNC_PAGES) {
            val batch = conversationApi.getSyncChanges(scope, id, revision, SYNC_PAGE_SIZE)
            check(batch.scope == scope && batch.id == id && batch.fromRevision == revision) {
                "Sync batch identity/fromRevision mismatch for $scope/$id"
            }
            if (batch.resetRequired) {
                syncSnapshot(session, scope, id)
                if (scope == SyncScope.USER) {
                    revision = messageDao.getScopeCursor(scope.name, id)
                        ?: error("Snapshot did not persist a cursor for $scope/$id")
                    return@repeat
                }
                return
            }
            if (batch.changes.isEmpty()) {
                check(batch.nextRevision == revision) { "Empty sync batch advanced its revision" }
                return
            }
            var expected = revision + 1L
            batch.changes.forEach { change ->
                check(change.revision == expected++) { "Non-contiguous sync journal for $scope/$id" }
            }
            check(batch.nextRevision == batch.changes.last().revision) { "Sync nextRevision mismatch" }
            try {
                applySyncBatch(session, scope, id, batch)
            } catch (reset: UserSyncResetRequiredException) {
                // A historical journal entry has become unapplicable (e.g. an
                // offline join whose membership has lapsed). Re-sync the stream
                // from a controlled snapshot — the authoritative current state
                // replaces both the stale entry and anything after it — then
                // continue delta pulls from the fresh cursor.
                syncSnapshot(session, scope, id)
                syncDeltaAfterSnapshot(session, scope, id)
                return
            }
            revision = batch.nextRevision
            if (!batch.hasMore) return
        }
        error("Sync page budget exhausted for $scope/$id")
    }

    private suspend fun syncSnapshot(session: RunningSession, scope: SyncScope, id: Uuid) {
        val snapshot = conversationApi.getSyncSnapshot(scope, id, SYNC_WINDOW_MESSAGES)
        check(snapshot.scope == scope && snapshot.id == id) { "Sync snapshot identity mismatch" }
        when (scope) {
            SyncScope.USER -> {
                // Prepare REST state before the transaction; never issue HTTP while holding a DB lock.
                val prepared = userSyncApplier.prepareSnapshot(snapshot.events)
                messageDao.transaction {
                    val applied = conversationRepository.applyUserSnapshotConversations(snapshot.conversations)
                    if (applied.isErr) error("Failed to apply USER conversation snapshot: $applied")
                    userSyncApplier.apply(prepared)
                    messageDao.setScopeCursor(scope.name, id, snapshot.revision)
                    // Persist the badge-recovery markers in the SAME commit:
                    // a process death before the scheduled refreshes run must
                    // leave a recoverable state.
                    snapshot.conversations.forEach { response ->
                        messageDao.markUnreadRefreshPending(response.conversationId)
                    }
                }
                // Unread refreshes are scheduled AFTER the commit, never inside
                // the transaction: the coordinator lock must not be taken while
                // a database transaction is held (the detail worker takes them
                // in the opposite order), and in-memory generations must only
                // advance for committed state.
                snapshot.conversations.forEach { response ->
                    requestUnreadRefresh(session, response.conversationId)
                }
            }
                SyncScope.CONVERSATION -> {
                    val me = ownUser.firstOrNull()?.id
                    messageDao.transaction {
                        if (snapshot.messages.isEmpty()) {
                            messageDao.demoteAllTimelineMessages(id)
                        } else {
                            messageDao.demoteTimelineBefore(id, snapshot.messages.minOf { it.id })
                            snapshot.messages.forEach { message ->
                                check(message.toMessageEntity() != null) { "Snapshot contains a message without sender" }
                                persistServerMessage(messageDao, userDao, participantDao, conversationDao, message, null, me)
                            }
                        }
                        messageDao.setScopeCursor(scope.name, id, snapshot.revision)
                        // Committed with the cursor: a process death before the
                        // scheduled refresh runs leaves a recoverable marker.
                        if (snapshot.messages.isNotEmpty()) messageDao.markUnreadRefreshPending(id)
                    }
                    // Journal recovery applied messages without realtime events:
                    // without this refresh the unread badge would keep its
                    // pre-offline value (or a fresh device's default) forever.
                    if (snapshot.messages.isNotEmpty()) requestUnreadRefresh(session, id)
                }
            }
        }

    /** Schedules a coalesced authoritative unread refresh bound to the live session. */
    private suspend fun requestUnreadRefresh(session: RunningSession, conversationId: Uuid) {
        if (session.job.isActive && isActiveSession(session)) {
            unreadRefreshCoordinator.requestRefresh(
                sessionScope = session.scope,
                conversationId = conversationId,
                conversationApi = conversationApi,
                conversationDao = conversationDao,
            )
        }
    }

    private suspend fun applySyncBatch(session: RunningSession, scope: SyncScope, id: Uuid, batch: SyncBatch) {
        if (scope == SyncScope.USER) {
            val prepared = userSyncApplier.prepareBatch(batch.changes.map { it.event })
            messageDao.transaction {
                userSyncApplier.apply(prepared)
                messageDao.setScopeCursor(scope.name, id, batch.nextRevision)
                // A restored Read moved this account's read cursor inside the
                // same transaction; persist the badge-recovery marker with it.
                batch.changes.mapNotNull { (it.event as? MessageEventResponse.Read)?.conversationId }
                    .distinct()
                    .forEach { conversationId -> messageDao.markUnreadRefreshPending(conversationId) }
            }
            // Multi-device read sync (journal recovery path): a restored Read
            // moved this account's last-read cursor without any realtime
            // fan-out, so the badge needs the same coalesced authoritative
            // refresh the live Read handler performs.
            batch.changes.mapNotNull { (it.event as? MessageEventResponse.Read)?.conversationId }
                .distinct()
                .forEach { conversationId -> requestUnreadRefresh(session, conversationId) }
            return
        }
        val me = ownUser.firstOrNull()?.id
        val metadataRefreshes = linkedSetOf<Uuid>()
        // Fetch metadata before checkpointing. Any failed refresh leaves the cursor behind.
        batch.changes.forEach { change ->
            when (val event = change.event) {
                is ConversationEventResponse.ConversationDeleted -> Unit
                is ConversationEventResponse.UserLeftConversation -> if (event.userId == me) Unit
                is ConversationEventResponse.ConversationCreated -> metadataRefreshes += event.conversationId
                is ConversationEventResponse.UserJoinedConversation -> metadataRefreshes += event.conversationId
                is ConversationEventResponse.GroupProfileUpdated -> metadataRefreshes += event.conversationId
                is ConversationEventResponse.PersonalSettingsUpdated -> metadataRefreshes += event.conversationId
                else -> Unit
            }
        }
        metadataRefreshes.forEach { conversationId ->
            val current = conversationDao.findConversationById(conversationId)
            if (current?.deleted_at == null) {
                val existingGroup = groupProfileDao?.getGroupProfile(conversationId)?.firstOrNull()
                if (current != null || existingGroup != null) refreshConversation(conversationId)
            }
        }
        messageDao.transaction {
            batch.changes.forEach { change ->
                when (val event = change.event) {
                    is MessageEventResponse.Received -> {
                        check(event.message.toMessageEntity() != null) { "Received event has no sender" }
                        persistServerMessage(messageDao, userDao, participantDao, conversationDao, event.message, event.requestId.takeIf { event.senderId == me }, me)
                    }
                    is MessageEventResponse.Withdrawn -> messageDao.revokeMessage(event.messageId, event.timestamp)
                    is MessageEventResponse.Read -> participantDao.updateLastReadMessage(
                        event.conversationId, event.readerId, event.messageId,
                    )
                    is ConversationEventResponse.ConversationDeleted -> {
                        conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                        groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                    }
                    is ConversationEventResponse.UserLeftConversation -> {
                        participantDao.removeParticipant(event.conversationId, event.userId)
                        if (event.userId == me) {
                            conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                            groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                        }
                    }
                    is ConversationEventResponse.GroupJoinRequest,
                    is ConversationEventResponse.GroupJoinRequestHandled,
                    is ContactEventResponse -> log.warn { "Unexpected USER event in conversation scope at revision ${change.revision}: ${event::class.simpleName}" }
                    is ConversationEventResponse -> Unit // Metadata was refreshed successfully above.
                    else -> log.warn { "Skipping unsupported conversation journal event at revision ${change.revision}: ${event::class.simpleName}" }
                    }
                }
                messageDao.setScopeCursor(scope.name, id, batch.nextRevision)
                // Persist the badge-recovery marker in the SAME transaction as
                // the cursor: a crash before the post-commit refresh scheduling
                // must still leave a recoverable state.
                if (batch.changes.any { it.event is MessageEventResponse.Received || it.event is MessageEventResponse.Withdrawn || it.event is MessageEventResponse.Read }) {
                    messageDao.markUnreadRefreshPending(id)
                }
            }
        // The batch restored unread-affecting events without realtime fan-out;
        // coalesce one authoritative detail refresh so the badge converges even
        // when no live Received/Read event follows.
        if (batch.changes.any { it.event is MessageEventResponse.Received || it.event is MessageEventResponse.Withdrawn || it.event is MessageEventResponse.Read }) {
            requestUnreadRefresh(session, id)
        }
        }



    private suspend fun refreshConversation(id: Uuid) {
        val detail = conversationApi.getDetail(id)
        detail.toUserEntity()?.let { userDao.insertUser(it) }
        detail.toGroupOwnerUserEntity()?.let { userDao.insertUser(it) }
        detail.lastMessage?.sender?.toUserEntity()?.let { userDao.insertUser(it) }
        conversationDao.insertConversation(detail.toConversation().toEntity())
        detail.toGroupProfileEntity()?.let { groupProfileDao?.insertGroupProfile(it) }
        participantDao.insertParticipant(detail.toParticipantEntity())
        detail.toPeerParticipantEntity()?.let { participantDao.insertParticipantIfAbsent(it) }
    }

    private suspend fun handleReceivedMessage(session: RunningSession, event: MessageEventResponse.Received) {
        try {
            val me = ownUser.firstOrNull()?.id
            messageDao.transaction {
                persistServerMessage(messageDao, userDao, participantDao, conversationDao, event.message, event.requestId.takeIf { event.senderId == me }, me)
            }
            if (event.senderId != me && event.message.revokedAt == null && session.job.isActive && isActiveSession(session)) {
                unreadRefreshCoordinator.requestRefresh(
                    sessionScope = session.scope,
                    conversationId = event.message.conversationId,
                    conversationApi = conversationApi,
                    conversationDao = conversationDao,
                )
            }
            syncWakeups.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[handleReceived] failed to persist realtime ACK" }
            syncWakeups.tryEmit(Unit)
        }
    }
    private suspend fun handleWithdrawnMessage(session: RunningSession, event: MessageEventResponse.Withdrawn) {
        try {
            messageDao.revokeMessage(event.messageId, event.timestamp)
            // The local window cannot determine whether this message was unread:
            // coalesce an authoritative detail refresh without holding syncMutex
            // or blocking the realtime event collector.
            if (session.job.isActive && isActiveSession(session)) {
                unreadRefreshCoordinator.requestRefresh(
                    sessionScope = session.scope,
                    conversationId = event.conversationId,
                    conversationApi = conversationApi,
                    conversationDao = conversationDao,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error handling withdrawn message" }
        }
    }

    private suspend fun handleReadMessage(session: RunningSession, event: MessageEventResponse.Read) {
        try {
            log.debug { "Message read: ${event.messageId} by ${event.readerId}" }
            participantDao.updateLastReadMessage(
                conversationId = event.conversationId,
                userId = event.readerId,
                lastMessageId = event.messageId
            )
            // Multi-device read sync: coalesce an authoritative detail refresh
            // rather than trusting the event's unreadCount directly (which may
            // default to 0 on older frames or arrive out of order with newer
            // messages).
            val myId = ownUser.firstOrNull()?.id
            if (myId != null && event.readerId == myId && session.job.isActive && isActiveSession(session)) {
                unreadRefreshCoordinator.requestRefresh(
                    sessionScope = session.scope,
                    conversationId = event.conversationId,
                    conversationApi = conversationApi,
                    conversationDao = conversationDao,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error handling read message" }
        }
    }

    suspend fun cleanup() {
        stopSession()
    }

    internal suspend fun inMemorySessionStateCountsForTest(): Triple<Int, Int, Int> =
        Triple(0, 0, _typingUsers.value.size)

    /**
     * Outbox driver: `SENDING` rows double as the outbox. `FAILED` rows are
     * deliberately excluded — a failure may be a permanent rejection (no
     * permission, removed from the group, unsupported content), so retrying it
     * automatically would loop, and only the user can decide to try again via
     * [retryMessage]. Retries trigger on WS (re)connect and every
     * [RETRY_POLL_INTERVAL]; messages still unacked after [MESSAGE_TTL] are
     * marked FAILED based on the attempt clock, not message creation time. A resend reuses the original
     * request id, which the server matches against `client_request_id` before
     * inserting, making replays idempotent.
     */
    private suspend fun startOutboxRetryLoop(session: RunningSession) {
        val connectionSubscribed = CompletableDeferred<Unit>()
        val retryJob = session.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Resend as soon as the socket comes back up. Content catch-up
                // is journal-driven (SyncStateResponse.Heads on every connect),
                // so no sweep runs here anymore.
                messageApi.connectionState
                    .onSubscription { connectionSubscribed.complete(Unit) }
                    .collectLatest { state ->
                        if (state is ConnectionState.Connected) {
                            retryPendingMessagesInternal()
                            unreadRefreshCoordinator.retryPending(
                                sessionScope = session.scope,
                                conversationRepository = conversationRepository,
                                conversationApi = conversationApi,
                                conversationDao = conversationDao,
                            )
                        }
                    }
            } catch (e: CancellationException) {
                connectionSubscribed.cancel(e)
                throw e
            } catch (t: Throwable) {
                connectionSubscribed.completeExceptionally(t)
                throw t
            }
        }
        retryJob.invokeOnCompletion { cause ->
            if (cause != null && !connectionSubscribed.isCompleted) {
                connectionSubscribed.completeExceptionally(cause)
            }
        }
        session.scope.launch {
            while (true) {
                delay(RETRY_POLL_INTERVAL)
                try {
                    if (ownUser.firstOrNull() == null) continue
                    sendMutex.withLock { expireStaleMessages() }
                    retryPendingMessagesInternal()
                    if (messageApi.connectionState.value is ConnectionState.Connected) {
                        unreadRefreshCoordinator.retryPending(
                            sessionScope = session.scope,
                            conversationRepository = conversationRepository,
                            conversationApi = conversationApi,
                            conversationDao = conversationDao,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn(e) { "Outbox sweep failed; retrying on the next tick" }
                }
            }
        }
        connectionSubscribed.await()
    }

    override suspend fun retryPendingMessages() {
        try {
            lifecycle.execute { retryPendingMessagesInternal() }
        } catch (_: SessionStoppedException) {
            log.warn { "[retryPendingMessages] refused: session is not active" }
        }
    }

    private suspend fun retryPendingMessagesInternal() = sendMutex.withLock {
        if (messageApi.connectionState.value !is ConnectionState.Connected) return@withLock
        val retryCutoff = Clock.System.now() - RETRY_DELAY
        val currentUserId = ownUser.firstOrNull()?.id ?: return@withLock
        val pending = messageDao.getRetryableMessages(retryCutoff).filter { it.user_id == currentUserId }
        if (pending.isEmpty()) return@withLock

        log.info { "[outbox] resending ${pending.size} pending message(s)" }
        for (entity in pending) {
            val current = messageDao.findMessageById(entity.id) ?: continue
            if (current.local_send_status != MessageStatus.SENDING) continue
            val request = MessageRequest.Send(
                senderId = current.user_id,
                conversationId = current.conversation_id,
                // The row keeps the reply target, and the server stores it on insert:
                // dropping it here would silently degrade a retried reply to a plain message.
                replyToMessageId = current.reply_to_message_id,
                content = current.content,
                requestId = entity.id.toString()
            )
            try {
                messageDao.recordAttempt(entity.id, entity.created_at, Clock.System.now())
                messageApi.send(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Socket died mid-retry: leave rows in SENDING for the next
                // trigger instead of failing the whole batch.
                log.warn(e) { "[outbox] resend interrupted" }
                break
            }
        }
    }

    /**
     * Re-sends a single failed message under its original request identity.
     *
     * The row moves back into the outbox before the socket write, so a retry
     * that is not acknowledged here is picked up by the periodic sweep and the
     * reconnect trigger rather than being lost. Id and timestamp are preserved:
     * the bubble keeps its place in the timeline, and the server matches the
     * original `client_request_id` instead of storing a second message.
     */
    override suspend fun retryMessage(messageId: Uuid): Result<Unit, MessageError> {
        return try {
            lifecycle.execute {
                sendMutex.withLock {
                    val message = messageDao.findMessageById(messageId)
                        ?: return@withLock Err(MessageError.MessageNotFound)

                    if (message.user_id != ownUser.firstOrNull()?.id) return@withLock Err(MessageError.PermissionDenied)
                    if (message.local_send_status != MessageStatus.FAILED) {
                        // A SENDING row is already owned by the outbox, and re-sending a
                        // SENT one would resurrect a delivered message.
                        log.warn { "[retryMessage] $messageId is ${message.local_send_status}, ignoring" }
                        return@withLock Ok(Unit)
                    }

                    if (!messageDao.claimFailedMessage(messageId, Clock.System.now())) return@withLock Ok(Unit)

                    if (messageApi.connectionState.value !is ConnectionState.Connected) {
                        // Accepted into the outbox; the UI shows waiting for a connection.
                        return@withLock Ok(Unit)
                    }

                    val request = MessageRequest.Send(
                        senderId = message.user_id,
                        conversationId = message.conversation_id,
                        content = message.content,
                        requestId = message.id.toString(),
                        replyToMessageId = message.reply_to_message_id,
                    )
                    messageApi.send(request)
                    Ok(Unit)
                }
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[retryMessage] refused: session is not active" }
            Err(MessageError.PermissionDenied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[retryMessage] resend failed for $messageId" }
            Err(MessageError.Unknown(e.message))
        }
    }

    private suspend fun expireStaleMessages() {
        val giveUpBefore = Clock.System.now() - MESSAGE_TTL
        messageDao.failStaleMessages(giveUpBefore)
    }

    private fun determineRenderType(content: MessageContent): MessageRenderType = when (content) {
        is TextContent -> MessageRenderType.TEXT
        is ImageContent -> MessageRenderType.IMAGE
        is VideoContent -> MessageRenderType.VIDEO
        is AudioContent -> MessageRenderType.AUDIO
        is FileContent -> MessageRenderType.FILE
        else -> MessageRenderType.OTHER
    }
}
