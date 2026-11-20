package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.exceptions.AuthenticationException
import com.github.woodsmarshes.chat.routes.adminRoutes
import com.github.woodsmarshes.chat.routes.articleRoutes
import com.github.woodsmarshes.chat.routes.authRoutes
import com.github.woodsmarshes.chat.routes.contactRoutes
import com.github.woodsmarshes.chat.routes.conversationRoutes
import com.github.woodsmarshes.chat.routes.fileRoutes
import com.github.woodsmarshes.chat.routes.realtimeRoutes
import com.github.woodsmarshes.chat.routes.sessionRoutes
import com.github.woodsmarshes.chat.routes.syncRoutes
import com.github.woodsmarshes.chat.routes.userRoutes
import com.github.woodsmarshes.chat.utils.toHttpStatusCode
import io.ktor.http.CacheControl
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import java.io.File

fun Application.configureRouting() {
    // DoubleReceive must NOT be installed here: its body cache disposal
    // discards the request ByteReadChannel, racing the WebSocket upgrade's
    // reader loop with a concurrent read that kills the socket. Nothing in
    // this app reads a request body twice.
    install(AutoHeadResponse)
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val domainError = cause.error
            val status = domainError.toHttpStatusCode()
            call.respond(status, domainError)
        }

        exception<AuthenticationException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }

        // Malformed JSON, missing required fields or an unsupported content
        // type surface from call.receive<T>() as BadRequestException — a
        // client error, not a server fault; the catch-all below used to
        // report it as a 500 and pollute error metrics and logs. Deliberately
        // NOT mapping IllegalArgumentException here: that would hide real
        // server bugs behind a 400.
        exception<BadRequestException> { call, cause ->
            this@configureRouting.environment.log.warn(
                "Malformed request to ${call.request.local.uri}: ${cause.message}",
            )
            call.respondText(text = "Malformed request body", status = HttpStatusCode.BadRequest)
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

    // User-uploaded files are served straight from disk: without nosniff a
    // polyglot payload could be reinterpreted as script/HTML by the browser.
    install(
        createApplicationPlugin("UploadSecurityHeaders") {
            onCall { call ->
                if (call.request.local.uri
                        .startsWith("/uploads/")
                ) {
                    call.response.headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
                }
            }
        },
    )

    routing {
        authRoutes()
        articleRoutes()
        userRoutes()
        conversationRoutes()
        syncRoutes()
        authenticate {
            adminRoutes()
            contactRoutes()
            fileRoutes()
            realtimeRoutes()
            sessionRoutes()
        }
        staticFiles("/uploads", File("uploads")) {
            cacheControl { file ->
                when {
                    file.path.contains("avatar") -> {
                        listOf(
                            CacheControl.MaxAge(
                                maxAgeSeconds = 3600,
                                proxyMaxAgeSeconds = 1800,
                                mustRevalidate = true,
                                proxyRevalidate = true,
                                visibility = CacheControl.Visibility.Public,
                            ),
                        )
                    }

                    file.path.contains("image") -> {
                        listOf(
                            CacheControl.MaxAge(
                                maxAgeSeconds = 86400 * 30,
                                proxyMaxAgeSeconds = 86400 * 7,
                                mustRevalidate = false,
                                visibility = CacheControl.Visibility.Public,
                            ),
                        )
                    }

                    file.path.contains("audio") -> {
                        listOf(
                            CacheControl.MaxAge(
                                maxAgeSeconds = 86400 * 7,
                                proxyMaxAgeSeconds = 86400 * 3,
                                mustRevalidate = false,
                                visibility = CacheControl.Visibility.Public,
                            ),
                        )
                    }

                    file.path.contains("video") -> {
                        listOf(
                            CacheControl.MaxAge(
                                maxAgeSeconds = 86400 * 7,
                                proxyMaxAgeSeconds = 86400 * 3,
                                mustRevalidate = false,
                                visibility = CacheControl.Visibility.Public,
                            ),
                        )
                    }

                    else -> {
                        listOf(
                            CacheControl.MaxAge(
                                maxAgeSeconds = 3600 * 4,
                                mustRevalidate = true,
                                visibility = CacheControl.Visibility.Public,
                            ),
                        )
                    }
                }
            }
        }

        // Serve web module SPA from classpath resources (packaged into the shadow jar)
        singlePageApplication {
            useResources = true
            filesPath = "static"
        }
    }
}
