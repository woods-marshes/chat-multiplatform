package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.PagingData
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface MessageRepository {

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
}