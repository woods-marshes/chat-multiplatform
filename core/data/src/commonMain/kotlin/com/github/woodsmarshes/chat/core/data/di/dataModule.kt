package com.github.woodsmarshes.chat.core.data.di

import androidx.paging.ExperimentalPagingApi
import com.github.woodsmarshes.chat.core.common.AppDispatchers
import com.github.woodsmarshes.chat.core.data.paging.ArticleRemoteMediator
import com.github.woodsmarshes.chat.core.data.paging.MessageRemoteMediator
import com.github.woodsmarshes.chat.core.data.repository.ArticleRepository
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.data.repository.AuthRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ContactRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.OfflineFirstArticleRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.OfflineFirstMessageRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import com.github.woodsmarshes.chat.core.data.paging.ArticleMediatorFactory
import com.github.woodsmarshes.chat.core.data.paging.MessageMediatorFactory
import com.github.woodsmarshes.chat.core.database.dao.ArticleDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import org.koin.dsl.module

@OptIn(ExperimentalPagingApi::class)
val dataModule = module {
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    single<MessageRepository> {
        OfflineFirstMessageRepositoryImpl(
            messageDao = get(),
            userDao = get(),
            participantDao = get(),
            messageApi = get(),
            conversationApi = get(),
            conversationDao = get(),
            groupJoinRequestDao = get(),
            userSettingDataSource = get(),
            scope = get(),
            mediatorFactory = get(),
            groupProfileDao = get(),
        )
    }
    singleOf(::ConversationRepositoryImpl) bind ConversationRepository::class
    singleOf(::UserRepositoryImpl) bind UserRepository::class
    single<ContactRepository> {
        ContactRepositoryImpl(
            contactApi = get(),
            contactDao = get(),
            contactRequestDao = get(),
            userDao = get(),
            userSettingDataSource = get(),
            realtimeApi = get(),
        )
    }
    singleOf(::OfflineFirstArticleRepositoryImpl) bind ArticleRepository::class

    single<ArticleMediatorFactory> {
        val api = get<ArticleApi>()
        val articles = get<ArticleDao>()
        val users = get<UserDao>()
        val holder = get<DatabaseHolder>()
        val dispatchers = get<AppDispatchers>()
        ArticleMediatorFactory { own, author ->
            ArticleRemoteMediator(
                getMyArticle = own,
                authorId = author,
                articleApi = api,
                articleDao = articles,
                userDao = users,
                appDispatchers = dispatchers,
                databaseHolder = holder
            )
        }
    }
    single<MessageMediatorFactory> {
        val api = get<ConversationApi>()
        val messages = get<MessageDao>()
        val users = get<UserDao>()
        val participants = get<ParticipantDao>()
        val holder = get<DatabaseHolder>()
        val dispatchers = get<AppDispatchers>()
        MessageMediatorFactory { own, conversation, group ->
            MessageRemoteMediator(
                ownUserId = own,
                conversationId = conversation,
                isGroup = group,
                appDispatchers = dispatchers,
                databaseHolder = holder,
                conversationApi = api,
                messageDao = messages,
                userDao = users,
                participantDao = participants
            )
        }
    }

}
