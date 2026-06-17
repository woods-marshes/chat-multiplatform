package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.UserRole
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object Users : UuidTable(name = "users", uuidVersion = UuidVersion.V7) {
    val username = varchar("username", length = 64).uniqueIndex()
    val email = varchar("email", length = 255).uniqueIndex()
    val displayName = varchar("display_name", length = 64).nullable()
    val avatarUrl = varchar("avatar", length = 512).nullable()
    val bio = varchar("bio", length = 512).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").clientDefault{ Clock.System.now() }
    val deletedAt = timestamp("deleted_at").nullable()
    val role = enumerationByName("role", 32, UserRole::class)
    val passwordHash = varchar("password_hash", length = 255)
    val salt = varchar("salt", length = 255)

    init {
        uniqueIndex(username, email)
    }
}