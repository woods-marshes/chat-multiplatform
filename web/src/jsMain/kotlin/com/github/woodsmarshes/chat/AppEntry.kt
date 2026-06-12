package com.github.woodsmarshes.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.github.woodsmarshes.chat.di.KoinInit
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxHeight
import com.varabyte.kobweb.compose.ui.modifiers.scrollBehavior
import com.varabyte.kobweb.core.App
import com.varabyte.kobweb.core.init.InitKobweb
import com.varabyte.kobweb.core.init.InitKobwebContext
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.SilkApp
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.common.SmoothColorStyle
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.loadFromLocalStorage
import com.varabyte.kobweb.silk.theme.colors.saveToLocalStorage
import com.varabyte.kobweb.silk.theme.colors.systemPreference
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import org.koin.compose.KoinApplication
import org.koin.core.Koin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

private const val COLOR_MODE_KEY = "example:colorMode"

lateinit var koin: Koin
lateinit var log: KLogger

@InitKobweb
fun initKobweb(ctx: InitKobwebContext) {
    KotlinLoggingConfiguration.direct.logLevel = Level.TRACE
    log = KotlinLogging.logger {}
    koin = KoinInit().init()
}

@Composable
inline fun <reified T : Any> rememberKoinInstance(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): T {
    return remember {
        koin.get<T>(
            qualifier = qualifier,
            parameters = parameters
        )
    }
}
@InitSilk
fun initColorMode(ctx: InitSilkContext) {
    ctx.config.initialColorMode = ColorMode.loadFromLocalStorage(COLOR_MODE_KEY) ?: ColorMode.systemPreference
}

@InitSilk
fun initStyles(ctx: InitSilkContext) {
    ctx.stylesheet.apply {
        registerStyleBase("html, body") { Modifier.fillMaxHeight() }
        registerStyleBase("body") { Modifier.scrollBehavior(ScrollBehavior.Smooth) }
    }
}

@App
@Composable
fun AppEntry(content: @Composable () -> Unit) {

    val appState = rememberAppState(
        networkMonitor = koin.get(),
        httpErrorEventChannel = koin.get(),
        userRepository = koin.get(),
        messageRepository = koin.get(),
        coroutineScope = koin.get()
    )
    SilkApp {
        val colorMode = ColorMode.current
        LaunchedEffect(colorMode) {
            colorMode.saveToLocalStorage(COLOR_MODE_KEY)
        }
        CompositionLocalProvider(LocalAppState provides appState) {
            Surface(SmoothColorStyle.toModifier().fillMaxHeight()) {
                content()
            }
        }
    }
}
