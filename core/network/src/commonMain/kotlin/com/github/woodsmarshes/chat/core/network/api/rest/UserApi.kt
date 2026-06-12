package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateUserSettingsRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.patch
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

class UserApi(
    private val client: HttpClient,
) {
    // 搜索用户
    suspend fun searchUsers(keyword: String): List<User> {
        return client.get(V1.Users.Search(keyword = keyword)).body()
    }

    // 检查邮箱或用户名是否存在
    suspend fun checkExists(email: String? = null, username: String? = null): Boolean {
        val resp = client.get(V1.Users.Check(email = email, username = username)).body<Map<String, Boolean>>()
        return resp["exists"] ?: false
    }

    // 获取个人资料
    suspend fun getMe(): User {
        return client.get(V1.Users.Me()).body()
    }

    // 修改个人资料
    suspend fun updateProfile(req: UpdateProfileRequest): User {
        return client.patch(V1.Users.Me()) {
            setBody(req)
        }.body()
    }

    // 获取个人设置
    suspend fun getSettings(): UserSetting {
        return client.get(V1.Users.Me.Settings()).body()
    }

    // 修改个人设置
    suspend fun updateSettings(req: UpdateUserSettingsRequest): Boolean {
        return client.put(V1.Users.Me.Settings()) {
            setBody(req)
        }.status.value in 200..299
    }

    // 获取我的会话列表
    suspend fun getMyConversations(): List<ConversationResponse> {
        return client.get(V1.Users.Me.Conversations()).body()
    }

    // 获取所有入群申请
    suspend fun getGroupRequests(status: RequestStatus = RequestStatus.PENDING): List<GroupJoinRequest> {
        return client.get(V1.Users.Me.GroupRequests(status = status)).body()
    }

    // 获取收到的入群申请
    suspend fun getIncomingGroupRequests(status: RequestStatus = RequestStatus.PENDING): List<GroupJoinRequest> {
        val parent = V1.Users.Me.GroupRequests(status = status)
        return client.get(V1.Users.Me.GroupRequests.IncomingGroupRequests(parent = parent)).body()
    }

    // 获取发出的入群申请
    suspend fun getSentGroupRequests(status: RequestStatus = RequestStatus.PENDING): List<GroupJoinRequest> {
        val parent = V1.Users.Me.GroupRequests(status = status)
        return client.get(V1.Users.Me.GroupRequests.SentGroupRequests(parent = parent)).body()
    }

    // 获取他人资料
    suspend fun getUserById(id: Uuid): User {
        return client.get(V1.Users.Id(id = id)).body()
    }
}