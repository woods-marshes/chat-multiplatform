package com.github.woodsmarshes.chat.events

import com.github.woodsmarshes.chat.core.model.Message
import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed class MessageEvent {
    /** 发送消息 */
    data class SendMessage(
        val message: Message,
        val conversationId: Uuid,
        val senderId: Uuid,
        val timestamp: Instant,
        val requestId: String,
    ) : MessageEvent()

    /** 撤回消息 */
    data class WithdrawMessage(
        val messageId: Uuid,
        val conversationId: Uuid,
        val senderId: Uuid,
        val timestamp: Instant,
    ) : MessageEvent()

    /** 消息已读 */
    data class ReadMessage(
        val messageId: Uuid,
        val conversationId: Uuid,
        val readerId: Uuid,
        val timestamp: Instant,
    ) : MessageEvent()

    /** 用户输入状态 */
    data class UserTyping(
        val conversationId: Uuid,
        val userId: Uuid,
        val isTyping: Boolean,
        val timestamp: Instant
    ) : MessageEvent()
}
