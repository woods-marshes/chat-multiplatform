package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.GroupProfile
import io.github.woodsmarshes.chat.db.GetGroupWithLastMessage
import io.github.woodsmarshes.chat.db.GroupProfileEntity

fun GroupProfile.toEntity() = GroupProfileEntity(
    conversation_id = this.conversationId,
    name = this.name,
    handle = this.handle,
    description = this.description,
    avatar_url = this.avatarUrl,
    owner_id = this.ownerId,
    settings = this.settings,
    created_at = this.createdAt,
    updated_at = this.updatedAt,
)

fun GroupProfileEntity.toGroupProfile() = GroupProfile(
    conversationId = this.conversation_id,
    name = this.name,
    handle = this.handle,
    description = this.description,
    avatarUrl = this.avatar_url,
    ownerId = this.owner_id,
    settings = this.settings,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
)

fun GetGroupWithLastMessage.toGroupProfile() = GroupProfile(
    conversationId = this.conversation_id,
    name = this.name,
    handle = this.handle,
    description = this.description,
    avatarUrl = this.avatar_url,
    ownerId = this.owner_id,
    settings = this.settings,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
)