package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/**
 * Idempotent resend must be validated against the reply target that was
 * persisted, not against the reply target carried by the retry request.
 * A retry that reuses the same client request id but points at a different
 * message is a different message and must be rejected.
 */
class MessageRepositoryIdempotencyTest {

    private val database = connectToH2Database()
    private val repository = MessageDataSourceImpl()

    private val senderId = Uuid.random()
    private val conversationId = Uuid.random()
    private val now = kotlin.time.Clock.System.now()

    @BeforeTest
    fun seed() {
        runBlocking {
            transaction(database) {
                SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
                Users.insert {
                    it[id] = senderId
                    it[username] = "sender-$senderId"
                    it[email] = "sender-$senderId@test.local"
                    it[passwordHash] = "hash"
                    it[salt] = ""
                    it[role] = UserRole.MEMBER
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                Conversations.insert {
                    it[this.id] = conversationId
                    it[type] = ConversationType.PRIVATE
                    it[metadata] = PrivateMetadata()
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        runBlocking {
            transaction(database) {
                // Replies first: H2 enforces the self-referencing FK per row.
                Messages.deleteWhere {
                    (Messages.conversationId eq conversationId) and (Messages.replyToMessageId.isNotNull())
                }
                Messages.deleteWhere { Messages.conversationId eq conversationId }
                Conversations.deleteWhere { Conversations.id eq conversationId }
                Users.deleteWhere { Users.id eq senderId }
            }
        }
    }

    @Test
    fun resendingWithADifferentReplyTargetIsRejected() = runBlocking {
        val requestId = Uuid.random()
        val replyTarget = insertRootMessage()

        repository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = TextContent(text = "hello"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            requestId = requestId,
        )

        val failure = assertFailsWith<AppException> {
            repository.insertMessage(
                conversationId = conversationId,
                senderId = senderId,
                content = TextContent(text = "hello"),
                category = MessageCategory.NORMAL,
                renderType = MessageRenderType.TEXT,
                replyToMessageId = replyTarget.id,
                requestId = requestId,
            )
        }

        assertEquals(MessageError.InvalidContent, failure.error)
    }

    @Test
    fun resendingWithTheSamePayloadReturnsTheStoredMessage() = runBlocking {
        val requestId = Uuid.random()
        val replyTarget = insertRootMessage()

        val first = repository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = TextContent(text = "hello"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            replyToMessageId = replyTarget.id,
            requestId = requestId,
        )
        val second = repository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = TextContent(text = "hello"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            replyToMessageId = replyTarget.id,
            requestId = requestId,
        )

        assertEquals(first!!.first.id, second!!.first.id)
        assertEquals(replyTarget.id, second.first.replyTo?.id)
        assertFalse(second.second)
    }

    @Test
    fun resendingMediaWithADifferentUrlIsRejected() = runBlocking {
        val requestId = Uuid.random()

        repository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = uploadedFile("a.pdf"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.FILE,
            requestId = requestId,
        )

        val failure = assertFailsWith<AppException> {
            repository.insertMessage(
                conversationId = conversationId,
                senderId = senderId,
                content = uploadedFile("b.pdf"),
                category = MessageCategory.NORMAL,
                renderType = MessageRenderType.FILE,
                requestId = requestId,
            )
        }

        assertEquals(MessageError.InvalidContent, failure.error)
    }

    @Test
    fun resendingTheSameFileReturnsTheStoredMessage() = runBlocking {
        val requestId = Uuid.random()

        val first = repository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = uploadedFile("a.pdf"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.FILE,
            requestId = requestId,
        )
        val second = repository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = uploadedFile("a.pdf"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.FILE,
            requestId = requestId,
        )

        assertEquals(first!!.first.id, second!!.first.id)
        assertFalse(second.second)
    }

    private fun uploadedFile(name: String) = FileContent(
        url = "/v1/files/content/$name",
        fileName = name,
        mimeType = "application/pdf",
        size = 3,
    )

    private suspend fun insertRootMessage() = repository.insertMessage(
        conversationId = conversationId,
        senderId = senderId,
        content = TextContent(text = "root"),
        category = MessageCategory.NORMAL,
        renderType = MessageRenderType.TEXT,
    )!!.first
}
