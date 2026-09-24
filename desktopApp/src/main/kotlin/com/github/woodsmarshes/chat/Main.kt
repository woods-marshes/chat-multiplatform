package com.github.woodsmarshes.chat

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.github.woodsmarshes.chat.app.ChatApp
import com.github.woodsmarshes.chat.app.di.initKoin
import com.github.woodsmarshes.chat.core.common.di.DesktopContext
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.resources.composeApp.Res
import com.github.woodsmarshes.chat.resources.composeApp.app_icon
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import org.jetbrains.compose.resources.painterResource
import org.koin.dsl.module

fun main() {
    val platformContext = DesktopContext()
    nucleusApplication(backend = NucleusBackend.Tao) {
        initKoin(
            platformModule = module {
                single<PlatformContext> { platformContext }
            }
        )
        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = "Chat",
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
            icon = painterResource(resource = Res.drawable.app_icon),
        ) {
            // Provides the drag region and window control buttons on Tao
            // windows (there is no native chrome otherwise).
            TitleBar { }
            ChatApp()
        }
    }
}
