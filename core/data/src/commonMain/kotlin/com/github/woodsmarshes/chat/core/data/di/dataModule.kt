package com.github.woodsmarshes.chat.core.data.di

import androidx.paging.ExperimentalPagingApi
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
import org.koin.dsl.module

@OptIn(ExperimentalPagingApi::class)
val dataModule = module {
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::OfflineFirstMessageRepositoryImpl) bind MessageRepository::class
    singleOf(::ConversationRepositoryImpl) bind ConversationRepository::class
    singleOf(::UserRepositoryImpl) bind UserRepository::class
    singleOf(::ContactRepositoryImpl) bind ContactRepository::class
    singleOf(::OfflineFirstArticleRepositoryImpl) bind ArticleRepository::class

    single<ArticleMediatorFactory> {
        val api = get<com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi>()
        val articles = get<com.github.woodsmarshes.chat.core.database.dao.ArticleDao>()
        val users = get<com.github.woodsmarshes.chat.core.database.dao.UserDao>()
        val holder = get<com.github.woodsmarshes.chat.core.database.di.DatabaseHolder>()
        val dispatchers = get<com.github.woodsmarshes.chat.core.common.AppDispatchers>()
        ArticleMediatorFactory { own, author -> ArticleRemoteMediator(own, author, api, articles, users, dispatchers, holder) }
    }
    single<MessageMediatorFactory> {
        val api = get<com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi>()
        val messages = get<com.github.woodsmarshes.chat.core.database.dao.MessageDao>()
        val users = get<com.github.woodsmarshes.chat.core.database.dao.UserDao>()
        val participants = get<com.github.woodsmarshes.chat.core.database.dao.ParticipantDao>()
        val holder = get<com.github.woodsmarshes.chat.core.database.di.DatabaseHolder>()
        val dispatchers = get<com.github.woodsmarshes.chat.core.common.AppDispatchers>()
        MessageMediatorFactory { own, conversation, group ->
            MessageRemoteMediator(own, conversation, group, dispatchers, holder, api, messages, users, participants)
        }
    }

}
