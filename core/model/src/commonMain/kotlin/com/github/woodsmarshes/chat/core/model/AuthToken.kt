package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthToken(
    val jwtToken: String?,
    val refreshToken: String?,
    val expiryTimestamp: Long?,
)

/**
 * Unified snapshot of the persisted authentication session mapped from a
 * single DataStore emission so observers never combine mismatched token and
 * user versions across independent flows.
 */
data class AuthSessionSnapshot(
    val generation: Long = 0L,
    val user: User? = null,
    val jwtToken: String? = null,
    val refreshToken: String? = null,
    val expiryTimestamp: Long? = null,
) {
    val isLoggedIn: Boolean
        get() = !jwtToken.isNullOrEmpty() && user != null
}
