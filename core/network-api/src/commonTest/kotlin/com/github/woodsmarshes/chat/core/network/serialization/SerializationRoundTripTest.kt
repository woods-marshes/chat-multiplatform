package com.github.woodsmarshes.chat.core.network.serialization

import com.github.woodsmarshes.chat.core.model.FormattingEntity.Bold
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.JoinGroupContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageSenderContext
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.FormattingEntity.Spoiler
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Round-trip tests for the wire formats shared by client and server.
 * `ProjectJson` (REST) and `ProjectProtobuf` (WebSocket) must both survive
 * the polymorphic hierarchies [MessageContent], [ConversationMetadata] and
 * recursive `replyTo` chains without loss.
 */
class SerializationRoundTripTest {

    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    private fun user(id: String, name: String) = SimpleUser(
        id = Uuid.parse(id),
        username = name,
        displayName = name,
        avatarUrl = null,
        createdAt = t0,
        updatedAt = t0,
        deletedAt = null,
        role = UserRole.MEMBER,
    )

    private fun senderContext() = MessageSenderContext(
        conversationRole = com.github.woodsmarshes.chat.core.model.ConversationRole.MEMBER,
        joinedAt = t0,
        participantSettings = ParticipantSettings(nickname = "nick", bubbleColor = "#FFAA00"),
    )

    // ── JSON (ProjectJson) ──────────────────────────────────────────────

    @Test
    fun `text message round-trips through JSON`() {
        val message = textMessage()

        val decoded = ProjectJson.decodeFromString<Message>(ProjectJson.encodeToString(Message.serializer(), message))

        assertEquals(message, decoded)
    }

    @Test
    fun `text content uses the shared class discriminator`() {
        val json = ProjectJson.encodeToString(MessageContent.serializer(), TextContent("hi"))

        assertTrue(json.contains("\"type\":\"TEXT\""), "unexpected JSON: $json")
    }

    @Test
    fun `formatting entities round-trip through JSON with their SerialNames`() {
        val content: MessageContent = TextContent(
            text = "styled text",
            entities = listOf(Bold(offset = 0, length = 6), Spoiler(offset = 7, length = 4)),
        )

        val decoded = ProjectJson.decodeFromString<MessageContent>(ProjectJson.encodeToString(MessageContent.serializer(), content))

        assertEquals(content, decoded)
    }

    @Test
    fun `image and file media contents round-trip through JSON`() {
        val image: MessageContent = ImageContent(
            url = "https://example.com/pic.jpg",
            fileName = "pic.jpg",
            width = 1920,
            height = 1080,
            size = 123_456L,
            blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
            mimeType = "image/jpeg",
        )
        val file: MessageContent = FileContent(
            url = "https://example.com/doc.pdf",
            fileName = "doc.pdf",
            mimeType = null,
            size = 9L,
        )

        assertEquals(image, ProjectJson.decodeFromString(
            MessageContent.serializer(), ProjectJson.encodeToString(MessageContent.serializer(), image)
        ))
        assertEquals(file, ProjectJson.decodeFromString(
            MessageContent.serializer(), ProjectJson.encodeToString(MessageContent.serializer(), file)
        ))
    }

    @Test
    fun `unknown JSON keys are ignored on decode`() {
        val json = """{"type":"TEXT","text":"hi","entities":[],"futureField":"whatever"}"""

        val decoded = ProjectJson.decodeFromString(MessageContent.serializer(), json)

        assertEquals(TextContent("hi"), decoded)
    }

    @Test
    fun `reply chain round-trips recursively through JSON`() {
        val original = textMessage(senderName = "alice")
        val reply = Message(
            id = Uuid.random(),
            conversationId = original.conversationId,
            sender = user("018f0000-0000-7000-8000-000000000002", "bob"),
            category = MessageCategory.NORMAL,
            createdAt = t0,
            revokedAt = null,
            replyTo = original,
            content = TextContent("replying"),
            senderContext = senderContext(),
        )

        val decoded = ProjectJson.decodeFromString<Message>(ProjectJson.encodeToString(Message.serializer(), reply))

        assertEquals(reply, decoded)
        assertEquals(original, decoded.replyTo)
    }

    @Test
    fun `system content (join group) round-trips through JSON`() {
        val content: MessageContent = JoinGroupContent(
            userId = Uuid.random(),
            userName = "alice",
            inviterId = Uuid.random(),
            inviterName = "carol",
        )

        val decoded = ProjectJson.decodeFromString<MessageContent>(ProjectJson.encodeToString(MessageContent.serializer(), content))

        assertEquals(content, decoded)
    }

    @Test
    fun `conversation metadata polymorphism survives JSON with discriminator GROUP vs PRIVATE`() {
        val group: ConversationMetadata = GroupMetadata(announcement = listOf("welcome"))
        val private: ConversationMetadata = PrivateMetadata(encryptionKey = "key")

        assertEquals(group, ProjectJson.decodeFromString(
            ConversationMetadata.serializer(), ProjectJson.encodeToString(ConversationMetadata.serializer(), group)
        ))
        assertEquals(private, ProjectJson.decodeFromString(
            ConversationMetadata.serializer(), ProjectJson.encodeToString(ConversationMetadata.serializer(), private)
        ))

        assertTrue(ProjectJson.encodeToString(ConversationMetadata.serializer(), group).contains("\"type\":\"GROUP\""))
        assertTrue(ProjectJson.encodeToString(ConversationMetadata.serializer(), private).contains("\"type\":\"PRIVATE\""))
    }

    // ── Protobuf (ProjectProtobuf, WebSocket wire format) ───────────────

    @Test
    fun `text message round-trips through protobuf`() {
        val message = textMessage()

        val bytes = ProjectProtobuf.encodeToByteArray(Message.serializer(), message)
        val decoded = ProjectProtobuf.decodeFromByteArray(Message.serializer(), bytes)

        assertEquals(message, decoded)
    }

    @Test
    fun `reply chain and sender context round-trip through protobuf`() {
        val original = textMessage(senderName = "alice")
        val reply = Message(
            id = Uuid.random(),
            conversationId = original.conversationId,
            sender = user("018f0000-0000-7000-8000-000000000003", "bob"),
            category = MessageCategory.NORMAL,
            createdAt = t0,
            revokedAt = t0,
            replyTo = original,
            content = TextContent("reply with entities", entities = listOf(Bold(0, 5))),
            senderContext = senderContext(),
        )

        val decoded = ProjectProtobuf.decodeFromByteArray(
            Message.serializer(),
            ProjectProtobuf.encodeToByteArray(Message.serializer(), reply),
        )

        assertEquals(reply, decoded)
    }

    @Test
    fun `conversation metadata round-trips through protobuf`() {
        val group: ConversationMetadata = GroupMetadata(announcement = listOf("a", "b"))

        val decoded = ProjectProtobuf.decodeFromByteArray(
            ConversationMetadata.serializer(),
            ProjectProtobuf.encodeToByteArray(ConversationMetadata.serializer(), group),
        )

        assertEquals(group, decoded)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun textMessage(senderName: String = "alice") = Message(
        id = Uuid.random(),
        conversationId = Uuid.random(),
        sender = user("018f0000-0000-7000-8000-000000000001", senderName),
        category = MessageCategory.NORMAL,
        createdAt = t0,
        revokedAt = null,
        replyTo = null,
        content = TextContent("hello"),
        senderContext = senderContext(),
    )
}
