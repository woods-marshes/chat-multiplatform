package com.github.woodsmarshes.chat.core.data.repository

import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val messagePersistenceLog = KotlinLogging.logger {}

/**
 * Merges a server-authored [message] (plus its optional `replyTo` target,
 * sender user, and sender participant context) and reconciles any matching
 * local outbox placeholder (`SENDING`/`FAILED`) within a single transaction.
 *
 * When [asReplyTarget] is true, the message is persisted with
 * `in_timeline = false` via `seedMessage` (`INSERT OR IGNORE`) so that reply
 * targets sitting outside the local timeline window never become Paging
 * anchors or clobber an existing timeline row.
 */
internal suspend fun persistServerMessage(
    messageDao: MessageDao,
    userDao: UserDao,
    participantDao: ParticipantDao,
    conversationDao: ConversationDao? = null,
    message: Message,
    ownRequestId: String? = null,
    resolvedOwnUserId: Uuid? = null,
    asReplyTarget: Boolean = false,
) {
    val ownId = resolvedOwnUserId
    val candidate = if (!asReplyTarget && ownId != null && message.sender?.id == ownId) {
        ownRequestId?.let(Uuid::parseOrNull) ?: message.clientRequestId
    } else null
    messageDao.transaction {
        message.replyTo?.let { reply ->
            persistServerMessage(
                messageDao = messageDao,
                userDao = userDao,
                participantDao = participantDao,
                conversationDao = conversationDao,
                message = reply,
                ownRequestId = null,
                resolvedOwnUserId = ownId,
                asReplyTarget = true,
            )
        }
        message.toUserEntity()?.let { userDao.insertUser(it) }
        message.toParticipantEntity()?.let { participantDao.insertParticipant(it) }
        val entity = message.toMessageEntity(MessageStatus.SENT, inTimeline = !asReplyTarget)
        if (entity == null) {
            messagePersistenceLog.warn { "[message-sync] skipping server message ${message.id}: no sender" }
            return@transaction
        }
        if (asReplyTarget) {
            // Reply targets live outside the local timeline window: seed
            // only (INSERT OR IGNORE), so they never become Paging anchors
            // and never clobber an existing timeline row, and the list
            // preview bump is skipped.
            messageDao.seedMessage(entity)
            return@transaction
        }
        val local = candidate?.let { messageDao.findMessageById(it) }
        val matchingId = candidate?.takeIf {
            local != null && local.user_id == entity.user_id &&
                local.conversation_id == message.conversationId &&
                local.local_send_status in listOf(MessageStatus.SENDING, MessageStatus.FAILED)
        }
        messageDao.mergeServerMessage(entity, matchingId)
        if (conversationDao != null) {
            val currentLast = conversationDao.findConversationById(message.conversationId)?.last_message_id
            if (currentLast == null || message.id > currentLast) {
                conversationDao.updateLastMessage(message.conversationId, message.id, message.createdAt)
            }
        }
    }
}
