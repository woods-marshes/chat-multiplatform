package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageSenderContext
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.network.dto.conversation.GroupInfo
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.network.dto.conversation.SimpleMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for the pure DTO / domain / DB-entity mappers in `core.data.model`.
 * These mappers feed the offline-first repositories, so their edge cases
 * (missing sender, missing context, null replies) are load-bearing.
 */
class ModelMapperTest {

    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    private fun sender(name: String = "alice") = SimpleUser(
        id = Uuid.parse("018f0000-0000-7000-8000-000000000001"),
        username = name,
        displayName = "Alice",
        avatarUrl = "https://example.com/a.png",
        createdAt = t0,
        updatedAt = t0,
        deletedAt = null,
        role = UserRole.MEMBER,
    )

    private fun message(
        sender: SimpleUser? = sender(),
        content: com.github.woodsmarshes.chat.core.model.MessageContent = TextContent("hello"),
        replyTo: Message? = null,
        senderContext: MessageSenderContext? = MessageSenderContext(
            conversationRole = ConversationRole.MEMBER,
            joinedAt = t0,
            participantSettings = ParticipantSettings(nickname = "nick", bubbleColor = "#123456"),
        ),
    ) = Message(
        id = Uuid.parse("018f0000-0000-7000-8000-000000000100"),
        conversationId = Uuid.parse("018f0000-0000-7000-8000-000000000200"),
        sender = sender,
        category = MessageCategory.NORMAL,
        createdAt = t0,
        revokedAt = null,
        replyTo = replyTo,
        content = content,
        senderContext = senderContext,
    )

    // ── MessageStatus.toUiState ─────────────────────────────────────────

    @Test
    fun `null local status maps to Completed`() {
        assertEquals(MessageState.Completed, (null as MessageStatus?).toUiState())
    }

    @Test
    fun `every MessageStatus maps to its UI state`() {
        assertEquals(MessageState.Sending, MessageStatus.SENDING.toUiState())
        assertEquals(MessageState.Completed, MessageStatus.SENT.toUiState())
        val failed = MessageStatus.FAILED.toUiState()
        assertTrue(failed is MessageState.SendFailed)
        assertTrue(failed.reason.isNotBlank())
    }

    // ── Message.toMessageEntity ─────────────────────────────────────────

    @Test
    fun `message maps to a DB entity with all identifiers preserved`() {
        val entity = message().toMessageEntity(localStatus = MessageStatus.SENDING)

        assertEquals(
            Uuid.parse("018f0000-0000-7000-8000-000000000100"),
            entity.id,
        )
        assertEquals(
            Uuid.parse("018f0000-0000-7000-8000-000000000200"),
            entity.conversation_id,
        )
        assertEquals(sender().id, entity.user_id)
        assertEquals(MessageStatus.SENDING, entity.local_send_status)
        assertNull(entity.reply_to_message_id)
        assertEquals(t0, entity.created_at)
    }

    @Test
    fun `render type is derived from the content type`() {
        assertEquals(
            com.github.woodsmarshes.chat.core.model.MessageRenderType.TEXT,
            message(content = TextContent("t")).toMessageEntity().render_type,
        )
        assertEquals(
            com.github.woodsmarshes.chat.core.model.MessageRenderType.IMAGE,
            message(
                content = ImageContent(
                    url = "u", fileName = "f", width = 1, height = 1, size = 1L, mimeType = "image/png",
                )
            ).toMessageEntity().render_type,
        )
        assertEquals(
            com.github.woodsmarshes.chat.core.model.MessageRenderType.AUDIO,
            message(
                content = AudioContent(url = "u", fileName = "f", duration = 1L, size = 1L)
            ).toMessageEntity().render_type,
        )
    }

    @Test
    fun `message without a sender is rejected`() {
        val senderless = message(sender = null)

        try {
            senderless.toMessageEntity()
            fail("expected IllegalStateException for missing sender")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Sender is required"))
        }
    }

    @Test
    fun `reply target id is stored on the entity`() {
        val original = message()
        val reply = message(replyTo = original)

        assertEquals(original.id, reply.toMessageEntity().reply_to_message_id)
    }

    @Test
    fun `toReplyMessageEntity maps this message's replyTo into an entity`() {
        // The mapper itself is correct (maps this.replyTo); the KNOWN ISSUE is
        // at the call site: MessageRemoteMediator.kt passes `message.replyTo`
        // into this helper, which stores the GRANDPARENT and loses the direct
        // reply target. See review finding on MessageRemoteMediator.
        val original = message()
        val reply = message(replyTo = original)

        val entity = reply.toReplyMessageEntity()

        assertNotNull(entity)
        assertEquals(original.id, entity.id)
        assertNull(entity.reply_to_message_id)
    }

    // ── Message.toUserEntity ────────────────────────────────────────────

    @Test
    fun `sender maps to a cached user entity without email or bio`() {
        val entity = message().toUserEntity()

        assertNotNull(entity)
        assertEquals(sender().id, entity.id)
        assertEquals("alice", entity.username)
        assertNull(entity.email)
        assertNull(entity.bio)
        assertEquals("Alice", entity.display_name)
    }

    @Test
    fun `user entity is null when the message has no sender`() {
        assertNull(message(sender = null).toUserEntity())
    }

    // ── Message.toParticipantEntity ─────────────────────────────────────

    @Test
    fun `sender context maps to a participant entity with settings`() {
        val entity = message().toParticipantEntity()

        assertNotNull(entity)
        assertEquals(sender().id, entity.user_id)
        assertEquals(ConversationRole.MEMBER, entity.role)
        assertEquals("nick", entity.settings.nickname)
        assertEquals("#123456", entity.settings.bubbleColor)
    }

    @Test
    fun `participant entity falls back to default settings when context has none`() {
        val contextlessSettings = MessageSenderContext(
            conversationRole = ConversationRole.ADMIN,
            joinedAt = t0,
            participantSettings = null,
        )

        val entity = message(senderContext = contextlessSettings).toParticipantEntity()

        assertNotNull(entity)
        assertEquals(ConversationRole.ADMIN, entity.role)
        assertEquals(ParticipantSettings(), entity.settings)
    }

    @Test
    fun `participant entity is null when the message has no sender context`() {
        assertNull(message(senderContext = null).toParticipantEntity())
    }

    @Test
    fun `participant entity is null when the message has no sender`() {
        assertNull(message(sender = null).toParticipantEntity())
    }

    // ── ConversationResponse.toMessageEntity ────────────────────────────

    @Test
    fun `conversation response without a last message maps to null entity`() {
        val response = conversationResponse(lastMessage = null)

        assertNull(response.toMessageEntity())
    }

    @Test
    fun `conversation response maps its last message into a DB entity`() {
        val response = conversationResponse()

        val entity = response.toMessageEntity()

        assertNotNull(entity)
        assertEquals(response.conversationId, entity.conversation_id)
        assertEquals(MessageCategory.NORMAL, entity.category)
        assertNull(entity.local_send_status)
    }

    @Test
    fun `conversation response last message without a sender is rejected`() {
        val response = conversationResponse(sender = null)

        try {
            response.toMessageEntity()
            fail("expected IllegalStateException for missing sender")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Sender is required"))
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun conversationResponse(
        sender: SimpleUser? = sender(),
        lastMessage: SimpleMessage? = SimpleMessage(
            id = Uuid.parse("018f0000-0000-7000-8000-000000000300"),
            sender = sender,
            category = MessageCategory.NORMAL,
            createdAt = t0,
            revokedAt = null,
            content = TextContent("last message"),
            senderContext = null,
        ),
    ) = com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse(
        conversationId = Uuid.parse("018f0000-0000-7000-8000-000000000200"),
        type = com.github.woodsmarshes.chat.core.model.ConversationType.GROUP,
        lastMessage = lastMessage,
        metadata = null,
        participant = com.github.woodsmarshes.chat.core.model.ConversationParticipant(
            conversationId = Uuid.parse("018f0000-0000-7000-8000-000000000200"),
            userId = sender().id,
            role = ConversationRole.MEMBER,
            lastReadMessageId = null,
            joinedAt = t0,
            mutedUntil = null,
            settings = ParticipantSettings(),
        ),
        conversationInfo = GroupInfo(
            name = "group",
            handle = null,
            description = null,
            avatarUrl = null,
            ownerId = sender().id,
            settings = GroupSettings(),
            createdAt = t0,
            updatedAt = t0,
            deletedAt = null,
            userInfo = com.github.woodsmarshes.chat.core.network.dto.conversation.UserInfo(
                id = sender().id,
                username = "alice",
                email = null,
                displayName = "Alice",
                avatarUrl = null,
                bio = null,
                createdAt = t0,
                updatedAt = t0,
                role = UserRole.MEMBER,
                deletedAt = null,
            ),
        ),
    )
}
