package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateConversationRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.HandleGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.InviteUserRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.InviteUsersRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.JoinGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.core.network.dto.message.ReadReportRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.ConversationLifecycleService
import com.github.woodsmarshes.chat.service.ConversationSettingsService
import com.github.woodsmarshes.chat.service.GroupMembershipService
import com.github.woodsmarshes.chat.service.MessageService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.conversationRoutes() {
    val lifecycleService by inject<ConversationLifecycleService>()
    val membersService by inject<GroupMembershipService>()
    val settingsService by inject<ConversationSettingsService>()
    val messageService by inject<MessageService>()
    // 搜索群组
    get<V1.Conversations.Search> { params ->
        val groups = lifecycleService.searchGroups(params.keyword).getOrThrow()
        call.respond(groups)
    }

    // 检查群组handle是否存在
    get<V1.Conversations.Check> { params ->
        val exists = lifecycleService.checkHandle(params.handle).getOrThrow()
        call.respond(mapOf("exists" to exists))
    }

    authenticate {
        //创建会话 (单聊或群聊)
        post<V1.Conversations> {
            val userId = call.extractUserId()
            val req = call.receive<CreateConversationRequest>()
            val result = lifecycleService.createConversation(userId, req).getOrThrow()
            call.respond(result)
        }
        // 获取会话详情
        get<V1.Conversations.Id> { params ->
            val userId = call.extractUserId()
            val detail = lifecycleService.getConversationDetail(params.id, userId).getOrThrow()
            call.respond(detail)
        }

        // 解散或删除会话
        delete<V1.Conversations.Id> { params ->
            val userId = call.extractUserId()
            lifecycleService.deleteConversation(params.id, userId).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 获取成员列表
        get<V1.Conversations.Id.Participants> { params ->
            val userId = call.extractUserId()
            val members = membersService.getParticipants(params.parent.id, userId).getOrThrow()
            call.respond(members)
        }

        // 批量邀请用户到群组
        post<V1.Conversations.Id.InviteUsers> { params ->
            val userId = call.extractUserId()
            val req = call.receive<InviteUsersRequest>()
            membersService.inviteUsersToGroup(
                conversationId = params.parent.id,
                inviterId = userId,
                userIds = req.userIds
            ).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 邀请单个用户到群组
        post<V1.Conversations.Id.Invite> { params ->
            val userId = call.extractUserId()
            val req = call.receive<InviteUserRequest>()
            membersService.inviteUser(params.parent.id, userId, req.targetUserId).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 主动加入群组
        post<V1.Conversations.Id.Join> { params ->
            val userId = call.extractUserId()
            val req = call.receive<JoinGroupRequest>() // 可能包含附加消息
            membersService.joinGroup(params.parent.id, userId, req.message).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 退出群组
        post<V1.Conversations.Id.Leave> { params ->
            val userId = call.extractUserId()
            membersService.leaveGroup(params.parent.id, userId).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 更新群设置
        put<V1.Conversations.Id.Settings.Group> { params ->
            val userId = call.extractUserId()
            val req = call.receive<UpdateConversationSettingsRequest>()
            settingsService.updateGroupSettings(params.parent.parent.id, userId, req).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 更新群个人设置
        put<V1.Conversations.Id.Settings.Personal> { params ->
            val userId = call.extractUserId()
            val req = call.receive<ParticipantSettings>()
            settingsService.updatePersonalSettings(params.parent.parent.id, userId, req).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 获取消息历史
        get<V1.Conversations.Id.Messages> { params ->
            val userId = call.extractUserId()
            val messages = messageService.getHistory(
                userId = userId,
                conversationId = params.parent.id,
                limit = params.limit,
                beforeId = params.beforeId,
            ).getOrThrow()
            call.respond(messages)
        }

        // 搜索消息
        get<V1.Conversations.Id.Messages.Search> { params ->
            val userId = call.extractUserId()
            val messages = messageService.searchMessages(
                userId = userId,
                conversationId = params.parent.parent.id,
                keyword = params.keyword,
                limit = params.parent.limit
            ).getOrThrow()
            call.respond(messages)
        }

        // 增量同步消息
        get<V1.Conversations.Id.Messages.Sync> { params ->
            val userId = call.extractUserId()
            val messages = messageService.syncMessages(
                userId = userId,
                conversationId = params.parent.parent.id,
                afterId = params.afterId,
                limit = params.parent.limit
            ).getOrThrow()
            call.respond(messages)
        }

        // 上报已读
        post<V1.Conversations.Id.Read> { params ->
            val userId = call.extractUserId()
            val req = call.receive<ReadReportRequest>() // 包含 messageId
            messageService.markAsRead(params.parent.id, userId, req.messageId).getOrThrow()
            call.respond(mapOf("success" to true))
        }

        // 获取该群的申请列表 (管理员视角)
        get<V1.Conversations.Id.Requests> { params ->
            val userId = call.extractUserId()
            val response = membersService.getGroupRequestsByConversation(
                conversationId = params.parent.id,
                adminId = userId,
                status = params.status
            ).getOrThrow()
            call.respond(response)
        }

        // 处理群申请 (审批/拒绝)
        post<V1.Conversations.Id.Requests> { params ->
            val userId = call.extractUserId()
            val req = call.receive<List<HandleGroupRequest>>() // { action: "APPROVE"/"REJECT", reason: "..." }
            membersService.handleGroupRequest(
                adminId = userId,
                requests = req
            ).getOrThrow()
            call.respond(mapOf("success" to true))
        }
    }
}