package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.GetConversationListView
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ConversationDao {
    // 写入与同步
    suspend fun insertConversation(conversation: ConversationEntity)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    // 查询 - 基础
    fun getAllActiveConversations(): Flow<List<ConversationEntity>>
    fun getConversationById(id: Uuid): Flow<ConversationEntity?>

    // 查询 - 视图（带最后一条消息预览，用于列表页）
    fun getConversationListView(currentUserId: Uuid): Flow<List<GetConversationListView>>

    // 更新逻辑
    suspend fun updateLastMessage(id: Uuid, lastMessageId: Uuid?, updatedAt: Instant)
    suspend fun updateMetadata(id: Uuid, metadata: ConversationMetadata?, updatedAt: Instant)

    // 删除逻辑
    suspend fun softDeleteConversation(id: Uuid, deletedAt: Instant)
    suspend fun hardDeleteConversation(id: Uuid)
}