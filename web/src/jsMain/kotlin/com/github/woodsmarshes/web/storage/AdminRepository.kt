package com.github.woodsmarshes.web.storage

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminLogListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminMessageListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminStatsResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.ResetPasswordResponse
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionListResponse
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.api.rest.AdminApi
import com.github.woodsmarshes.web.koinInject
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

/** Admin data access layer. Delegates to the shared Ktor API client; the server re-checks ADMIN. */
object AdminRepository {
    suspend fun listUsers(
        keyword: String?,
        role: UserRole?,
        banned: Boolean?,
        page: Int,
        limit: Int = 20,
    ): AdminUserListResponse = koinInject<AdminApi>().listUsers(keyword, role, banned, page, limit)

    suspend fun getUser(id: Uuid): AdminUserDetailResponse = koinInject<AdminApi>().getUser(id)

    suspend fun setBanned(
        id: Uuid,
        banned: Boolean,
    ) = koinInject<AdminApi>().setBanned(id, banned)

    suspend fun changeRole(
        id: Uuid,
        newRole: UserRole,
    ) = koinInject<AdminApi>().changeRole(id, newRole)

    suspend fun listUserSessions(id: Uuid): AuthSessionListResponse = koinInject<AdminApi>().listUserSessions(id)

    suspend fun revokeAllUserSessions(id: Uuid) = koinInject<AdminApi>().revokeAllUserSessions(id)

    suspend fun resetPassword(id: Uuid): ResetPasswordResponse = koinInject<AdminApi>().resetPassword(id)

    suspend fun listConversations(
        type: ConversationType?,
        page: Int,
        limit: Int = 20,
    ): AdminConversationListResponse = koinInject<AdminApi>().listConversations(type, page, limit)

    suspend fun getConversation(id: Uuid): AdminConversationDetailResponse = koinInject<AdminApi>().getConversation(id)

    suspend fun deleteConversation(id: Uuid) = koinInject<AdminApi>().deleteConversation(id)

    suspend fun listConversationMessages(
        conversationId: Uuid,
        beforeId: Uuid?,
        limit: Int = 50,
    ): AdminMessageListResponse = koinInject<AdminApi>().listConversationMessages(conversationId, beforeId, limit)

    suspend fun revokeMessage(messageId: Uuid) = koinInject<AdminApi>().revokeMessage(messageId)

    suspend fun getStats(): AdminStatsResponse = koinInject<AdminApi>().getStats()

    suspend fun listLogs(
        level: String?,
        limit: Int = 100,
    ): AdminLogListResponse = koinInject<AdminApi>().listLogs(level, limit)

    suspend fun listAuditLogs(limit: Int = 100): AdminLogListResponse = koinInject<AdminApi>().listAuditLogs(limit)
}

/** Turns a failed admin call into a readable message; Ktor's raw text embeds URL/body. */
suspend fun describeAdminError(e: Throwable): String {
    val response = (e as? ResponseException)?.response ?: return "操作失败"
    if (response.status == HttpStatusCode.Unauthorized) return "登录已失效，请重新登录"
    val error = runCatching { response.body<UserError>() }.getOrNull() ?: return "操作失败"
    return when (error) {
        UserError.PermissionDenied -> "没有权限"
        UserError.NotFound -> "未找到"
        UserError.InvalidRequest -> "请求无效"
        UserError.UpdateFailed -> "操作失败"
        is UserError.Unknown -> error.message ?: "操作失败"
    }
}

fun isAdminRole(role: UserRole?): Boolean = role == UserRole.ADMIN
