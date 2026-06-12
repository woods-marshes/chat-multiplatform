package com.github.woodsmarshes.chat.core.data.di

import androidx.paging.ExperimentalPagingApi
import com.github.woodsmarshes.chat.core.data.paging.MessageRemoteMediator
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.data.repository.AuthRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ContactRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.OfflineFirstMessageRepositoryImpl
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import kotlin.uuid.Uuid
import org.koin.dsl.module

val dataModule = module {
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::OfflineFirstMessageRepositoryImpl) bind MessageRepository::class
    singleOf(::ConversationRepositoryImpl) bind ConversationRepository::class
    singleOf(::UserRepositoryImpl) bind UserRepository::class
    singleOf(::ContactRepositoryImpl) bind ContactRepository::class

    @OptIn(ExperimentalPagingApi::class)
    factory { (ownUserId: Uuid, conversationId: Uuid, isGroup: Boolean) ->
        MessageRemoteMediator(
            ownUserId = ownUserId,
            conversationId = conversationId,
            isGroup = isGroup,
            appDispatchers = get(),
            conversationApi = get(),
            messageDao = get(),
            userDao = get(),
            participantDao = get(),
        )
    }
}
