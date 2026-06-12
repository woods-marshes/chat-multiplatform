package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class GroupProfile(
    @ProtoNumber(1) val conversationId: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val handle: String?,
    @ProtoNumber(4) val description: String?,
    @ProtoNumber(5) val avatarUrl: String?,
    @ProtoNumber(6) val ownerId: Uuid,
    @ProtoNumber(7) val settings: GroupSettings,
    @ProtoNumber(8) val createdAt: Instant,
    @ProtoNumber(9) val updatedAt: Instant,
)

@Serializable
data class GroupSettings(
    @ProtoNumber(1) val joinApprovalRequired: Boolean = false,
    @ProtoNumber(2) val allowMemberInvite: Boolean = true,
    @ProtoNumber(4) val allowBotJoin: Boolean = true,
    @ProtoNumber(5) val muteAll: Boolean = false,
)
