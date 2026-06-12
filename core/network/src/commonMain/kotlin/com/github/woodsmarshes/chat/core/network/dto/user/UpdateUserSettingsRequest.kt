package com.github.woodsmarshes.chat.core.network.dto.user

import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.UserPreference
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class UpdateUserSettingsRequest(
    @ProtoNumber(1) val privacy: PrivacySetting? = null,
    @ProtoNumber(2) val preferences: UserPreference? = null
)