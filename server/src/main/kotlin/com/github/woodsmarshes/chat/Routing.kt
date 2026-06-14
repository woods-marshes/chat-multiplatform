package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.routes.articleRoutes
import com.github.woodsmarshes.chat.routes.authRoutes
import com.github.woodsmarshes.chat.routes.contactRoutes
import com.github.woodsmarshes.chat.routes.conversationRoutes
import com.github.woodsmarshes.chat.routes.fileRoutes
import com.github.woodsmarshes.chat.routes.realtimeRoutes
import com.github.woodsmarshes.chat.routes.userRoutes
import com.github.woodsmarshes.chat.utils.toHttpStatusCode
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import java.io.File
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(DoubleReceive)
    install(AutoHeadResponse)
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val domainError = cause.error
            val status = domainError.toHttpStatusCode()
            call.respond(status, domainError)
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }
        exception<Throwable> { call, cause ->
            this@configureRouting.environment.log.error("Unhandled exception: ${cause.message}", cause)
            call.respondText(
                text = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
    install(Resources)

    routing {
        authRoutes()
        articleRoutes()
        userRoutes()
        conversationRoutes()
        authenticate {
            contactRoutes()
            fileRoutes()
            realtimeRoutes()
        }
        staticFiles("/uploads", File("uploads")) {
            cacheControl { file ->
                when {
                    file.path.contains("avatar") -> listOf(
                        CacheControl.MaxAge(
                            maxAgeSeconds = 3600,
                            proxyMaxAgeSeconds = 1800,
                            mustRevalidate = true,
                            proxyRevalidate = true,
                            visibility = CacheControl.Visibility.Public
                        )
                    )
                    file.path.contains("image") -> listOf(
                        CacheControl.MaxAge(
                            maxAgeSeconds = 86400 * 30,
                            proxyMaxAgeSeconds = 86400 * 7,
                            mustRevalidate = false,
                            visibility = CacheControl.Visibility.Public
                        )
                    )
                    file.path.contains("audio") -> listOf(
                        CacheControl.MaxAge(
                            maxAgeSeconds = 86400 * 7,
                            proxyMaxAgeSeconds = 86400 * 3,
                            mustRevalidate = false,
                            visibility = CacheControl.Visibility.Public
                        )
                    )
                    file.path.contains("video") -> listOf(
                        CacheControl.MaxAge(
                            maxAgeSeconds = 86400 * 7,
                            proxyMaxAgeSeconds = 86400 * 3,
                            mustRevalidate = false,
                            visibility = CacheControl.Visibility.Public
                        )
                    )
                    else -> listOf(
                        CacheControl.MaxAge(
                            maxAgeSeconds = 3600 * 4,
                            mustRevalidate = true,
                            visibility = CacheControl.Visibility.Public
                        )
                    )
                }
            }
        }

        // Serve web module SPA from classpath resources (copied via processResources)
        singlePageApplication {
            useResources = true
            filesPath = "static"
        }
    }
}
