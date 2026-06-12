package com.github.woodsmarshes.chat.core.network.dto.conversation

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
sealed interface CreateConversationRequest {
    val type: ConversationType
}

@Serializable
@SerialName("GROUP")
data class CreateGroupRequest(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val handle: String? = null,
    @ProtoNumber(3) val description: String? = null,
    @ProtoNumber(4) val avatar: String? = null,
    @ProtoNumber(5) val settings: GroupSettings? = null,
    @ProtoNumber(6) val memberIds: List<Uuid> = emptyList()
) : CreateConversationRequest {
    override val type: ConversationType = ConversationType.GROUP
}

@Serializable
@SerialName("PRIVATE")
data class CreatePrivateRequest(
    @ProtoNumber(1) val targetUserId: Uuid
) : CreateConversationRequest {
    override val type: ConversationType = ConversationType.PRIVATE
}
