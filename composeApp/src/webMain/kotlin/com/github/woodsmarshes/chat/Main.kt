package com.github.woodsmarshes.chat

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.woodsmarshes.chat.app.ChatApp
import com.github.woodsmarshes.chat.app.di.initKoin
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.common.di.WebContext
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.di.provideDbDriver
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
suspend fun main() {
    val platformContext = WebContext()
    initKoin(
        platformModule = module {
            single<PlatformContext> { platformContext }
        }
    )
    ComposeViewport {
        ChatApp()
    }
}
