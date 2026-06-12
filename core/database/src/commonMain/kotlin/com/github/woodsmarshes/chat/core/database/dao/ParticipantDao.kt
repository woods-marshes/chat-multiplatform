package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import io.github.woodsmarshes.chat.db.GetParticipantsExcludingUser
import io.github.woodsmarshes.chat.db.GetParticipantsWithUserInfo
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ParticipantDao {
    // 写入
    suspend fun insertParticipant(participant: ParticipantEntity)
    suspend fun insertParticipants(participants: List<ParticipantEntity>)

    // 查询
    fun getParticipantsByConversationId(conversationId: Uuid): Flow<List<ParticipantEntity>>
    fun getParticipantsWithUserInfo(conversationId: Uuid): Flow<List<GetParticipantsWithUserInfo>>
    fun getParticipant(conversationId: Uuid, userId: Uuid): Flow<ParticipantEntity?>

    fun getParticipantsExcludingUser(
        conversationIds: List<Uuid>,
        excludeUserId: Uuid
    ): Flow<List<GetParticipantsExcludingUser>>

    // 更新
    suspend fun updateRole(conversationId: Uuid, userId: Uuid, role: ConversationRole)
    suspend fun updateLastReadMessage(conversationId: Uuid, userId: Uuid, lastMessageId: Uuid?)
    suspend fun updateMuteStatus(conversationId: Uuid, userId: Uuid, mutedUntil: Instant?)
    suspend fun updateSettings(conversationId: Uuid, userId: Uuid, settings: ParticipantSettings)

    // 删除
    suspend fun removeParticipant(conversationId: Uuid, userId: Uuid)
    suspend fun removeAllParticipantsFromConversation(conversationId: Uuid)
}