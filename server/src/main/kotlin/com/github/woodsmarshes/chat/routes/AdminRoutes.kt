package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.contract.api.V1
import com.github.woodsmarshes.chat.core.contract.dto.admin.UpdateBanRequest
import com.github.woodsmarshes.chat.core.contract.dto.user.ChangeRoleRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.AdminService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * Application-administration endpoints. Every handler re-verifies the
 * caller's ADMIN role through [AdminService.requireAdmin] — the JWT only
 * proves identity, never privilege.
 */
fun Route.adminRoutes() {
    val adminService by inject<AdminService>()

    authenticate {
        get<V1.Admin.Users> { params ->
            val adminId = call.extractUserId()
            adminService.requireAdmin(adminId).getOrThrow()
            val result = adminService.listUsers(
                keyword = params.keyword,
                role = params.role,
                banned = params.banned,
                page = params.page,
                limit = params.limit,
            ).getOrThrow()
            call.respond(result)
        }

        get<V1.Admin.Users.Id> { params ->
            val adminId = call.extractUserId()
            adminService.requireAdmin(adminId).getOrThrow()
            val result = adminService.getUserDetail(params.id).getOrThrow()
            call.respond(result)
        }

        put<V1.Admin.Users.Id.Ban> { params ->
            val adminId = call.extractUserId()
            val req = call.receive<UpdateBanRequest>()
            adminService.setBanned(adminId, params.parent.id, req.banned).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        put<V1.Admin.Users.Id.Role> { params ->
            val adminId = call.extractUserId()
            val req = call.receive<ChangeRoleRequest>()
            adminService.changeRole(adminId, params.parent.id, req.newRole).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        get<V1.Admin.Users.Id.Sessions> { params ->
            val adminId = call.extractUserId()
            adminService.requireAdmin(adminId).getOrThrow()
            val result = adminService.listUserSessions(adminId, params.parent.id).getOrThrow()
            call.respond(result)
        }

        post<V1.Admin.Users.Id.Sessions.RevokeAll> { params ->
            val adminId = call.extractUserId()
            val revoked = adminService.revokeAllUserSessions(adminId, params.parent.parent.id).getOrThrow()
            call.respond(mapOf("revoked" to revoked))
        }

        post<V1.Admin.Users.Id.ResetPassword> { params ->
            val adminId = call.extractUserId()
            val result = adminService.resetPassword(adminId, params.parent.id).getOrThrow()
            call.respond(result)
        }

        get<V1.Admin.Conversations> { params ->
            val adminId = call.extractUserId()
            val result = adminService.listConversations(adminId, params.type, params.page, params.limit).getOrThrow()
            call.respond(result)
        }

        get<V1.Admin.Conversations.Id> { params ->
            val adminId = call.extractUserId()
            val result = adminService.getConversationDetail(adminId, params.id).getOrThrow()
            call.respond(result)
        }

        get<V1.Admin.Conversations.Id.Messages> { params ->
            val adminId = call.extractUserId()
            val result = adminService.listConversationMessages(adminId, params.parent.id, params.page, params.limit).getOrThrow()
            call.respond(result)
        }

        post<V1.Admin.Messages.Id.Revoke> { params ->
            val adminId = call.extractUserId()
            adminService.revokeMessage(adminId, params.parent.id).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        get<V1.Admin.Stats> {
            val adminId = call.extractUserId()
            val result = adminService.getStats(adminId).getOrThrow()
            call.respond(result)
        }

        get<V1.Admin.Logs> { params ->
            val adminId = call.extractUserId()
            val result = adminService.listLogs(adminId, params.level, params.limit).getOrThrow()
            call.respond(result)
        }
    }
}