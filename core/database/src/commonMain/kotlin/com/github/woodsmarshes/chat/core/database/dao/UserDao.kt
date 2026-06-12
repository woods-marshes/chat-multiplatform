package com.github.woodsmarshes.chat.core.database.dao

import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface UserDao {
    fun getUserById(id: Uuid): Flow<UserEntity?>

    suspend fun insertUser(user: UserEntity)

    suspend fun insertUsers(users: List<UserEntity>)

    suspend fun deleteUser(id: Uuid)

    suspend fun deleteAllUsers()
}