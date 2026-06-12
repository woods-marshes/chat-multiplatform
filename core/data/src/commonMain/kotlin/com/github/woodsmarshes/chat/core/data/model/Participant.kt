package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import io.github.woodsmarshes.chat.db.ParticipantEntity

fun ConversationParticipant.toEntity() = ParticipantEntity(
    conversation_id = this.conversationId,
    user_id = this.userId,
    role = this.role,
    last_read_message_id = this.lastReadMessageId,
    joined_at = this.joinedAt,
    muted_until = this.mutedUntil,
    settings = this.settings,
)

fun ParticipantEntity.toParticipant() = ConversationParticipant(
    conversationId = this.conversation_id,
    userId = this.user_id,
    role = this.role,
    lastReadMessageId = this.last_read_message_id,
    joinedAt = this.joined_at,
    mutedUntil = this.muted_until,
    settings = this.settings,
)