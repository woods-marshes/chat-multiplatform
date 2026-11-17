package com.github.woodsmarshes.chat.feature.chat.model

import com.github.woodsmarshes.chat.core.model.presentation.MessageUiModel

import kotlin.uuid.Uuid

/**
 * Chat screen state. The draft input deliberately lives OUTSIDE this class
 * (see [ChatViewModel.input]): a keystroke must not invalidate every scope
 * reading this state, most importantly the message list.
 *
 * The conversation top bar streams (header / typingUsers / memberCount) also
 * live OUTSIDE this class (see ChatViewModel.chatHeader / typingUsers /
 * memberCount): the 1s typing ticker must not invalidate every scope reading
 * this state either.
 */
data class ChatUiState(
    val isSending: Boolean = false,
    val error: String? = null,
    val isRecordingAudio: Boolean = false,
    val replyToMessage: MessageUiModel? = null,
    val ownUserId: Uuid? = null,
    // Multi-select mode (long-press / Ctrl+click entry).
    val selectionMode: Boolean = false,
    // Selected messages in selection order.
    val selectedMessages: List<MessageUiModel> = emptyList(),
    // Messages staged for the forward dialog.
    val forwardingMessages: List<MessageUiModel> = emptyList(),
    val isForwarding: Boolean = false,
)

