package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.model.RequestType
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.contact.AddContactRequest
import com.github.woodsmarshes.chat.core.network.dto.contact.HandleContactRequest
import com.github.woodsmarshes.chat.core.network.dto.contact.UpdateContactRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.ContactService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.contactRoutes() {
    val contactService by inject<ContactService>()
    // 获取好友列表
    get<V1.Contacts> {
        val userId = call.extractUserId()
        val contacts = contactService.getContacts(userId).getOrThrow()
        call.respond(contacts)
    }

    // 发起好友请求
    post<V1.Contacts> {
        val userId = call.extractUserId()
        val req = call.receive<AddContactRequest>()
        contactService.sendFriendRequest(userId, req).getOrThrow()
        call.respond(mapOf("success" to true))
    }

    // 获取好友请求列表
    get<V1.Contacts.Requests> { params ->
        val userId = call.extractUserId()
        val requests = when(params.type) {
            RequestType.SENT -> contactService.getSentRequests(userId)
            RequestType.RECEIVED -> contactService.getReceivedRequests(userId)
            RequestType.ALL  -> contactService.getAllRequests(userId)
            null -> contactService.getAllRequests(userId)
        }.getOrThrow()
        call.respond(requests)
    }

    // 处理好友请求
    post<V1.Contacts.Requests.Id> { params ->
        val userId = call.extractUserId()
        val req = call.receive<HandleContactRequest>()
        contactService.handleFriendRequest(userId, params.contactRequestId, req.action, req.remark).getOrThrow()
        call.respond(mapOf("success" to true))
    }

    // 删除好友
    delete<V1.Contacts.Id> { params ->
        val userId = call.extractUserId()
        contactService.deleteContact(userId, params.id).getOrThrow()
        call.respond(mapOf("success" to true))
    }

    // 拉黑
    post<V1.Contacts.Id.Block> { params ->
        val userId = call.extractUserId()
        contactService.blockUser(userId, params.parent.id).getOrThrow()
        call.respond(mapOf("success" to true))
    }

    // 解除拉黑
    post<V1.Contacts.Id.Unblock> { params ->
        val userId = call.extractUserId()
        contactService.unblockUser(userId, params.parent.id).getOrThrow()
        call.respond(mapOf("success" to true))
    }

    // 更新联系人昵称和备注
    put<V1.Contacts.Id.Update> { params ->
        val userId = call.extractUserId()
        val req = call.receive<UpdateContactRequest>()
        contactService.updateContactInfo(userId, params.parent.id, req.nickname, req.alias).getOrThrow()
        call.respond(mapOf("success" to true))
    }
}