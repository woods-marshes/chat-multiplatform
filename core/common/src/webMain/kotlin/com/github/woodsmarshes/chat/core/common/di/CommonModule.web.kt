package com.github.woodsmarshes.chat.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

class WebContext() : PlatformContext
actual fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.Default