package com.github.woodsmarshes.chat.core.common

import com.github.woodsmarshes.chat.core.common.di.provideIODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

sealed interface AppDispatchers {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher

    companion object : AppDispatchers {
        override val io: CoroutineDispatcher = provideIODispatcher()
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
}