package com.github.woodsmarshes.chat.di

import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.base.hashing.HashingServiceImpl
import com.github.woodsmarshes.chat.base.jwt.TokenService
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.events.EventBusImpl
import com.github.woodsmarshes.chat.prometheusRegistry
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import com.github.woodsmarshes.chat.utils.TemporaryUploadStoreImpl
import com.github.woodsmarshes.chat.websocket.RealtimeDelivery
import com.github.woodsmarshes.chat.websocket.SessionIndex
import com.github.woodsmarshes.chat.websocket.WebSocketRealtimeDelivery
import com.github.woodsmarshes.chat.websocket.WebSocketSessionManager
import io.micrometer.core.instrument.MeterRegistry
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

    // The Prometheus registry backs both EventBusImpl's drop counter and the
    // MicrometerMetrics plugin; register it explicitly so singleOf's
    // reflective constructor injection can resolve the MeterRegistry
    // parameter (Kotlin default values do not apply to Koin).
    single<MeterRegistry> { prometheusRegistry }

    singleOf(::EventBusImpl) {
        bind<EventBus>()
    }

    singleOf(::TemporaryUploadStoreImpl) {
        bind<TemporaryUploadStore>()
    }

    // One shared session index: WebSocketSessionManager records live
    // connections into it and RealtimeDelivery looks targets up in the
    // same index — separate instances would silently never meet.
    single { SessionIndex() }

    single {
        WebSocketSessionManager(sessions = get())
    }

    single<RealtimeDelivery> {
        WebSocketRealtimeDelivery(
            sessions = get(),
            participants = get(),
            logger = get(),
            scope = get(),
        )
    }
}