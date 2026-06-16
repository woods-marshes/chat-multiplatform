package com.github.woodsmarshes.chat.core.network.api

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.RequestType
import io.ktor.resources.Resource
import kotlin.uuid.Uuid

@Resource("/v1")
class V1 {
    @Resource("/auth")
    class Auth(val parent: V1 = V1()) {
        @Resource("/register")
        class Register(val parent: Auth = Auth())

        @Resource("/login")
        class Login(val parent: Auth = Auth())

        @Resource("/refresh")
        class Refresh(val parent: Auth = Auth())
    }

    @Resource("/users")
    class Users(val parent: V1 = V1()) {
        // GET: 搜索用户 (USER_SEARCH)
        // GET /v1/users?keyword=abc
        @Resource("/search")
        class Search(val parent: Users = Users(), val keyword: String)

        // GET /v1/users/check?email=xxx&username=xxx (检查存在)
        @Resource("/check")
        class Check(val parent: Users = Users(), val email: String? = null, val username: String? = null)


        // GET /v1/users/{id}
        @Resource("/{id}")
        class Id(val parent: Users = Users(), val id: Uuid) {
            @Resource("/role")
            class Role(val parent: Id)
        }

        // GET /v1/users/me (获取自己资料)
        @Resource("/me")
        class Me(val parent: Users = Users()) {

            @Resource("/settings")
            class Settings(val parent: Me = Me())

            // 获取我的所有会话列表
            // GET /v1/users/me/conversations
            @Resource("/conversations")
            class Conversations(val parent: Me = Me())

            // 获取与我有关的加群申请
            // GET /v1/users/me/group-requests
            @Resource("/group-requests")
            class GroupRequests(
                val parent: Me = Me(),
                val status: RequestStatus? = RequestStatus.PENDING
            ) {
                // GET /v1/users/me/group-requests/incoming
                // 获取“我管理的群”收到的申请 (我是管理员/群主)
                @Resource("/incoming")
                class IncomingGroupRequests(
                    val parent: GroupRequests = GroupRequests()
                )

                // GET /v1/users/me/group-requests/sent
                // 获取“我发出的”加群申请 (我是申请人)
                @Resource("/sent")
                class SentGroupRequests(
                    val parent: GroupRequests = GroupRequests()
                )
            }
        }
    }

    @Resource("/conversations")
    class Conversations(val parent: V1 = V1()) {

        @Resource("/search")
        class Search(val parent: Conversations = Conversations(), val keyword: String)

        // GET /v1/conversations/check?handle=xxx
        @Resource("/check")
        class Check(val parent: Conversations = Conversations(), val handle: String)

        // GET /v1/conversations/{id}
        // DELETE /v1/conversations/{id}
        @Resource("/{id}")
        class Id(val parent: Conversations = Conversations(), val id: Uuid) {

            // POST /v1/conversations/{id}/invite-users (批量邀请)
            @Resource("/invite-users")
            class InviteUsers(val parent: Id)
            // POST /v1/conversations/{id}/invite (邀请)

            @Resource("/invite")
            class Invite(val parent: Id)

            // GET /v1/conversations/{id}/participants
            @Resource("/participants")
            class Participants(val parent: Id)

            // POST /v1/conversations/{id}/join (加入)
            @Resource("/join")
            class Join(val parent: Id)

            // POST /v1/conversations/{id}/leave
            @Resource("/leave")
            class Leave(val parent: Id)

            // PUT /v1/conversations/{id}/settings (更新设置/置顶/免打扰)
            @Resource("/settings")
            class Settings(val parent: Id) {
                // 群设置 (仅群组可用)
                @Resource("/group")
                class Group(val parent: Settings)

                // 个人设置 (个人会话设置，如置顶、免打扰等)
                @Resource("/personal")
                class Personal(val parent: Settings)
            }

            // GET /v1/conversations/{id}/messages?limit=20&before_seq=100
            @Resource("/messages")
            class Messages(
                val parent: Id,
                val limit: Int = 20,
                val beforeId: Uuid? = null, // 基于序列号分页
            ) {
                @Resource("/search")
                class Search(
                    val parent: Messages,
                    val keyword: String,
                )

                @Resource("/sync")
                class Sync(
                    val parent: Messages,
                    val afterId: Uuid,
                )
            }

            // 更新已读状态
            // POST /v1/conversations/{id}/read
            @Resource("/read")
            class Read(val parent: Id)

            // GET /v1/conversations/{id}/requests (获取该群的申请列表)
            // POST /v1/conversations/{id}/requests (处理申请: 审批/拒绝)
            @Resource("/requests")
            class Requests(
                val parent: Id,
                val status: RequestStatus? = RequestStatus.PENDING,
                val page: Int = 1,
                val limit: Int = 20
            )
        }
    }

    @Resource("/contacts")
    class Contacts(val parent: V1 = V1()) {
        // GET /v1/contacts (列表)
        // POST /v1/contacts (发送请求)
        // DELETE /v1/contacts/{id} (删除/拉黑)
        @Resource("/{id}")
        class Id(val parent: Contacts = Contacts(), val id: Uuid) {
            @Resource("/block")
            class Block(val parent: Id)

            @Resource("/unblock")
            class Unblock(val parent: Id)

            @Resource("/update")
            class Update(val parent: Id)
        }

        // GET /v1/contacts/requests (获取好友请求列表)
        @Resource("/requests")
        class Requests(
            val parent: Contacts = Contacts(),
            val type: RequestType? = null
        ) {

            // POST /v1/contacts/requests/{requestId}/action
            @Resource("/{contactRequestId}")
            class Id(val parent: Requests, val contactRequestId: Uuid)
        }
    }

    @Resource("/articles")
    class Articles(val parent: V1 = V1(), val offset: Long = 0, val limit: Int = 50) {
        @Resource("/{id}")
        class Id(val parent: Articles = Articles(), val id: Uuid)

        @Resource("/my")
        class My(val parent: Articles = Articles()) {
            @Resource("/{id}")
            class Id(val parent: My = My(), val id: Uuid)
        }
    }

    @Resource("/files")
    class Files(val parent: V1 = V1()) {
        @Resource("/upload")
        class Upload(
            val parent: Files = Files(),
            val type: FileType
        )
        // POST /v1/files/avatar?target=USER
        // POST /v1/files/avatar?target=GROUP&targetId=xxx
        @Resource("/avatar")
        class Avatar(
            val parent: Files = Files(),
            val isGroup: Boolean, // true=群头像, false=用户头像
            val targetId: Uuid? = null // 如果是 GROUP，则必填
        )
    }
}