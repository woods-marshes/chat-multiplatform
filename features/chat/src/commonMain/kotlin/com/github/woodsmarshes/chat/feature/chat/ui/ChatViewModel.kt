package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn

import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.feature.chat.model.ChatUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    conversationId: String,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val log = KotlinLogging.logger {}
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationUuid: Uuid = Uuid.parse(conversationId)

    init {
        viewModelScope.launch {
            userRepository.getMeFlow()
                .map { it?.id }
                .collectLatest { ownUserId ->
                    _uiState.value = _uiState.value.copy(ownUserId = ownUserId)
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<MessageUiModel>> = userRepository.getMeFlow()
        .map { it?.id }
        .onEach { userId ->
            log.info { "[ChatVM-messages] getMeFlow emitted userId=$userId" }
        }
        .flatMapLatest { ownUserId ->
            val uid = ownUserId ?: Uuid.NIL
            log.info { "[ChatVM-messages] flatMapLatest calling getMessages(ownUserId=$uid)" }
            messageRepository.getMessages(
                ownUserId = uid,
                conversationId = conversationUuid,
                isGroup = false,
            )
        }
        .onEach {
            log.info { "[ChatVM-messages] got new PagingData from getMessages" }
        }
        .cachedIn(viewModelScope)

    fun onInputChanged(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun insertEmoji(emoji: String) {
        val input = _uiState.value.input
        val newText = input.text.substring(0, input.selection.start) +
            emoji +
            input.text.substring(input.selection.end)
        val newCursor = input.selection.start + emoji.length
        _uiState.value = _uiState.value.copy(
            input = input.copy(
                text = newText,
                selection = androidx.compose.ui.text.TextRange(newCursor),
            ),
        )
    }

    fun sendMessage() {
        val text = _uiState.value.input.text
        if (text.isBlank()) return

        log.info { "[ChatVM] sendMessage triggered, text='$text'" }

        _uiState.value = _uiState.value.copy(
            input = TextFieldValue(),
            isSending = true,
        )

        viewModelScope.launch {
            val replyToId = _uiState.value.replyToMessage?.id
            val result = messageRepository.sendMessage(
                conversationId = conversationUuid,
                content = TextContent(text),
                replyToMessageId = replyToId,
            )
            result.onOk {
                log.info { "[ChatVM] sendMessage result OK" }
                _uiState.value = _uiState.value.copy(isSending = false, replyToMessage = null)
            }.onErr {
                log.warn { "[ChatVM] sendMessage result ERR: ${it.message}" }
                _uiState.value = _uiState.value.copy(isSending = false, error = "Send failed")
            }
        }
    }

    fun setReplyTo(message: MessageUiModel) {
        _uiState.value = _uiState.value.copy(replyToMessage = message)
    }

    fun clearReplyTo() {
        _uiState.value = _uiState.value.copy(replyToMessage = null)
    }
}
