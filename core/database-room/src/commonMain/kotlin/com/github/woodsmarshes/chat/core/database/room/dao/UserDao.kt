package com.github.woodsmarshes.chat.core.database.room.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.github.woodsmarshes.chat.core.database.room.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: Uuid): Flow<UserEntity?>

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id IN (:ids)")
    fun getUsersByIds(ids: List<Uuid>): Flow<List<UserEntity>>

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: Uuid)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
