package com.github.woodsmarshes.chat

import android.app.Application
import com.github.woodsmarshes.chat.app.di.initKoin
import com.github.woodsmarshes.chat.core.common.di.AndroidContext
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.di.provideDbDriver
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

object Static {
    init {
        System.setProperty("kotlin-logging-to-android-native", "true")
    }
}

class ChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val static = Static
        val platformContext = AndroidContext(this)
        initKoin(
            platformModule = module {
                single<PlatformContext> { platformContext }
            }
        )
    }
}
