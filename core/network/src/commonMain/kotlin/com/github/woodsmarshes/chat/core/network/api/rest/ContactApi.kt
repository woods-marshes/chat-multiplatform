package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.RequestType
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.contact.AddContactRequest
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.core.network.dto.contact.HandleContactRequest
import com.github.woodsmarshes.chat.core.network.dto.contact.UpdateContactRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

class ContactApi(private val client: HttpClient) {

    // 获取联系人列表
    suspend fun getContacts(): List<Pair<Contact, User>> {
        return client.get(V1.Contacts()).body()
    }

    // 发起好友请求
    suspend fun addContact(targetId: Uuid, message: String? = null): Boolean {
        return client.post(V1.Contacts()) {
            setBody(AddContactRequest(targetId, message))
        }.status.value == 200
    }

    // 获取好友请求列表 (SENT, RECEIVED, ALL)
    suspend fun getContactRequests(type: RequestType? = null): List<ContactRequest> {
        return client.get(V1.Contacts.Requests(type = type)).body()
    }

    // 处理好友请求 (同意/拒绝)
    suspend fun handleRequest(requestId: Uuid, action: ContactRequestAction, remark: String? = null): Boolean {
        val parent = V1.Contacts.Requests()
        return client.post(V1.Contacts.Requests.Id(parent, requestId)) {
            setBody(HandleContactRequest(action, remark))
        }.status.value == 200
    }

    // 删除好友
    suspend fun deleteContact(id: Uuid): Boolean {
        return client.delete(V1.Contacts.Id(id = id)).status.value == 200
    }

    // 拉黑用户
    suspend fun blockUser(id: Uuid): Boolean {
        val parent = V1.Contacts.Id(id = id)
        return client.post(V1.Contacts.Id.Block(parent)).status.value == 200
    }

    // 解除拉黑用户
    suspend fun unblockUser(id: Uuid): Boolean {
        val parent = V1.Contacts.Id(id = id)
        return client.post(V1.Contacts.Id.Unblock(parent)).status.value == 200
    }

    // 更新好友备注/昵称
    suspend fun updateContactInfo(id: Uuid, nickname: String?, alias: String?): Boolean {
        val parent = V1.Contacts.Id(id = id)
        return client.put(V1.Contacts.Id.Update(parent)) {
            setBody(UpdateContactRequest(nickname, alias))
        }.status.value == 200
    }
}