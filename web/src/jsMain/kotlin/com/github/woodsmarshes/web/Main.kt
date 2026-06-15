package com.github.woodsmarshes.web

import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.common.di.WebContext
import com.github.woodsmarshes.chat.core.common.di.commonModule
import com.github.woodsmarshes.chat.core.datastore.di.dataStoreModule
import com.github.woodsmarshes.chat.core.network.di.networkModule
import com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig
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
                single<NetworkConfig> { loadJsNetworkConfig() }
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

fun loadJsNetworkConfig(): NetworkConfig {
    val hostname = window.location.hostname.takeIf { it.isNotEmpty() } ?: "127.0.0.1"

    println("=== DEBUG: loadJsNetworkConfig resolved hostname = $hostname ===")

    val protocolString = window.location.protocol
    val isTls = protocolString.startsWith("https", ignoreCase = true)

    val portString = window.location.port
    var port = if (portString.isNotEmpty()) {
        portString.toIntOrNull() ?: if (isTls) 443 else 80
    } else {
        if (isTls) 443 else 80
    }

    if ((hostname == "localhost" || hostname == "127.0.0.1") && port != 9051) {
        port = 9051
    }

    return NetworkConfig(
        host = hostname,
        port = port,
        useTls = isTls
    )
}
