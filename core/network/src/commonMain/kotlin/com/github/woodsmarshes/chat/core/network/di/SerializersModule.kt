package com.github.woodsmarshes.chat.core.network.di

import com.github.woodsmarshes.chat.core.contract.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.contract.serialization.ProjectProtobuf
import org.koin.dsl.module

val serializersModule =
    module {
        single {
            ProjectJson
        }
        single {
            ProjectProtobuf
        }
    }
