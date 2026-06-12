package com.github.woodsmarshes.chat.core.database.room

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import com.github.woodsmarshes.chat.core.database.room.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.room.dao.UserDao
import com.github.woodsmarshes.chat.core.database.room.entity.MessageEntity
import com.github.woodsmarshes.chat.core.database.room.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
@TypeConverters(RoomTypeConverters::class)
abstract class RoomChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
}

expect object ChatDatabaseConstructor : RoomDatabaseConstructor<RoomChatDatabase> {
    override fun initialize(): RoomChatDatabase
}
