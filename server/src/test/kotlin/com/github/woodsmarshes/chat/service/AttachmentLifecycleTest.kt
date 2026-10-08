package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class AttachmentLifecycleTest {

    private val messageRepository = mockk<MessageRepository>()
    private val fileService = mockk<FileService>()
    private val uploadStore = mockk<TemporaryUploadStore>()
    private val privateFileRepository = mockk<PrivateFileRepository>(relaxUnitFun = true)
    private val participantRepository = mockk<ConversationParticipantRepository>()

    private val lifecycle = AttachmentLifecycle(
        messageRepository = messageRepository,
        fileService = fileService,
        uploadStore = uploadStore,
        privateFileRepository = privateFileRepository,
        participantRepository = participantRepository,
    )

    private val senderId = Uuid.random()
    private val privateUrl = "/v1/files/content/report-${Uuid.random()}.pdf"
    private val privateFileName = privateUrl.substringAfterLast('/')
    private val content = FileContent(
        url = privateUrl, fileName = "report.pdf", mimeType = "application/pdf", size = 3,
    )

    @Test
    fun pendingUploadIsTrustedWhenPhysicalFileExists() = runBlocking {
        every { uploadStore.retrieveAndConfirm(privateUrl, senderId) } returns content
        every { fileService.resolvePrivateFile(privateFileName) } returns mockk<File>()

        assertEquals(content, lifecycle.resolveTrustedMedia(content, senderId))
        coVerify(exactly = 0) { privateFileRepository.getConversationsForFile(any()) }
        every { fileService.publicFileExists(any()) }
        verify(exactly = 0) { fileService.publicFileExists(any()) }
    }

    @Test
    fun confirmedPendingPrivateUploadIsRejectedAndInvalidatedWhenPhysicalFileIsMissing() = runBlocking {
        every { uploadStore.retrieveAndConfirm(privateUrl, senderId) } returns content
        every { fileService.resolvePrivateFile(privateFileName) } returns null
        justRun { uploadStore.invalidate(privateUrl) }

        assertNull(lifecycle.resolveTrustedMedia(content, senderId))
        verify(exactly = 1) { uploadStore.invalidate(privateUrl) }
    }

    @Test
    fun alreadySentPrivateFileRequiresSenderMembershipInReachedConversation() = runBlocking {
        val outsiderId = Uuid.random()
        val conversationId = Uuid.random()
        every { uploadStore.retrieveAndConfirm(privateUrl, any()) } returns null
        every { fileService.resolvePrivateFile(privateFileName) } returns mockk<File>()
        coEvery { privateFileRepository.getConversationsForFile(privateFileName) } returns listOf(conversationId)
        coEvery { participantRepository.getConversationParticipant(senderId, conversationId) } returns mockk()
        coEvery { participantRepository.getConversationParticipant(outsiderId, conversationId) } returns null

        assertEquals(content, lifecycle.resolveTrustedMedia(content, senderId))
        assertNull(lifecycle.resolveTrustedMedia(content, outsiderId))
    }

    @Test
    fun alreadySentPrivateFileIsRejectedWhenPhysicalFileIsMissing() = runBlocking {
        val conversationId = Uuid.random()
        every { uploadStore.retrieveAndConfirm(privateUrl, senderId) } returns null
        every { fileService.resolvePrivateFile(privateFileName) } returns null
        coEvery { privateFileRepository.getConversationsForFile(privateFileName) } returns listOf(conversationId)
        coEvery { participantRepository.getConversationParticipant(senderId, conversationId) } returns mockk()

        assertNull(lifecycle.resolveTrustedMedia(content, senderId))
        coVerify(exactly = 0) { privateFileRepository.getConversationsForFile(any()) }
    }

    @Test
    fun unsentPrivateFileIsRejected() = runBlocking {
        every { uploadStore.retrieveAndConfirm(privateUrl, senderId) } returns null
        every { fileService.resolvePrivateFile(privateFileName) } returns mockk<File>()
        coEvery { privateFileRepository.getConversationsForFile(privateFileName) } returns emptyList()

        assertNull(lifecycle.resolveTrustedMedia(content, senderId))
    }

    @Test
    fun existingPublicFileIsTrusted() = runBlocking {
        val publicUrl = "/uploads/image/pic-${Uuid.random()}.png"
        val media: MediaContent = ImageContent(
            url = publicUrl, fileName = "pic.png", width = 1, height = 1, size = 1,
        )
        every { uploadStore.retrieveAndConfirm(publicUrl, senderId) } returns null
        every { fileService.publicFileExists(publicUrl) } returns true

        assertEquals(media, lifecycle.resolveTrustedMedia(media, senderId))
    }

    @Test
    fun missingPublicFileIsRejected() = runBlocking {
        val publicUrl = "/uploads/image/gone-${Uuid.random()}.png"
        val media: MediaContent = ImageContent(
            url = publicUrl, fileName = "gone.png", width = 1, height = 1, size = 1,
        )
        every { uploadStore.retrieveAndConfirm(publicUrl, senderId) } returns null
        every { fileService.publicFileExists(publicUrl) } returns false

        assertNull(lifecycle.resolveTrustedMedia(media, senderId))
    }

    @Test
    fun unknownUrlSchemeIsRejected() = runBlocking {
        val media: MediaContent = FileContent(
            url = "https://cdn.example.com/evil.pdf", fileName = "evil.pdf", mimeType = null, size = 1,
        )
        every { uploadStore.retrieveAndConfirm(media.url, senderId) } returns null

        assertNull(lifecycle.resolveTrustedMedia(media, senderId))
    }

    @Test
    fun gcInvalidatesUploadStoreAndDeletesAttachmentWhoseLastLiveReferenceWasWithdrawn() = runBlocking {
        val message = message(content)
        coEvery { messageRepository.countLiveFileReferences(privateFileName) } returns 0L
        justRun { uploadStore.invalidate(privateUrl) }
        coJustRun { fileService.deletePrivateFile(privateFileName) }

        lifecycle.gcAfterWithdraw(message)

        verify(exactly = 1) { uploadStore.invalidate(privateUrl) }
        coVerify(exactly = 1) { fileService.deletePrivateFile(privateFileName) }
    }

    @Test
    fun gcKeepsAttachmentStillReferencedByOtherMessages() = runBlocking {
        val message = message(content)
        coEvery { messageRepository.countLiveFileReferences(privateFileName) } returns 2L

        lifecycle.gcAfterWithdraw(message)

        verify(exactly = 0) { uploadStore.invalidate(any()) }
        coVerify(exactly = 0) { fileService.deletePrivateFile(any()) }
    }

    @Test
    fun gcIgnoresNonFileMessages() = runBlocking {
        lifecycle.gcAfterWithdraw(message(TextContent("hello")))

        coVerify { messageRepository wasNot Called }
        coVerify { fileService wasNot Called }
    }

    @Test
    fun gcIgnoresPublicUploads() = runBlocking {
        val media: MediaContent = ImageContent(
            url = "/uploads/image/pic.png", fileName = "pic.png", width = 1, height = 1, size = 1,
        )
        lifecycle.gcAfterWithdraw(message(media))

        coVerify { messageRepository wasNot Called }
        coVerify { fileService wasNot Called }
    }

    private fun message(content: MessageContent) = Message(
        id = Uuid.random(),
        conversationId = Uuid.random(),
        category = MessageCategory.NORMAL,
        createdAt = kotlin.time.Clock.System.now(),
        content = content,
    )
}
