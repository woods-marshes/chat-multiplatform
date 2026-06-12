package com.github.woodsmarshes.chat.events

import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed class ContactEvent {
    /** 发送好友请求 */
    data class FriendRequestSent(
        val requestId: Uuid,
        val senderId: Uuid,
        val receiverId: Uuid,
        val timestamp: Instant
    ) : ContactEvent()

    /** 好友请求被接受 */
    data class FriendRequestAccepted(
        val requestId: Uuid,
        val senderId: Uuid,
        val receiverId: Uuid,
        val timestamp: Instant
    ) : ContactEvent()

    /** 好友请求被拒绝 */
    data class FriendRequestRejected(
        val requestId: Uuid,
        val senderId: Uuid,
        val receiverId: Uuid,
        val reason: String?,
        val timestamp: Instant
    ) : ContactEvent()

    /** 添加好友 */
    data class ContactAdded(
        val userId: Uuid,
        val contactId: Uuid,
        val timestamp: Instant
    ) : ContactEvent()

    /** 删除好友 */
    data class ContactDeleted(
        val userId: Uuid,
        val contactId: Uuid,
        val timestamp: Instant
    ) : ContactEvent()

    /** 拉黑用户 */
    data class UserBlocked(
        val userId: Uuid,
        val blockedUserId: Uuid,
        val timestamp: Instant
    ) : ContactEvent()

    /** 解除拉黑 */
    data class UserUnblocked(
        val userId: Uuid,
        val unblockedUserId: Uuid,
        val timestamp: Instant
    ) : ContactEvent()

    /** 更新联系人信息 */
    data class ContactUpdated(
        val userId: Uuid,
        val contactId: Uuid,
        val nickname: String?,
        val alias: String?,
        val timestamp: Instant
    ) : ContactEvent()
}
