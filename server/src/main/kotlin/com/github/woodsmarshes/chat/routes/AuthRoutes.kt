package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.AuthService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import kotlin.collections.mapOf

fun Route.authRoutes() {
    val authService by inject<AuthService>()
    post<V1.Auth.Register> {
        val req = call.receive<RegisterRequest>()
        val res = authService.register(req).getOrThrow()
        call.respond(res)
    }

    post<V1.Auth.Login> {
        val req = call.receive<LoginRequest>()
        val res = authService.login(req).getOrThrow()
        call.respond(res)
    }

    post<V1.Auth.Refresh> {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?.removePrefix("Bearer ") ?: run {
            call.respond(
                io.ktor.http.HttpStatusCode.Unauthorized,
                mapOf("error" to "Missing token")
            )
            return@post
        }
        val res = authService.refreshToken(authHeader).getOrThrow()
        call.respond(res)
    }

    authenticate {
        get<V1.Auth.Verify> {
            val userId = call.extractUserId()
            call.respond(HttpStatusCode.OK, mapOf("userId" to userId.toString()))
        }
    }
}