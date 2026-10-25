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
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.presentation.MessageUiModel
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

/**
 * The delta-sync engine of [OfflineFirstMessageRepositoryImpl]: watches the
 * realtime connection, pulls journal heads, snapshots brand-new streams, and
 * applies change batches into the local cache. Extracted verbatim; the
 * repository owns the session lifecycle and passes its checks in.
 */
internal class MessageSyncEngine(
    private val messageApi: RealtimeApi,
    private val conversationApi: ConversationApi,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val participantDao: ParticipantDao,
    private val conversationDao: ConversationDao,
    private val groupJoinRequestDao: GroupJoinRequestDao,
    private val groupProfileDao: GroupProfileDao?,
    private val conversationRepository: ConversationRepository,
    private val ownUser: Flow<User?>,
    private val syncWakeups: MutableSharedFlow<Unit>,
    private val syncMutex: Mutex,
    private val userSyncApplier: UserSyncApplier,
    private val unreadRefreshCoordinator: UnreadRefreshCoordinator,
    private val isActiveSession: (OfflineFirstMessageRepositoryImpl.RunningSession) -> Boolean,
) {
    private val log = KotlinLogging.logger {}

    private companion object {
        const val SYNC_WINDOW_MESSAGES = 100
        const val SYNC_PAGE_SIZE = 100
        const val MAX_SYNC_PAGES = 40
        val SYNC_POLL_INTERVAL = 30.seconds
    }

    fun startSyncWorker(session: OfflineFirstMessageRepositoryImpl.RunningSession) {
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

    private suspend fun syncHeads(session: OfflineFirstMessageRepositoryImpl.RunningSession) = syncMutex.withLock {
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

    private suspend fun syncStream(session: OfflineFirstMessageRepositoryImpl.RunningSession, scope: SyncScope, id: Uuid) {
        val storedRevision = messageDao.getScopeCursor(scope.name, id)
        if (storedRevision == null) {
            syncSnapshot(session, scope, id)
            if (scope == SyncScope.USER) syncDeltaAfterSnapshot(session, scope, id)
            return
        }
        syncDeltaAfterSnapshot(session, scope, id, storedRevision)
    }

    private suspend fun syncDeltaAfterSnapshot(
        session: OfflineFirstMessageRepositoryImpl.RunningSession,
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
                log.warn(reset) { "[sync] delta batch requires reset for $scope/$id; re-syncing from snapshot" }
                syncSnapshot(session, scope, id)
                syncDeltaAfterSnapshot(session, scope, id)
                return
            }
            revision = batch.nextRevision
            if (!batch.hasMore) return
        }
        error("Sync page budget exhausted for $scope/$id")
    }

    private suspend fun syncSnapshot(session: OfflineFirstMessageRepositoryImpl.RunningSession, scope: SyncScope, id: Uuid) {
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
    private suspend fun requestUnreadRefresh(session: OfflineFirstMessageRepositoryImpl.RunningSession, conversationId: Uuid) {
        if (session.job.isActive && isActiveSession(session)) {
            unreadRefreshCoordinator.requestRefresh(
                sessionScope = session.scope,
                conversationId = conversationId,
                conversationApi = conversationApi,
                conversationDao = conversationDao,
            )
        }
    }

    private suspend fun applySyncBatch(session: OfflineFirstMessageRepositoryImpl.RunningSession, scope: SyncScope, id: Uuid, batch: SyncBatch) {
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
                        applyConversationDeleted(event.conversationId, event.timestamp, conversationDao, participantDao, groupJoinRequestDao)
                    }
                    is ConversationEventResponse.UserLeftConversation -> {
                        applyUserLeftConversation(event.conversationId, event.userId, me, event.timestamp, conversationDao, participantDao, groupJoinRequestDao)
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
}
