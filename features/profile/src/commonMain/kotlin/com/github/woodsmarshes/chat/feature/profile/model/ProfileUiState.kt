package com.github.woodsmarshes.chat.feature.profile.model

data class ProfileUiState(
    val userId: String? = null,
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val email: String? = null,
    // True until the first emission arrives (cached row or refresh result).
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    // Refresh failed and nothing is cached to fall back to.
    val error: String? = null,
    // The navigation argument was not a valid UUID, or the server answered
    // UserError.NotFound (404) for a well-formed one.
    val notFound: Boolean = false,
    // One-shot error for actions (e.g. opening a chat failed).
    val actionError: String? = null,
    val isStartingChat: Boolean = false,
)
