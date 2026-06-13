package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.feature.conversations.model.ConversationsUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class ConversationsViewModel(
    private val conversationRepository: ConversationRepository,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
        refresh()
    }

    // ---------------- Conversation list ----------------

    private fun loadConversations() {
        viewModelScope.launch {
            conversationRepository.getConversationListFlow()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true) }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "加载失败",
                        isLoading = false,
                    )
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversations = conversations,
                        isLoading = false,
                    )
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                conversationRepository.syncConversations()
            } catch (_: Exception) {
                // silently ignore sync failures
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    // ---------------- FAB bottom sheet ----------------

    fun showActions() {
        _uiState.value = _uiState.value.copy(showActions = true)
    }

    fun dismissActions() {
        _uiState.value = _uiState.value.copy(showActions = false)
    }

    // ---------------- Create group ----------------

    fun showCreateGroup() {
        _uiState.value = _uiState.value.copy(
            showActions = false,
            showCreateGroup = true,
            groupName = "",
            groupDescription = "",
            createError = null,
        )
    }

    fun dismissCreateGroup() {
        _uiState.value = _uiState.value.copy(showCreateGroup = false)
    }

    fun onGroupNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun onGroupDescriptionChanged(desc: String) {
        _uiState.value = _uiState.value.copy(groupDescription = desc)
    }

    fun createGroup() {
        val name = _uiState.value.groupName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(createError = "群聊名称不能为空")
            return
        }

        _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
        viewModelScope.launch {
            conversationRepository.createGroup(
                name = name,
                description = _uiState.value.groupDescription.trim().ifEmpty { null },
            ).onErr {
                _uiState.value = _uiState.value.copy(isCreating = false, createError = "创建失败")
            }.onOk {
                _uiState.value = _uiState.value.copy(showCreateGroup = false, isCreating = false)
                refresh()
            }
        }
    }

    // ---------------- Join group ----------------

    fun showJoinGroup() {
        _uiState.value = _uiState.value.copy(
            showActions = false,
            showJoinGroup = true,
            joinGroupId = "",
            joinError = null,
        )
    }

    fun dismissJoinGroup() {
        _uiState.value = _uiState.value.copy(showJoinGroup = false)
    }

    fun onJoinGroupIdChanged(id: String) {
        _uiState.value = _uiState.value.copy(joinGroupId = id)
    }

    fun joinGroup() {
        val idStr = _uiState.value.joinGroupId.trim()
        val id = try {
            Uuid.parse(idStr)
        } catch (_: Exception) {
            log.info { "[ConversationsViewModel]: joinGroup(), not parse" }
            _uiState.value = _uiState.value.copy(joinError = "无效的群组 ID")
            return
        }

        _uiState.value = _uiState.value.copy(isJoining = true, joinError = null)
        viewModelScope.launch {
            conversationRepository.joinGroup(id).onErr {
                log.info { "[ConversationsViewModel]: joinGroup() => $it" }
                _uiState.value = _uiState.value.copy(isJoining = false, joinError = "加入失败，请检查 ID 是否正确")
            }.onOk {
                log.info { "[ConversationsViewModel]: joinGroup() => success" }
                _uiState.value = _uiState.value.copy(showJoinGroup = false, isJoining = false)
                refresh()
            }
        }
    }
}
