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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@ExperimentalPagingApi
class MessageRemoteMediator(
    private val ownUserId: Uuid,
    private val conversationId: Uuid,
    private val isGroup: Boolean,
    private val appDispatchers: AppDispatchers,
    private val conversationApi: ConversationApi,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val participantDao: ParticipantDao,
) : RemoteMediator<Uuid, KeyedMessagesWithRelations>(){
    private val log = KotlinLogging.logger {}

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Uuid, KeyedMessagesWithRelations>
    ): MediatorResult {
        return try {
            val lastMsgId = when (loadType) {
                LoadType.REFRESH -> {
                    log.debug(tag = "MessageRemoteMediator", message = "loadType is REFRESH")
                    null
                }
                LoadType.PREPEND -> {
                    log.debug(tag = "MessageRemoteMediator", message = "loadType is PREPEND")
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    log.debug(tag = "MessageRemoteMediator", message = "loadType is APPEND")
                    state.lastItemOrNull()?.id
                }
            }
            log.debug(tag = "MessageRemoteMediator", message = "lastMsgId: $lastMsgId")
            val pageSize = state.config.pageSize
            val response = conversationApi.getMessages(
                conversationId = conversationId,
                limit = pageSize,
                beforeId = lastMsgId
            )
            if (response.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }
            if (isGroup) {
                // Reply targets must be persisted with THEIR OWN content, so map
                // the referenced message directly (toReplyMessageEntity would
                // walk to reply.replyTo and store the grandparent instead).
                val replyEntities = response
                    .mapNotNull { it.replyTo }
                    // A reply target without a sender cannot be persisted; skip
                    // it instead of aborting the whole page.
                    .filter { it.sender != null }
                    .map { reply ->
                        Triple(
                            reply.toMessageEntity(),
                            reply.toUserEntity(),
                            reply.toParticipantEntity()
                        )
                    }

                val mainEntities = response
                    .map { message ->
                        Triple(
                            message.toMessageEntity(),
                            message.toUserEntity(),
                            message.toParticipantEntity()
                        )
                    }
                val userEntities = (replyEntities + mainEntities)
                    .mapNotNull { it.second }
                    .filter { it.id != ownUserId }
                    .distinct()
                val participantEntities = (replyEntities + mainEntities)
                    .mapNotNull { it.third }
                    .distinct()
                val messageEntities = (replyEntities + mainEntities)
                    .mapNotNull { it.first }
                    .distinct()

                withContext(appDispatchers.io) {
                    messageDao.transaction{
                        userDao.insertUsers(userEntities)
                        participantDao.insertParticipants(participantEntities)
                        messageDao.insertMessages(messageEntities)
                    }
                }
            } else {
                // Private chats need sender users cached too, or the sender join
                // in keyedMessagesWithRelations returns null names.
                val replyEntities = response
                    .mapNotNull { it.replyTo }
                    .filter { it.sender != null }
                    .map { reply -> reply.toMessageEntity() }
                val userEntities = (response.mapNotNull { it.replyTo } + response)
                    .mapNotNull { it.toUserEntity() }
                    .filter { it.id != ownUserId }
                    .distinct()
                val mainEntities = response.map { it.toMessageEntity() }
                val messageEntities = (replyEntities + mainEntities)
                    .filterNotNull()
                    .distinct()
                withContext(appDispatchers.io) {
                    messageDao.transaction {
                        userDao.insertUsers(userEntities)
                        messageDao.insertMessages(messageEntities)
                    }
                }
            }
            MediatorResult.Success(
                endOfPaginationReached = response.size < pageSize
            )
        } catch (e: CancellationException) {
            // Never swallow structured-concurrency cancellation.
            throw e
        } catch (e: Exception) {
            log.error(tag = "MessageRemoteMediator", message = "Load failed", throwable = e)
            MediatorResult.Error(e)
        }
    }

}