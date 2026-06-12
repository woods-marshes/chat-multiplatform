package com.github.woodsmarshes.chat.core.network.di

import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.chat.core.network.api.rest.ContactApi
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.ktor.HttpEventBus
import com.github.woodsmarshes.chat.core.network.ktor.HttpEventBusImpl
import com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig
import com.github.woodsmarshes.chat.core.network.ktor.createHttpClient
import com.github.woodsmarshes.chat.core.network.ktor.httpEngine
import com.github.woodsmarshes.chat.core.network.ktor.loadNetworkConfig
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {
    singleOf(::HttpEventBusImpl) bind HttpEventBus::class
    single<NetworkConfig> { loadNetworkConfig() }
    single<HttpClient> {
        createHttpClient(
            httpClientEngine = httpEngine().create(),
            config = get(),
            authTokenDataSource = get(),
            httpEventBus = get(),
        )
    }
    singleOf(::AuthApi)
    singleOf(::ContactApi)
    singleOf(::ConversationApi)
    singleOf(::UserApi)
    singleOf(::FileApi)
    singleOf(::RealtimeApi)

}