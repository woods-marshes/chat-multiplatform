package com.github.woodsmarshes.chat.core.database.di

import com.github.woodsmarshes.chat.core.common.AppDispatchers
import com.github.woodsmarshes.chat.core.database.dao.ArticleDao
import com.github.woodsmarshes.chat.core.database.dao.ArticleDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ContactDao
import com.github.woodsmarshes.chat.core.database.dao.ContactDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.ConversationDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDao
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import org.koin.dsl.module

val daosModule = module {
    single<UserDao> {
        UserDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io,
        )
    }
    single<ParticipantDao> {
        ParticipantDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
    single<MessageDao> {
        MessageDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
    single<GroupProfileDao> {
        GroupProfileDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
    single<ConversationDao> {
        ConversationDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
    single<ArticleDao> {
        ArticleDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
    single<ContactDao> {
        ContactDaoImpl(
            dbProvider = { get<DatabaseHolder>().getActiveDatabase() },
            ioContext = get<AppDispatchers>().io
        )
    }
}