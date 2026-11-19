package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.contract.api.V1
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminLogListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminMessageListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminStatsResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.ResetPasswordResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.UpdateBanRequest
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionListResponse
import com.github.woodsmarshes.chat.core.contract.dto.user.ChangeRoleRequest
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

/** Application-administration endpoints under `/v1/admin`. Requires an ADMIN caller. */
class AdminApi(
    private val client: HttpClient,
) {
    suspend fun listUsers(
        keyword: String?,
        role: UserRole?,
        banned: Boolean?,
        page: Int,
        limit: Int,
    ): AdminUserListResponse = client.get(
        V1.Admin.Users(keyword = keyword, role = role, banned = banned, page = page, limit = limit)
    ).body()

    suspend fun getUser(id: Uuid): AdminUserDetailResponse = client.get(
        V1.Admin.Users.Id(parent = V1.Admin.Users(), id = id)
    ).body()

    suspend fun setBanned(id: Uuid, banned: Boolean) {
        client.put(
            V1.Admin.Users.Id.Ban(parent = V1.Admin.Users.Id(parent = V1.Admin.Users(), id = id))
        ) {
            setBody(UpdateBanRequest(banned = banned))
        }
    }

    suspend fun changeRole(id: Uuid, newRole: UserRole) {
        client.put(
            V1.Admin.Users.Id.Role(parent = V1.Admin.Users.Id(parent = V1.Admin.Users(), id = id))
        ) {
            setBody(ChangeRoleRequest(newRole = newRole))
        }
    }

    suspend fun listUserSessions(id: Uuid): AuthSessionListResponse = client.get(
        V1.Admin.Users.Id.Sessions(parent = V1.Admin.Users.Id(parent = V1.Admin.Users(), id = id))
    ).body()

    suspend fun revokeAllUserSessions(id: Uuid) {
        val user = V1.Admin.Users.Id(parent = V1.Admin.Users(), id = id)
        client.post(V1.Admin.Users.Id.Sessions.RevokeAll(parent = V1.Admin.Users.Id.Sessions(parent = user)))
    }

    suspend fun resetPassword(id: Uuid): ResetPasswordResponse = client.post(
        V1.Admin.Users.Id.ResetPassword(parent = V1.Admin.Users.Id(parent = V1.Admin.Users(), id = id))
    ).body()

    suspend fun listConversations(
        type: ConversationType?,
        page: Int,
        limit: Int,
    ): AdminConversationListResponse = client.get(
        V1.Admin.Conversations(type = type, page = page, limit = limit)
    ).body()

    suspend fun getConversation(id: Uuid): AdminConversationDetailResponse = client.get(
        V1.Admin.Conversations.Id(parent = V1.Admin.Conversations(), id = id)
    ).body()

    suspend fun deleteConversation(id: Uuid) {
        client.delete(V1.Admin.Conversations.Id(parent = V1.Admin.Conversations(), id = id))
    }

    suspend fun listConversationMessages(
        conversationId: Uuid,
        beforeId: Uuid?,
        limit: Int,
    ): AdminMessageListResponse = client.get(
        V1.Admin.Conversations.Id.Messages(
            parent = V1.Admin.Conversations.Id(parent = V1.Admin.Conversations(), id = conversationId),
            beforeId = beforeId,
            limit = limit,
        )
    ).body()

    suspend fun revokeMessage(messageId: Uuid) {
        client.post(
            V1.Admin.Messages.Id.Revoke(
                parent = V1.Admin.Messages.Id(parent = V1.Admin.Messages(), id = messageId)
            )
        )
    }

    suspend fun getStats(): AdminStatsResponse = client.get(V1.Admin.Stats()).body()

    suspend fun listLogs(level: String?, limit: Int): AdminLogListResponse = client.get(
        V1.Admin.Logs(level = level, limit = limit)
    ).body()

    suspend fun listAuditLogs(limit: Int): AdminLogListResponse = client.get(
        V1.Admin.AuditLogs(limit = limit)
    ).body()
}
