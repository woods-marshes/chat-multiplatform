package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.model.GroupSettings
import io.github.woodsmarshes.chat.db.GetGroupWithLastMessage
import io.github.woodsmarshes.chat.db.GroupProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface GroupProfileDao {
    // 写入与同步
    suspend fun insertGroupProfile(groupProfile: GroupProfileEntity)
    suspend fun insertGroupProfiles(groupProfiles: List<GroupProfileEntity>)

    // 查询
    fun getGroupProfile(conversationId: Uuid): Flow<GroupProfileEntity?>
    fun getGroupProfiles(conversationIds: List<Uuid>): Flow<List<GroupProfileEntity>>
    fun getGroupByHandle(handle: String): Flow<GroupProfileEntity?>

    // 关联查询
    fun getGroupWithLastMessage(): Flow<List<GetGroupWithLastMessage>>

    // 更新
    suspend fun updateGroupInfo(
        conversationId: Uuid,
        name: String,
        description: String?,
        avatarUrl: String?,
        updatedAt: Instant
    )

    suspend fun updateGroupSettings(
        conversationId: Uuid,
        settings: GroupSettings,
        updatedAt: Instant
    )

    suspend fun transferOwnership(
        conversationId: Uuid,
        newOwnerId: Uuid,
        updatedAt: Instant
    )

    // 删除
    suspend fun deleteGroupProfile(conversationId: Uuid)
}