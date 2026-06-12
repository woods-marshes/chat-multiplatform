package com.github.woodsmarshes.chat

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.woodsmarshes.chat.base.ServerConfig
import io.ktor.http.*
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val appConfig by inject<ServerConfig>()
    val config = appConfig.tokenConfig

    authentication {
        jwt {
            realm = config.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience)
                    .withIssuer(config.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(config.audience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
            // 支持从 query param 提取 token（浏览器 WebSocket 不允许自定义 Header）
            authHeader { call ->
                call.request.parseAuthorizationHeader()?.takeIf {
                    it.authScheme == AuthScheme.Bearer && it is HttpAuthHeader.Single
                } ?: call.request.queryParameters["access_token"]?.let { token ->
                    HttpAuthHeader.Single(AuthScheme.Bearer, token)
                }
            }
        }
    }
}
