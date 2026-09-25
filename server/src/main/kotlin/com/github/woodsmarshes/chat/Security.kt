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
            // The query-parameter fallback exists only because browsers cannot
            // set headers on a WebSocket handshake. It is restricted to the
            // websocket route: on REST endpoints a token in the query string
            // leaks into access logs, proxy logs and browser history.
            authHeader { call ->
                val header = call.request.parseAuthorizationHeader()
                if (header != null) {
                    header.takeIf {
                        it.authScheme == AuthScheme.Bearer && it is HttpAuthHeader.Single
                    }
                } else if (call.request.local.uri.startsWith(WS_PATH)) {
                    call.request.queryParameters["access_token"]?.let { token ->
                        HttpAuthHeader.Single(AuthScheme.Bearer, token)
                    }
                } else {
                    null
                }
            }
        }
    }
}

private const val WS_PATH = "/ws"
