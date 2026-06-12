package com.github.woodsmarshes.chat.core.database.room.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.github.woodsmarshes.chat.core.database.room.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    fun getMessages(conversationId: Uuid, limit: Int = 20): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun getMessageById(id: Uuid): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND id < :beforeId ORDER BY id DESC LIMIT :limit")
    fun getMessagesBefore(conversationId: Uuid, beforeId: Uuid, limit: Int): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET revokedAt = :revokedAt WHERE id = :id")
    suspend fun revokeMessage(id: Uuid, revokedAt: Instant)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Uuid)
}
