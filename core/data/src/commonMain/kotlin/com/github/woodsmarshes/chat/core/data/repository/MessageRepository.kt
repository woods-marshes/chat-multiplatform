package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.PagingData
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface MessageRepository {
    val connectionState: kotlinx.coroutines.flow.StateFlow<com.github.woodsmarshes.chat.core.model.ConnectionState>

    /**
     * Starts the session-bound message consumers and outbox workers. Idempotent
     * while the current session is already running; waits if a previous session
     * is still stopping before starting the new one.
     */
    suspend fun startSession()

    /**
     * Cancels all session-bound message workers (event consumption, reconnect
     * sync, periodic sweep, and in-flight gap repairs), waits for them to exit,
     * and then clears in-memory session state (seq high-water marks, gap repair
     * handles, and typing indicators). Idempotent.
     */
    suspend fun stopSession()

    /**
     * Resends outbox messages whose original send was never acknowledged.
     * Idempotent: a retry keeps the original request id, which the server
     * matches against `client_request_id` before inserting a new message.
     */
    suspend fun retryPendingMessages()

    /**
     * Re-sends one failed message under its original request identity.
     *
     * The record keeps its id and timestamp, so the retry neither adds a second
     * bubble nor reorders the timeline, and the server deduplicates it against
     * the first attempt instead of storing another message. The stored reply
     * target is reused, so a retried reply does not degrade to a plain message.
     */
    suspend fun retryMessage(messageId: Uuid): Result<Unit, MessageError>

//    val invalidationEvents: Flow<Unit>

    fun getMessages(
        ownUserId: Uuid,
        conversationId: Uuid,
        isGroup: Boolean,
        limit: Int = 20
    ): Flow<PagingData<MessageUiModel>>

    suspend fun sendMessage(
        conversationId: Uuid,
        content: MessageContent,
        replyToMessageId: Uuid? = null,
    ): Result<Unit, MessageError>

    /**
     * Asks the server to withdraw [messageId]. Failures are surfaced to the
     * caller (the UI decides how to report them) and are never retried
     * automatically — the user can revoke again once back online.
     */
    suspend fun revokeMessage(messageId: Uuid): Result<Unit, MessageError>

    suspend fun markAsRead(conversationId: Uuid, messageId: Uuid)

    /**
     * Who is currently typing in a conversation: userId -> last typing-event
     * timestamp (epoch millis). Entries do NOT expire on their own — pair
     * this flow with a ticker and treat entries older than a few seconds as
     * stopped, in case the matching "stopped typing" event was lost.
     */
    fun getTypingUsersFlow(conversationId: Uuid): Flow<Map<Uuid, Long>>

    /** Broadcasts the local user's typing state to conversation members. */
    suspend fun sendTyping(conversationId: Uuid, isTyping: Boolean)
}