package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.PrivateFiles
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Persists messages carrying a private attachment and asserts that the
 * (file_name, conversation_id) download-authorization mapping is recorded in
 * the same transaction — including one file forwarded into two conversations.
 */
class MessageRepositoryAttachmentMappingTest {

    private val database = connectToH2Database()
    private val repository = MessageDataSourceImpl()

    private val senderId = Uuid.random()
    private val conversationA = Uuid.random()
    private val conversationB = Uuid.random()
    private val now = kotlin.time.Clock.System.now()

    @BeforeTest
    fun seed() {
        runBlocking {
            transaction(database) {
                SchemaUtils.create(Users, Conversations, Messages, PrivateFiles)
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
                listOf(conversationA, conversationB).forEach { id ->
                    Conversations.insert {
                        it[this.id] = id
                        it[type] = ConversationType.PRIVATE
                        it[metadata] = PrivateMetadata()
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        runBlocking {
            transaction(database) {
                PrivateFiles.deleteWhere { PrivateFiles.conversationId inList listOf(conversationA, conversationB) }
                Messages.deleteWhere { Messages.conversationId inList listOf(conversationA, conversationB) }
                Conversations.deleteWhere { Conversations.id inList listOf(conversationA, conversationB) }
                Users.deleteWhere { Users.id eq senderId }
            }
        }
    }

    private fun fileContent(url: String): MessageContent =
        FileContent(url = url, fileName = "report.pdf", mimeType = "application/pdf", size = 3)

    private fun mappingsFor(fileName: String): List<Uuid> = transaction(database) {
        PrivateFiles.selectAll()
            .where { PrivateFiles.fileName eq fileName }
            .map { it[PrivateFiles.conversationId].value }
    }

    @Test
    fun sendingAFileMessageRecordsTheDownloadMapping() {
        runBlocking {
        val url = "/v1/files/content/report-${Uuid.random()}.pdf"

        val inserted = repository.insertMessage(
            conversationId = conversationA,
            senderId = senderId,
            content = fileContent(url),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.FILE,
        )
        kotlin.test.assertNotNull(inserted)

        assertEquals(listOf(conversationA), mappingsFor(url.substringAfterLast('/')))
        }
    }

    @Test
    fun forwardingTheSameFileIntoAnotherConversationAddsARow() {
        runBlocking {
        val url = "/v1/files/content/forwarded-${Uuid.random()}.pdf"
        val fileName = url.substringAfterLast('/')

        repository.insertMessage(
            conversationId = conversationA, senderId = senderId,
            content = fileContent(url), category = MessageCategory.NORMAL, renderType = MessageRenderType.FILE,
        )
        repository.insertMessage(
            conversationId = conversationB, senderId = senderId,
            content = fileContent(url), category = MessageCategory.NORMAL, renderType = MessageRenderType.FILE,
        )

        assertEquals(setOf(conversationA, conversationB), mappingsFor(fileName).toSet())
        }
    }

    @Test
    fun textMessagesRecordNoMapping() {
        runBlocking {
        repository.insertMessage(
            conversationId = conversationA,
            senderId = senderId,
            content = TextContent(text = "hello"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
        )

        assertEquals(0, transaction(database) { PrivateFiles.selectAll().count() }.toInt())
        }
    }
}
