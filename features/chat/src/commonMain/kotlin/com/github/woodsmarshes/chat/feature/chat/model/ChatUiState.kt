package com.github.woodsmarshes.chat.feature.chat.model

import com.github.woodsmarshes.chat.core.model.ui.ConversationHeader
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser

import kotlin.uuid.Uuid

/**
 * Chat screen state. The draft input deliberately lives OUTSIDE this class
 * (see [ChatViewModel.input]): a keystroke must not invalidate every scope
 * reading this state, most importantly the message list.
 */
data class ChatUiState(
    val isSending: Boolean = false,
    val error: String? = null,
    val isRecordingAudio: Boolean = false,
    val replyToMessage: MessageUiModel? = null,
    val ownUserId: Uuid? = null,
    // Conversation top bar (Telegram-style) state.
    val header: ConversationHeader? = null,
    // Cached participant list; memberCount is derived for groups only.
    val members: List<SenderUser> = emptyList(),
    val memberCount: Int? = null,
    // Members currently typing; the header avatar swaps to the first one.
    val typingUsers: List<SenderUser> = emptyList(),
    // Multi-select mode (long-press / Ctrl+click entry).
    val selectionMode: Boolean = false,
    // Selected messages in selection order.
    val selectedMessages: List<MessageUiModel> = emptyList(),
    // Messages staged for the forward dialog.
    val forwardingMessages: List<MessageUiModel> = emptyList(),
    val isForwarding: Boolean = false,
)

