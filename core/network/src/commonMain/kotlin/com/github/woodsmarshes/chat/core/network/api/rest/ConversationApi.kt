package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateConversationRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.HandleGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.InviteUserRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.InviteUsersRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.JoinGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.core.network.dto.message.ReadReportRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

class ConversationApi(
    private val client: HttpClient
) {
    // 搜索群组
    suspend fun searchGroups(keyword: String): List<GroupProfile> {
        return client.get(V1.Conversations.Search(keyword = keyword)).body()
    }

    // 检查群组handle是否存在
    suspend fun checkExists(handle: String): Boolean {
        val resp = client.get(V1.Conversations.Check(handle = handle)).body<Map<String, Boolean>>()
        return resp["exists"] ?: false
    }

    // 创建会话 (单聊/群聊)
    suspend fun createConversation(req: CreateConversationRequest): Conversation {
        return client.post(V1.Conversations()) {
            setBody(req)
        }.body()
    }

    // 获取会话详情
    suspend fun getDetail(id: Uuid): ConversationResponse {
        return client.get(V1.Conversations.Id(id = id)).body()
    }

    // 解散或删除会话
    suspend fun deleteConversation(id: Uuid): Boolean {
        return client.delete(V1.Conversations.Id(id = id)).status.value == 200
    }

    // 获取成员列表
    suspend fun getParticipants(id: Uuid): List<Pair<ConversationParticipant, User>> {
        val parent = V1.Conversations.Id(id = id)
        return client.get(V1.Conversations.Id.Participants(parent)).body()
    }

    // 批量邀请用户入群
    suspend fun inviteUsers(conversationId: Uuid, userIds: List<Uuid>): Boolean {
        val parent = V1.Conversations.Id(id = conversationId)
        return client.post(V1.Conversations.Id.InviteUsers(parent)) {
            setBody(InviteUsersRequest(userIds))
        }.status.value == 200
    }

    // 邀请单个用户入群
    suspend fun inviteUser(conversationId: Uuid, userId: Uuid): Boolean {
        return inviteUsers(conversationId, listOf(userId))
    }

    // 主动申请加入群组
    suspend fun joinGroup(conversationId: Uuid, message: String? = null): Boolean {
        val parent = V1.Conversations.Id(id = conversationId)
        return client.post(V1.Conversations.Id.Join(parent)) {
            setBody(JoinGroupRequest(message))
        }.status.value == 200
    }

    // 退出群组
    suspend fun leaveGroup(conversationId: Uuid, message: String? = null): Boolean {
        val parent = V1.Conversations.Id(id = conversationId)
        return client.post(V1.Conversations.Id.Leave(parent))
            .status.value == 200
    }

    // 更新群组设置 (仅Owner)
    suspend fun updateGroupSettings(conversationId: Uuid, req: UpdateConversationSettingsRequest): Boolean {
        val idResource = V1.Conversations.Id(id = conversationId)
        val settingsResource = V1.Conversations.Id.Settings(idResource)
        return client.put(V1.Conversations.Id.Settings.Group(settingsResource)) {
            setBody(req)
        }.status.value == 200
    }

    // 更新个人在该会话的设置 (置顶/免打扰/昵称)
    suspend fun updatePersonalSettings(conversationId: Uuid, req: ParticipantSettings): Boolean {
        val idResource = V1.Conversations.Id(id = conversationId)
        val settingsResource = V1.Conversations.Id.Settings(idResource)
        return client.put(V1.Conversations.Id.Settings.Personal(settingsResource)) {
            setBody(req)
        }.status.value == 200
    }

    // 获取历史消息
    suspend fun getMessages(
        conversationId: Uuid,
        limit: Int = 20,
        beforeId: Uuid? = null
    ): List<Message> {
        val parent = V1.Conversations.Id(id = conversationId)
        return client.get(V1.Conversations.Id.Messages(parent, limit, beforeId)).body()
    }

    // 搜索消息
    suspend fun searchMessages(
        conversationId: Uuid,
        keyword: String,
        limit: Int = 20
    ): List<Message> {
        val idRes = V1.Conversations.Id(id = conversationId)
        val msgRes = V1.Conversations.Id.Messages(parent = idRes, limit = limit)
        val searchRes = V1.Conversations.Id.Messages.Search(parent = msgRes, keyword = keyword)

        return client.get(searchRes).body()
    }

    // 增量同步消息
    suspend fun syncMessages(
        conversationId: Uuid,
        afterId: Uuid,
        limit: Int = 50
    ): List<Message> {
        val idRes = V1.Conversations.Id(id = conversationId)
        val msgRes = V1.Conversations.Id.Messages(parent = idRes, limit = limit)
        val syncRes = V1.Conversations.Id.Messages.Sync(parent = msgRes, afterId = afterId)

        return client.get(syncRes).body()
    }

    // 上报已读
    suspend fun markAsRead(conversationId: Uuid, messageId: Uuid): Boolean {
        val idRes = V1.Conversations.Id(id = conversationId)
        val readRes = V1.Conversations.Id.Read(parent = idRes)

        val response = client.post(readRes) {
            setBody(ReadReportRequest(messageId = messageId))
        }
        return response.status.value in 200..299
    }

    // 获取该群的申请列表 (管理员视角)
    suspend fun getGroupJoinRequests(
        conversationId: Uuid,
        status: RequestStatus = RequestStatus.PENDING
    ): List<GroupJoinRequest> {
        val idRes = V1.Conversations.Id(id = conversationId)
        val requestsRes = V1.Conversations.Id.Requests(parent = idRes, status = status)

        return client.get(requestsRes).body()
    }

    // 处理群申请 (审批/拒绝)
    suspend fun handleGroupRequests(
        conversationId: Uuid,
        requests: List<HandleGroupRequest>
    ): Boolean {
        val idRes = V1.Conversations.Id(id = conversationId)
        val requestsRes = V1.Conversations.Id.Requests(parent = idRes)

        val response = client.post(requestsRes) {
            setBody(requests)
        }
        return response.status.value in 200..299
    }
}