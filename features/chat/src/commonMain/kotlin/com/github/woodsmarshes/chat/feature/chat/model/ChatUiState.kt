package com.github.woodsmarshes.chat.feature.chat.model

import androidx.compose.ui.text.input.TextFieldValue
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel

import kotlin.uuid.Uuid

data class ChatUiState(
    val input: TextFieldValue = TextFieldValue(),
    val isSending: Boolean = false,
    val error: String? = null,
    val isRecordingAudio: Boolean = false,
    val replyToMessage: MessageUiModel? = null,
    val ownUserId: Uuid? = null,
)
