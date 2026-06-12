package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthToken(
    val jwtToken: String?,
    val refreshToken: String?,
    val expiryTimestamp: Long?,
)
