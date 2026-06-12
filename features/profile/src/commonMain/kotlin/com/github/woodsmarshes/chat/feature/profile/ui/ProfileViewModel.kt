package com.github.woodsmarshes.chat.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.feature.profile.model.ProfileUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class ProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        val userIdStr = savedStateHandle.get<String>("userId")
        if (userIdStr != null) {
            val userId = try { Uuid.parse(userIdStr) } catch (_: Exception) { null }
            if (userId != null) {
                loadProfile(userId)
            }
        }
    }

    private fun loadProfile(userId: Uuid) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = userRepository.fetchUserDetail(userId)
            result.onOk { user ->
                _uiState.value = _uiState.value.copy(
                    displayName = user.displayName ?: "",
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    bio = user.bio,
                    email = user.email,
                    isLoading = false,
                )
            }.onErr {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load profile")
            }
        }
    }
}
