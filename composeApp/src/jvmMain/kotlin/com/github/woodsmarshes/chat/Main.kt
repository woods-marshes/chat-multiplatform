package com.github.woodsmarshes.chat

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.github.woodsmarshes.chat.app.ChatApp
import com.github.woodsmarshes.chat.app.di.initKoin
import com.github.woodsmarshes.chat.core.common.di.DesktopContext
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.resources.Res
import com.github.woodsmarshes.chat.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.dsl.module

fun main() = application {
    val platformContext = DesktopContext()
    initKoin(
        platformModule = module {
            single<PlatformContext> { platformContext }
        }
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Chat",
        state = WindowState(size = DpSize(1200.dp, 800.dp)),
        icon = painterResource(resource = Res.drawable.app_icon)
    ) {
        ChatApp()
    }
}
