package com.github.woodsmarshes.chat.core.database.room.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.github.woodsmarshes.chat.core.model.UserRole
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Uuid,
    val username: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val passwordHash: String,
    val salt: String,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
