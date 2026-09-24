package com.github.woodsmarshes.chat.core.model.ui

import kotlin.uuid.Uuid

/**
 * Chat-header identity of a conversation: what the chat top bar shows and
 * where a tap on it should lead.
 *
 * @param title group name, or for private chats the peer's
 *   nickname/display name/username.
 * @param peerUserId private chats only: the other participant's user id,
 *   used to open their profile from the header.
 */
data class ConversationHeader(
    val conversationId: Uuid,
    val isGroup: Boolean,
    val title: String,
    val avatarUrl: String? = null,
    val peerUserId: Uuid? = null,
)
