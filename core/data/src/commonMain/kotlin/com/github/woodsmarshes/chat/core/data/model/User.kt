package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.dto.conversation.UserInfo
import io.github.woodsmarshes.chat.db.UserEntity

fun User.toUserEntity() = UserEntity(
    id = this.id,
    username = this.username,
    email = this.email,
    display_name = this.displayName,
    avatar = this.avatarUrl,
    bio = this.bio,
    created_at = this.createdAt,
    updated_at = this.updatedAt,
    deleted_at = this.deletedAt,
    role = this.role,
)

fun UserEntity.toUser() = User(
    id = this.id,
    username = this.username,
    email = this.email,
    displayName = this.display_name,
    avatarUrl = this.avatar,
    bio = this.bio,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
    deletedAt = this.deleted_at,
    role = this.role,
)
