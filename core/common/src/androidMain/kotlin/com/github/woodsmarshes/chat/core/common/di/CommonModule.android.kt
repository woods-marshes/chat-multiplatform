package com.github.woodsmarshes.chat.core.common.di

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

class AndroidContext(val context: Context): PlatformContext
actual fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO