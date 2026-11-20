package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.contract.api.V1
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionListResponse
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.toAuthSessionInfo
import com.github.woodsmarshes.chat.utils.extractSessionId
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.server.auth.authenticate
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * Self-service login-session ("my devices") management. Users can list
 * their own sessions, revoke one, or revoke all others — the current
 * session is identified by the access token's session claim.
 */
fun Route.sessionRoutes() {
    val authSessionRepository by inject<AuthSessionRepository>()
    val userRepository by inject<UserRepository>()

    authenticate {
        get<V1.Users.Me.Sessions> {
            val userId = call.extractUserId()
            val currentSessionId = call.extractSessionId()
            // Only live sessions are devices the user can still act on;
            // revoked/expired rows are history, not devices.
            val sessions =
                authSessionRepository
                    .listByUser(userId)
                    .filter { it.revokedAt == null }
            call.respond(
                AuthSessionListResponse(
                    items = sessions.map { it.toAuthSessionInfo(isCurrent = it.id == currentSessionId) },
                    currentSessionId = currentSessionId,
                ),
            )
        }

        delete<V1.Users.Me.Sessions.SessionId> { params ->
            val userId = call.extractUserId()
            // revokeById is ownership-scoped: a session id belonging to
            // another user simply does not match.
            authSessionRepository.revokeById(params.sessionId, userId)
            call.respond(mapOf("success" to true))
        }

        post<V1.Users.Me.Sessions.RevokeOthers> {
            val userId = call.extractUserId()
            val currentSessionId = call.extractSessionId()
            authSessionRepository.revokeAllByUser(userId, exceptSessionId = currentSessionId)
            call.respond(mapOf("success" to true))
        }
    }
}
