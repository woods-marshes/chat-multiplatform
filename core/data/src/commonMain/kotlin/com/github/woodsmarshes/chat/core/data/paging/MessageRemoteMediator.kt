package com.github.woodsmarshes.chat.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.github.woodsmarshes.chat.core.common.AppDispatchers
import com.github.woodsmarshes.chat.core.common.utils.debug
import com.github.woodsmarshes.chat.core.common.utils.error
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import kotlinx.coroutines.withContext
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.uuid.Uuid

@ExperimentalPagingApi
class MessageRemoteMediator(
    private val ownUserId: Uuid,
    private val conversationId: Uuid,
    private val isGroup: Boolean,
    private val appDispatchers: AppDispatchers,
    private val databaseHolder: DatabaseHolder,
    private val conversationApi: ConversationApi,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val participantDao: ParticipantDao,
) : SessionBoundRemoteMediator<Uuid, KeyedMessagesWithRelations>(databaseHolder.sessionGate) {
    private val log = KotlinLogging.logger {}

    private class StaleWindowGenerationException(
        conversationId: Uuid,
        expectedGeneration: Long,
        actualGeneration: Long,
    ) : IllegalStateException(
        "Timeline window generation changed for $conversationId during history load (expected=$expectedGeneration, actual=$actualGeneration)",
    )

    override suspend fun loadInSession(
        loadType: LoadType,
        state: PagingState<Uuid, KeyedMessagesWithRelations>
    ): MediatorResult {
        if (loadType == LoadType.PREPEND) {
            log.debug(tag = "MessageRemoteMediator", message = "loadType is PREPEND")
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        // Capture the persistent window generation and the oldest confirmed
        // timeline message ID inside one DB read before issuing the network
        // call. APPEND must never use state.lastItemOrNull()?.id because the
        // tail item in PagingState can be an unacked outbox row (SENDING/FAILED)
        // whose client requestId does not exist as a server cursor; when no
        // confirmed timeline row exists yet (e.g. only outbox rows are cached),
        // beforeId falls back to null to fetch the newest server page.
        val (expectedGeneration, lastMsgId) = withContext(appDispatchers.io) {
            var generation = 0L
            var cursorId: Uuid? = null
            messageDao.transaction {
                generation = messageDao.getWindowGeneration(conversationId)
                cursorId = when (loadType) {
                    LoadType.REFRESH -> null
                    LoadType.APPEND -> messageDao.getOldestConfirmedTimelineMessageId(conversationId)
                    LoadType.PREPEND -> null
                }
            }
            generation to cursorId
        }
        log.debug(
            tag = "MessageRemoteMediator",
            message = "loadType=$loadType expectedGeneration=$expectedGeneration lastMsgId=$lastMsgId",
        )

        val pageSize = state.config.pageSize
        val response = conversationApi.getMessages(
            conversationId = conversationId,
            limit = pageSize,
            beforeId = lastMsgId,
        )
        currentCoroutineContext().ensureActive()

        val staleFailure: StaleWindowGenerationException? = if (response.isEmpty()) {
            withContext(appDispatchers.io) {
                var err: StaleWindowGenerationException? = null
                messageDao.transaction {
                    val currentGen = messageDao.getWindowGeneration(conversationId)
                    if (currentGen != expectedGeneration) {
                        err = StaleWindowGenerationException(conversationId, expectedGeneration, currentGen)
                    }
                }
                err
            }
        } else if (isGroup) {
            // Reply targets must be persisted with THEIR OWN content, so map
            // the referenced message directly (toReplyMessageEntity would
            // walk to reply.replyTo and store the grandparent instead).
            // They are seeded (INSERT OR IGNORE, in_timeline=false): they sit
            // outside the local timeline window and must never become Paging
            // anchors ahead of the window (see MessageSyncCursor).
            val replyEntities = response
                .mapNotNull { it.replyTo }
                // A reply target without a sender cannot be persisted; skip
                // it instead of aborting the whole page.
                .filter { it.sender != null }
                .map { reply ->
                    Triple(
                        reply.toMessageEntity(inTimeline = false),
                        reply.toUserEntity(),
                        reply.toParticipantEntity(),
                    )
                }

            val mainEntities = response
                .map { message ->
                    Triple(
                        message.toMessageEntity(),
                        message.toUserEntity(),
                        message.toParticipantEntity(),
                    )
                }
            val userEntities = (replyEntities + mainEntities)
                .mapNotNull { it.second }
                .filter { it.id != ownUserId }
                .distinct()
            val participantEntities = (replyEntities + mainEntities)
                .mapNotNull { it.third }
                .distinct()
            val replyMessageEntities = replyEntities.mapNotNull { it.first }
            val mainMessageEntities = mainEntities.mapNotNull { it.first }

            withContext(appDispatchers.io) {
                var err: StaleWindowGenerationException? = null
                messageDao.transaction {
                    val currentGen = messageDao.getWindowGeneration(conversationId)
                    if (currentGen != expectedGeneration) {
                        err = StaleWindowGenerationException(conversationId, expectedGeneration, currentGen)
                        return@transaction
                    }
                    userDao.insertUsers(userEntities)
                    participantDao.insertParticipants(participantEntities)
                    messageDao.seedMessages(replyMessageEntities)
                    messageDao.insertMessages(mainMessageEntities)
                }
                err
            }
        } else {
            // Private chats need sender users cached too, or the sender join
            // in keyedMessagesWithRelations returns null names.
            val replyEntities = response
                .mapNotNull { it.replyTo }
                .filter { it.sender != null }
                .map { reply -> reply.toMessageEntity(inTimeline = false) }
            val userEntities = (response.mapNotNull { it.replyTo } + response)
                .mapNotNull { it.toUserEntity() }
                .filter { it.id != ownUserId }
                .distinct()
            val mainEntities = response.map { it.toMessageEntity() }
            withContext(appDispatchers.io) {
                var err: StaleWindowGenerationException? = null
                messageDao.transaction {
                    val currentGen = messageDao.getWindowGeneration(conversationId)
                    if (currentGen != expectedGeneration) {
                        err = StaleWindowGenerationException(conversationId, expectedGeneration, currentGen)
                        return@transaction
                    }
                    userDao.insertUsers(userEntities)
                    messageDao.seedMessages(replyEntities.filterNotNull())
                    messageDao.insertMessages(mainEntities.filterNotNull())
                }
                err
            }
        }

        if (staleFailure != null) {
            onLoadFailure(staleFailure)
            return MediatorResult.Error(staleFailure)
        }

        return MediatorResult.Success(
            endOfPaginationReached = response.size < pageSize,
        )
    }

    override fun onLoadFailure(failure: Exception) {
        log.error(tag = "MessageRemoteMediator", message = "Load failed", throwable = failure)
    }
}
