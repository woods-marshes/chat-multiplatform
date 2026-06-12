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
import com.github.woodsmarshes.chat.core.data.model.toReplyMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toReplyParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toReplyUserEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.GetMessagesWithAllRelationsByPage
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
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
                val replyEntities = response
                    .mapNotNull { it.replyTo }
                    .map { reply ->
                        Triple(
                            reply.toReplyMessageEntity(),
                            reply.toReplyUserEntity(),
                            reply.toReplyParticipantEntity()
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
                val replyEntities = response
                    .mapNotNull { it.replyTo }
                    .map { reply ->
                        reply.toReplyMessageEntity()
                    }
                val mainEntities = response.map { it.toMessageEntity() }
                val messageEntities = (replyEntities + mainEntities)
                    .filterNotNull()
                    .distinct()
                withContext(appDispatchers.io) {
                    messageDao.insertMessages(messageEntities)
                }
            }
            MediatorResult.Success(
                endOfPaginationReached = response.size < pageSize
            )
        } catch (e: Exception) {
            log.error(tag = "MessageRemoteMediator", message = "Load failed", throwable = e)
            MediatorResult.Error(e)
        }
    }

}