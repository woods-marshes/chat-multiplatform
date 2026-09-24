package com.github.woodsmarshes.chat.core.network.dto.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
sealed class ContactEventResponse : RealtimeEvent {

    @Serializable
    data class FriendRequestSent(
        @ProtoNumber(1) val requestId: Uuid,
        @ProtoNumber(2) val senderId: Uuid,
        @ProtoNumber(3) val receiverId: Uuid,
        @ProtoNumber(4) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class FriendRequestAccepted(
        @ProtoNumber(1) val requestId: Uuid,
        @ProtoNumber(2) val senderId: Uuid,
        @ProtoNumber(3) val receiverId: Uuid,
        @ProtoNumber(4) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class FriendRequestRejected(
        @ProtoNumber(1) val requestId: Uuid,
        @ProtoNumber(2) val senderId: Uuid,
        @ProtoNumber(3) val receiverId: Uuid,
        @ProtoNumber(4) val reason: String?,
        @ProtoNumber(5) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class ContactAdded(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val contactId: Uuid,
        @ProtoNumber(3) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class ContactDeleted(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val contactId: Uuid,
        @ProtoNumber(3) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class UserBlocked(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val blockedUserId: Uuid,
        @ProtoNumber(3) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class UserUnblocked(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val unblockedUserId: Uuid,
        @ProtoNumber(3) val timestamp: Instant
    ) : ContactEventResponse()

    @Serializable
    data class ContactUpdated(
        @ProtoNumber(1) val userId: Uuid,
        @ProtoNumber(2) val contactId: Uuid,
        @ProtoNumber(3) val nickname: String?,
        @ProtoNumber(4) val alias: String?,
        @ProtoNumber(5) val timestamp: Instant
    ) : ContactEventResponse()
}