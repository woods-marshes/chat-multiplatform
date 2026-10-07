package com.github.woodsmarshes.chat.core.database.dao

import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface UserDao {
    val boundDatabase: ChatDatabase?

    /**
     * Captures the [ChatDatabase] active at the moment this method is called
     * and returns a [UserDao] handle pinned to that exact database instance,
     * so subsequent DAO calls on the returned handle never dynamically resolve
     * a different session's database after a suspension point.
     */
    fun bindToCurrentDatabase(): UserDao

    fun getUserById(id: Uuid): Flow<UserEntity?>

    suspend fun insertUser(user: UserEntity)

    suspend fun insertUsers(users: List<UserEntity>)

    suspend fun deleteUser(id: Uuid)

    suspend fun deleteAllUsers()
}
