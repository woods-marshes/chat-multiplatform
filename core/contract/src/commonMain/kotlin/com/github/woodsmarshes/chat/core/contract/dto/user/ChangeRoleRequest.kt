package com.github.woodsmarshes.chat.core.contract.dto.user

import com.github.woodsmarshes.chat.core.model.UserRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ChangeRoleRequest(
    @ProtoNumber(1) val newRole: UserRole,
)
