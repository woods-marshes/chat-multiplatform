package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class UserDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : UserDao {
    private val queries
        get() = dbProvider().usersQueries

    override fun getUserById(id: Uuid): Flow<UserEntity?> {
        return queries
            .getUserById(id)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override suspend fun insertUser(user: UserEntity) {
        queries.upsertUser(user)
    }

    override suspend fun insertUsers(users: List<UserEntity>) {
        if (users.isEmpty()) return
        queries.transaction {
            users.forEach {
                insertUser(it)
            }
        }
    }

    override suspend fun deleteUser(id: Uuid) {
        queries.hardDeleteUser(id)
    }

    override suspend fun deleteAllUsers() {
        queries.deleteAllUser()
    }
}