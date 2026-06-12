package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import com.github.woodsmarshes.chat.core.model.MessageStatus
import io.github.woodsmarshes.chat.db.GetLatestMessage
import io.github.woodsmarshes.chat.db.GetLatestMessages
import io.github.woodsmarshes.chat.db.GetMessageById
import io.github.woodsmarshes.chat.db.GetMessagesWithAllRelationsByIds
import io.github.woodsmarshes.chat.db.GetMessagesWithAllRelationsByPage
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface MessageDao {

    suspend fun transaction(body: suspend SuspendingTransactionWithoutReturn.() -> Unit,)
    // 写入
    suspend fun insertMessage(message: MessageEntity)
    suspend fun insertMessages(messages: List<MessageEntity>)

    // 查询 - 基础分页
    fun getMessagesPaged(
        conversationId: Uuid,
        beforeTimestamp: Instant,
        limit: Long
    ): Flow<List<MessageEntity>>

    fun pagingSource(conversationId: Uuid, pageSize: Long): PagingSource<Uuid, KeyedMessagesWithRelations>

    // 查询 - 核心关联分页 (带发送者信息)
    fun getMessagesWithRelationsByPage(
        conversationId: Uuid,
        beforeId: Uuid?,
        limit: Long
    ): Flow<List<GetMessagesWithAllRelationsByPage>>

    // 查询 - 批量补全回复引用
    fun getMessagesWithRelationsByIds(ids: List<Uuid>): Flow<List<GetMessagesWithAllRelationsByIds>>

    // 获取单个会话的最后一条消息
    fun getLatestMessage(conversationId: Uuid): Flow<GetLatestMessage?>

    // 批量获取多个会话的最后一条消息
    fun getLatestMessages(conversationIds: List<Uuid>): Flow<List<GetLatestMessages>>

    fun getMessageById(id: Uuid): Flow<GetMessageById?>

    fun getRepliesToMessage(messageId: Uuid): Flow<List<MessageEntity>>

    // 更新与状态管理
    suspend fun updateMessageStatus(oldId: Uuid, newId: Uuid, createdAt: Instant, status: MessageStatus)

    suspend fun revokeMessage(id: Uuid, revokedAt: Instant)

    // 删除
    suspend fun deleteMessage(id: Uuid)

    suspend fun clearConversationHistory(conversationId: Uuid)

    // 统计
    fun countUnreadAfter(
        conversationId: Uuid,
        myUserId: Uuid,
        lastReadTimestamp: Instant
    ): Flow<Long>
}