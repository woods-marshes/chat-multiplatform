package com.github.woodsmarshes.web

import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.common.di.WebContext
import com.github.woodsmarshes.chat.core.common.di.commonModule
import com.github.woodsmarshes.chat.core.datastore.di.dataStoreModule
import com.github.woodsmarshes.chat.core.network.di.networkModule
import com.github.woodsmarshes.web.wrapper.createRoot
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import react.create

inline fun <reified T> koinInject(): T = GlobalContext.get().get(T::class)

fun main() {
    val platformContext = WebContext()
    startKoin {
        modules(
            module {
                single<PlatformContext> { platformContext }
            },
            commonModule,
            dataStoreModule,
            networkModule,
        )
    }

    window.addEventListener("load", {
        val container = document.getElementById("root")
        createRoot(container).render(App.create())
    })
}
