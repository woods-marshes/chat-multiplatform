package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn

import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.MessageRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.chat.model.ChatUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    conversationId: String,
    private val isGroup: Boolean,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val log = KotlinLogging.logger {}

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * Draft input as its own stream: keystrokes must not invalidate the
     * scopes reading [uiState] (the message list above all), so the input
     * bar collects this separately in its own recompose scope.
     */
    private val _input = MutableStateFlow(TextFieldValue())
    val input: StateFlow<TextFieldValue> = _input.asStateFlow()

    private val conversationUuid: Uuid? = runCatching { Uuid.parse(conversationId) }.getOrNull()

    private var isTypingActive = false
    private var lastTypingSentAt = 0L
    private var typingStopJob: Job? = null
    private var forwardJob: Job? = null

    /** Conversation picker data for the forward dialog. */
    val forwardTargets: Flow<List<ConversationUiModel>> = flow {
        emitAll(conversationRepository.getConversationListFlow())
    }

    init {
        viewModelScope.launch {
            userRepository.getMeFlow()
                .map { it?.id }
                .collectLatest { ownUserId ->
                    _uiState.value = _uiState.value.copy(ownUserId = ownUserId)
                }
        }

        val convId = conversationUuid
        if (convId != null) {
            // Chat header: live title/avatar from Room (group profile or peer user).
            viewModelScope.launch {
                conversationRepository.getConversationHeaderFlow(convId).collect { header ->
                    _uiState.value = _uiState.value.copy(header = header)
                }
            }

            // Members + typing indicator, combined with a 1s ticker so stale
            // typing entries (lost "stopped" event) age out on screen.
            // distinctUntilChanged keeps the ticker from re-emitting identical
            // state every second, which would churn every reader of uiState.
            viewModelScope.launch {
                combine(
                    conversationRepository.getGroupMembersFlow(convId),
                    messageRepository.getTypingUsersFlow(convId),
                    userRepository.getMeFlow(),
                    tickerFlow(),
                ) { members, typing, me, _ ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    // The server echoes typing events back to the sender's own
                    // sessions, so exclude the local user explicitly.
                    val activeTypingUsers = typing
                        .filterValues { now - it < TYPING_VISIBLE_MS }
                        .keys
                        .filterNot { it == me?.id }
                        .let { ids -> members.filter { it.id in ids } }
                    members to activeTypingUsers
                }
                    .map { (members, activeTypingUsers) ->
                        Triple(members, activeTypingUsers, members.size)
                    }
                    .distinctUntilChanged()
                    .collectLatest { (members, activeTypingUsers, count) ->
                        _uiState.value = _uiState.value.copy(
                            members = members,
                            typingUsers = activeTypingUsers,
                            memberCount = if (isGroup) count else null,
                        )
                    }
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
            conversationUuid?.let { convId ->
                messageRepository.getMessages(
                    ownUserId = uid,
                    conversationId = convId,
                    isGroup = isGroup,
                )
            } ?: flowOf(PagingData.empty())
        }
        .onEach {
            log.info { "[ChatVM-messages] got new PagingData from getMessages" }
        }
        .cachedIn(viewModelScope)

    fun onInputChanged(value: TextFieldValue) {
        _input.value = value
        onTextInputChanged(value.text)
    }

    fun insertEmoji(emoji: String) {
        val input = _input.value
        val newText = input.text.substring(0, input.selection.start) +
            emoji +
            input.text.substring(input.selection.end)
        val newCursor = input.selection.start + emoji.length
        _input.value = input.copy(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newCursor),
        )
    }

    fun sendMessage() {
        val text = _input.value.text
        if (text.isBlank()) return

        log.info { "[ChatVM] sendMessage triggered" }

        _uiState.value = _uiState.value.copy(isSending = true)

        viewModelScope.launch {
            val replyToId = _uiState.value.replyToMessage?.id
            val result = messageRepository.sendMessage(
                conversationId = conversationUuid ?: return@launch,
                content = TextContent(text),
                replyToMessageId = replyToId,
            )
            result.onOk {
                log.info { "[ChatVM] sendMessage result OK" }
                // Clear the input only after a confirmed send: on failure the
                // user keeps their draft instead of losing it.
                _input.value = TextFieldValue()
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = null,
                )
                clearReplyTo()
            }.onErr {
                log.warn { "[ChatVM] sendMessage result ERR: ${it.message}" }
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = strings.sendFailed,
                )
                clearReplyTo()
            }
            stopTyping(conversationUuid)
        }
    }

    fun setReplyTo(message: MessageUiModel) {
        _uiState.value = _uiState.value.copy(replyToMessage = message)
    }

    fun clearReplyTo() {
        _uiState.value = _uiState.value.copy(replyToMessage = null)
    }

    /**
     * Swipe-to-reply toggle: swiping the message that is already the reply
     * target cancels the reply instead of re-setting it.
     */
    fun toggleReplyTo(message: MessageUiModel) {
        if (_uiState.value.replyToMessage?.id == message.id) {
            clearReplyTo()
        } else {
            setReplyTo(message)
        }
    }

    // ---------------- Multi-select ----------------

    fun enterSelection(message: MessageUiModel) {
        _uiState.value = _uiState.value.copy(
            selectionMode = true,
            selectedMessages = listOf(message),
        )
    }

    fun toggleSelection(message: MessageUiModel) {
        if (!_uiState.value.selectionMode) {
            enterSelection(message)
            return
        }
        val current = _uiState.value.selectedMessages
        val updated = if (current.any { it.id == message.id }) {
            current.filterNot { it.id == message.id }
        } else {
            current + message
        }
        _uiState.value = _uiState.value.copy(
            selectionMode = updated.isNotEmpty(),
            selectedMessages = updated,
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectionMode = false,
            selectedMessages = emptyList(),
        )
    }

    // ---------------- Forward ----------------

    fun startForward(messages: List<MessageUiModel>) {
        if (messages.isEmpty()) return
        _uiState.value = _uiState.value.copy(forwardingMessages = messages)
    }

    fun dismissForward() {
        if (!_uiState.value.isForwarding) {
            _uiState.value = _uiState.value.copy(forwardingMessages = emptyList())
        }
    }

    /**
     * Resends the selected messages' content into the target conversation,
     * sequentially, preserving order.
     */
    fun forwardMessages(targetConversationId: Uuid) {
        val messages = _uiState.value.forwardingMessages
        if (messages.isEmpty()) return
        forwardJob?.cancel()
        forwardJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isForwarding = true)
            var failed = 0
            messages.forEach { message ->
                messageRepository.sendMessage(
                    conversationId = targetConversationId,
                    content = message.content,
                ).onErr { failed++ }
            }
            _uiState.value = _uiState.value.copy(
                isForwarding = false,
                forwardingMessages = emptyList(),
                error = if (failed > 0) strings.forwardFailed else null,
            )
        }
    }

    fun setReplyToAndClearSelection(message: MessageUiModel) {
        setReplyTo(message)
        clearSelection()
    }

    fun clearReplyToAndSelection() {
        clearReplyTo()
        clearSelection()
    }

    // ---------------- Typing indicator ----------------

    private fun onTextInputChanged(text: String) {
        val convId = conversationUuid ?: return
        typingStopJob?.cancel()

        if (text.isBlank()) {
            stopTyping(convId)
            return
        }
        if (!isTypingActive) {
            isTypingActive = true
            sendTyping(convId, true)
        } else {
            // Keep-alive so peers do not age the indicator out mid-typing.
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - lastTypingSentAt > TYPING_KEEPALIVE_MS) {
                sendTyping(convId, true)
            }
        }
        typingStopJob = viewModelScope.launch {
            delay(TYPING_IDLE_STOP_MS)
            stopTyping(convId)
        }
    }

    private fun sendTyping(conversationId: Uuid, isTyping: Boolean) {
        lastTypingSentAt = Clock.System.now().toEpochMilliseconds()
        viewModelScope.launch {
            messageRepository.sendTyping(conversationId, isTyping)
        }
    }

    private fun stopTyping(conversationId: Uuid?) {
        if (conversationId == null) return
        if (isTypingActive) {
            isTypingActive = false
            sendTyping(conversationId, false)
        }
    }

    private fun tickerFlow() = flow {
        while (true) {
            emit(Unit)
            delay(TYPING_TICK_MS)
        }
    }

    private companion object {
        /** How long a typing entry stays visible since its last event. */
        const val TYPING_VISIBLE_MS = 4_000L

        /** Re-announce typing at this interval while the user keeps typing. */
        const val TYPING_KEEPALIVE_MS = 2_500L

        /** Stop announcing typing after this idle time without keystrokes. */
        const val TYPING_IDLE_STOP_MS = 3_000L

        const val TYPING_TICK_MS = 1_000L
    }
}
