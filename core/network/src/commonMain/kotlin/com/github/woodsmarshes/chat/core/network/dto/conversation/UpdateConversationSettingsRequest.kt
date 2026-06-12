package com.github.woodsmarshes.chat.core.network.dto.conversation

import com.github.woodsmarshes.chat.core.model.GroupSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class UpdateConversationSettingsRequest(
    @ProtoNumber(1) val name: String? = null,
    @ProtoNumber(2) val handle: String? = null,
    @ProtoNumber(3) val description: String? = null,
    @ProtoNumber(4) val avatarUrl: String? = null,
    @ProtoNumber(5) val ownerId: Uuid? = null,
    @ProtoNumber(6) val settings: GroupSettings? = null,
)
