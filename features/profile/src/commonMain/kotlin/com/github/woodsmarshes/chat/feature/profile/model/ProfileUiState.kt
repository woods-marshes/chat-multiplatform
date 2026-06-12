package com.github.woodsmarshes.chat.feature.profile.model

data class ProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val email: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
