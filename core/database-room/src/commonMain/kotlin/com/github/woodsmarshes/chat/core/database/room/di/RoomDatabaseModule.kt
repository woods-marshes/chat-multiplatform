package com.github.woodsmarshes.chat.core.database.room.di

import com.github.woodsmarshes.chat.core.database.room.RoomChatDatabase
import com.github.woodsmarshes.chat.core.database.room.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.room.dao.UserDao
import org.koin.dsl.module

val roomDatabaseModule = module {
    // Placeholder — actual database creation requires platform-specific driver
    // See Room.databaseBuilder<RoomChatDatabase>("chat.db")
    //     .setDriver(platformDriver)
    //     .build()
    single<RoomChatDatabase?> { null }
    single<UserDao?> { null }
    single<MessageDao?> { null }
}
