package com.github.woodsmarshes.chat.di

import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.base.hashing.HashingServiceImpl
import com.github.woodsmarshes.chat.base.jwt.TokenService
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.events.EventBusImpl
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import com.github.woodsmarshes.chat.utils.TemporaryUploadStoreImpl
import com.github.woodsmarshes.chat.websocket.MessageBroadcaster
import com.github.woodsmarshes.chat.websocket.WebSocketSessionManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val MainModule = module {
    singleOf(::TokenServiceImpl) {
        bind<TokenService>()
    }

    singleOf(::HashingServiceImpl) {
        bind<HashingService>()
    }

    singleOf(::EventBusImpl) {
        bind<EventBus>()
    }

    singleOf(::TemporaryUploadStoreImpl) {
        bind<TemporaryUploadStore>()
    }

    single {
        WebSocketSessionManager()
    }

    singleOf(::MessageBroadcaster)
}