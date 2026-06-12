package com.github.woodsmarshes.chat.events

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed class ConversationEvent {
    /** 创建会话 */
    data class ConversationCreated(
        val conversationId: Uuid, // 改为一致的字段名
        val type: ConversationType,
        val creatorId: Uuid,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 删除会话 */
    data class ConversationDeleted(
        val conversationId: Uuid,
        val deleterId: Uuid,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 加入会话 */
    data class UserJoinedConversation(
        val conversationId: Uuid,
        val userId: List<Uuid>,
        val inviterId: Uuid?,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 退出会话 */
    data class UserLeftConversation(
        val conversationId: Uuid,
        val userId: Uuid,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 会话设置更新 */
    data class GroupProfileUpdated(
        val conversationId: Uuid,
        val updaterId: Uuid,
        val profile: UpdateConversationSettingsRequest,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 个人会话设置更新 */
    data class PersonalSettingsUpdated(
        val conversationId: Uuid,
        val userId: Uuid,
        val settings: ParticipantSettings,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 群组申请 */
    data class GroupJoinRequest(
        val requestId: Uuid,
        val conversationId: Uuid,
        val applicantId: Uuid,
        val message: String?,
        val timestamp: Instant
    ) : ConversationEvent()

    /** 群组申请处理 */
    data class GroupJoinRequestHandled(
        val requestId: Uuid,
        val conversationId: Uuid,
        val applicantId: Uuid,
        val handlerId: Uuid,
        val approved: Boolean,
        val reason: String?,
        val timestamp: Instant
    ) : ConversationEvent()
}

fun ConversationEvent.getConversationId(): Uuid = when (this) {
    is ConversationEvent.ConversationCreated -> this.conversationId
    is ConversationEvent.ConversationDeleted -> this.conversationId
    is ConversationEvent.UserJoinedConversation -> this.conversationId
    is ConversationEvent.UserLeftConversation -> this.conversationId
    is ConversationEvent.GroupProfileUpdated -> this.conversationId
    is ConversationEvent.PersonalSettingsUpdated -> this.conversationId
    is ConversationEvent.GroupJoinRequest -> this.conversationId
    is ConversationEvent.GroupJoinRequestHandled -> this.conversationId
}
