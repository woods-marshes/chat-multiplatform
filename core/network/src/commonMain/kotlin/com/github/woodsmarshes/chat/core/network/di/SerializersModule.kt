package com.github.woodsmarshes.chat.core.network.di

import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import org.koin.dsl.module

val serializersModule = module {
    single {
        ProjectJson
    }
    single {
        ProjectProtobuf
    }
}