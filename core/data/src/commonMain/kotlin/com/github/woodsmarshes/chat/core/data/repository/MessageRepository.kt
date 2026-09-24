package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.PagingData
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface MessageRepository {
    /**
     * Resends outbox messages whose original send was never acknowledged.
     * Idempotent: the server keys retried messages by their requestId.
     */
    suspend fun retryPendingMessages()

//    val invalidationEvents: Flow<Unit>

    fun getMessages(
        ownUserId: Uuid,
        conversationId: Uuid,
        isGroup: Boolean,
        limit: Int = 20
    ): Flow<PagingData<MessageUiModel>>

    suspend fun sendMessage(
        conversationId: Uuid,
        content: MessageContent,
        replyToMessageId: Uuid? = null,
    ): Result<Unit, MessageError>

    suspend fun revokeMessage(messageId: Uuid)

    suspend fun markAsRead(conversationId: Uuid, messageId: Uuid)

    /**
     * Who is currently typing in a conversation: userId -> last typing-event
     * timestamp (epoch millis). Entries do NOT expire on their own — pair
     * this flow with a ticker and treat entries older than a few seconds as
     * stopped, in case the matching "stopped typing" event was lost.
     */
    fun getTypingUsersFlow(conversationId: Uuid): Flow<Map<Uuid, Long>>

    /** Broadcasts the local user's typing state to conversation members. */
    suspend fun sendTyping(conversationId: Uuid, isTyping: Boolean)
}