package com.github.woodsmarshes.chat.core.common.di

import com.github.woodsmarshes.chat.core.common.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

interface PlatformContext

val commonModule = module {
    single<AppDispatchers> { AppDispatchers }
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + AppDispatchers.default)
    }
}

expect fun provideIODispatcher(): CoroutineDispatcher