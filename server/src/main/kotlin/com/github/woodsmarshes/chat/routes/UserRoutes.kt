package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.user.ChangeRoleRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateUserSettingsRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.ConversationLifecycleService
import com.github.woodsmarshes.chat.service.GroupMembershipService
import com.github.woodsmarshes.chat.service.UserService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.resources.patch
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val userService by inject<UserService>()
    val lifecycleService by inject<ConversationLifecycleService>()
    val membersService by inject<GroupMembershipService>()
    // 检查是否存在，不需要权限
    get<V1.Users.Check> { params ->
        val exists = userService.checkExists(params.email, params.username).getOrThrow()
        call.respond(mapOf("exists" to exists))
    }

    // 搜索用户
    get<V1.Users.Search> { params ->
        val results = userService.searchUsers(params.keyword).getOrThrow()
        call.respond(results)
    }

    authenticate {
        // 获取自己的资料
        get<V1.Users.Me> {
            val userId = call.extractUserId()
            val user = userService.getMyProfile(userId).getOrThrow()
            call.respond(user)
        }

        // 修改自己的资料
        patch<V1.Users.Me> {
            val userId = call.extractUserId()
            val req = call.receive<UpdateProfileRequest>()
            val updatedUser = userService.updateProfile(userId, req).getOrThrow()
            call.respond(updatedUser)
        }

        get<V1.Users.Me.Settings> {
            val userId = call.extractUserId()
            val settings = userService.getUserSettings(userId).getOrThrow()
            call.respond(settings)
        }

        put<V1.Users.Me.Settings> {
            val userId = call.extractUserId()
            val req = call.receive<UpdateUserSettingsRequest>()
            userService.updateUserSettings(userId, req).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 获取我的会话列表
        get<V1.Users.Me.Conversations> {
            val userId = call.extractUserId()
            val conversations = lifecycleService.getUserConversations(userId).getOrThrow()
            call.respond(conversations)
        }

        // 获取群组申请
        get<V1.Users.Me.GroupRequests> { params ->
            val userId = call.extractUserId()
            val rsp = membersService.getGroupRequests(userId, params.status).getOrThrow()
            call.respond(rsp)
        }

        // 获取收到的群申请
        get<V1.Users.Me.GroupRequests.IncomingGroupRequests> { params ->
            val userId = call.extractUserId()
            val rsp = membersService.getIncomingGroupRequests(userId, params.parent.status).getOrThrow()
            call.respond(rsp)
        }

        // 获取发出的群申请
        get<V1.Users.Me.GroupRequests.SentGroupRequests> { params ->
            val userId = call.extractUserId()
            val rsp = membersService.getSentGroupRequests(userId, params.parent.status).getOrThrow()
            call.respond(rsp)
        }

        // 获取他人资料
        get<V1.Users.Id> { params ->
            val selfId = call.extractUserId()
            val user = userService.getUserProfileForViewer(targetUserId = params.id, viewerId = selfId).getOrThrow()
            call.respond(user)
        }

        // 修改用户角色 (管理员专用)
        put<V1.Users.Id.Role> { params ->
            val adminId = call.extractUserId()
            val req = call.receive<ChangeRoleRequest>()
            userService.changeUserRole(adminId, params.parent.id, req.newRole).getOrThrow()
            call.respond(mapOf("success" to true))
        }
    }
}