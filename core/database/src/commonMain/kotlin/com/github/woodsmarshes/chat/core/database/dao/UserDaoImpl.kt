package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.UserEntity
import io.github.woodsmarshes.chat.db.UsersQueries
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

/**
 * Dynamic [UserDao] backed by [dbProvider]. Calling [bindToCurrentDatabase]
 * captures the active [ChatDatabase] at the start of a session or operation
 * and returns a [PinnedUserDao] fixed to that database instance, so subsequent
 * writes never re-resolve [dbProvider] into a different user's database.
 */
class UserDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : UserDao {
    override val boundDatabase: ChatDatabase?
        get() = null

    private suspend fun writeQueries(): UsersQueries {
        currentCoroutineContext().ensureActive()
        val contextDb = currentCoroutineContext()[BoundDatabaseElement]?.database
        if (contextDb != null) {
            return contextDb.usersQueries
        }
        return dbProvider().usersQueries
    }

    override fun bindToCurrentDatabase(): UserDao {
        val capturedDb = runCatching { dbProvider() }.getOrElse {
            throw SessionStoppedException(it.message ?: "Database is not initialized")
        }
        return PinnedUserDao(
            boundDatabase = capturedDb,
            ioContext = ioContext,
            sessionGate = PinnedDatabaseSessionGate(capturedDb, sessionGate),
        )
    }

    override fun getUserById(id: Uuid): Flow<UserEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.usersQueries.getUserById(id) },
            extractor = { it.executeAsOneOrNull() },
        )

    override suspend fun insertUser(user: UserEntity) {
        writeQueries().upsertUser(user)
    }

    override suspend fun insertUsers(users: List<UserEntity>) {
        if (users.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            users.forEach {
                q.upsertUser(it)
            }
        }
    }

    override suspend fun deleteUser(id: Uuid) {
        writeQueries().hardDeleteUser(id)
    }

    override suspend fun deleteAllUsers() {
        writeQueries().deleteAllUser()
    }
}

private class PinnedUserDao(
    override val boundDatabase: ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate,
) : UserDao {
    private suspend fun writeQueries(): UsersQueries {
        currentCoroutineContext().ensureActive()
        return boundDatabase.usersQueries
    }

    override fun bindToCurrentDatabase(): UserDao = this

    override fun getUserById(id: Uuid): Flow<UserEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.usersQueries.getUserById(id) },
            extractor = { it.executeAsOneOrNull() },
        )

    override suspend fun insertUser(user: UserEntity) {
        writeQueries().upsertUser(user)
    }

    override suspend fun insertUsers(users: List<UserEntity>) {
        if (users.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            users.forEach {
                q.upsertUser(it)
            }
        }
    }

    override suspend fun deleteUser(id: Uuid) {
        writeQueries().hardDeleteUser(id)
    }

    override suspend fun deleteAllUsers() {
        writeQueries().deleteAllUser()
    }
}
