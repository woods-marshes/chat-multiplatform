package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coJustRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class AttachmentLifecycleTest {

    private val messageRepository = mockk<MessageRepository>()
    private val fileService = mockk<FileService>()
    private val uploadStore = mockk<TemporaryUploadStore>()
    private val privateFileRepository = mockk<PrivateFileRepository>()

    private val lifecycle = AttachmentLifecycle(
        messageRepository = messageRepository,
        fileService = fileService,
        uploadStore = uploadStore,
        privateFileRepository = privateFileRepository,
    )

    private val privateUrl = "/v1/files/content/report-${Uuid.random()}.pdf"
    private val content = FileContent(
        url = privateUrl, fileName = "report.pdf", mimeType = "application/pdf", size = 3,
    )

    @Test
    fun pendingUploadIsTrustedWithoutFurtherChecks() = runBlocking {
        coEvery { uploadStore.retrieveAndConfirm(privateUrl) } returns content

        assertEquals(content, lifecycle.resolveTrustedMedia(content))
        coVerify(exactly = 0) { privateFileRepository.hasMapping(any()) }
        coVerify(exactly = 0) { fileService.publicFileExists(any()) }
    }

    @Test
    fun alreadySentPrivateFileIsTrustedViaItsMapping() = runBlocking {
        coEvery { uploadStore.retrieveAndConfirm(privateUrl) } returns null
        coEvery { privateFileRepository.hasMapping(privateUrl.substringAfterLast('/')) } returns true

        assertEquals(content, lifecycle.resolveTrustedMedia(content))
    }

    @Test
    fun unsentPrivateFileIsRejected() = runBlocking {
        coEvery { uploadStore.retrieveAndConfirm(privateUrl) } returns null
        coEvery { privateFileRepository.hasMapping(privateUrl.substringAfterLast('/')) } returns false

        assertNull(lifecycle.resolveTrustedMedia(content))
    }

    @Test
    fun existingPublicFileIsTrusted() = runBlocking {
        val publicUrl = "/uploads/image/pic-${Uuid.random()}.png"
        val media: MediaContent = ImageContent(
            url = publicUrl, fileName = "pic.png", width = 1, height = 1, size = 1,
        )
        coEvery { uploadStore.retrieveAndConfirm(publicUrl) } returns null
        coEvery { fileService.publicFileExists(publicUrl) } returns true

        assertEquals(media, lifecycle.resolveTrustedMedia(media))
    }

    @Test
    fun missingPublicFileIsRejected() = runBlocking {
        val publicUrl = "/uploads/image/gone-${Uuid.random()}.png"
        val media: MediaContent = ImageContent(
            url = publicUrl, fileName = "gone.png", width = 1, height = 1, size = 1,
        )
        coEvery { uploadStore.retrieveAndConfirm(publicUrl) } returns null
        coEvery { fileService.publicFileExists(publicUrl) } returns false

        assertNull(lifecycle.resolveTrustedMedia(media))
    }

    @Test
    fun unknownUrlSchemeIsRejected() = runBlocking {
        val media: MediaContent = FileContent(
            url = "https://cdn.example.com/evil.pdf", fileName = "evil.pdf", mimeType = null, size = 1,
        )
        coEvery { uploadStore.retrieveAndConfirm(media.url) } returns null

        assertNull(lifecycle.resolveTrustedMedia(media))
    }

    @Test
    fun gcDeletesAttachmentWhoseLastLiveReferenceWasWithdrawn() = runBlocking {
        val message = message(content)
        coEvery { messageRepository.countLiveFileReferences(privateUrl.substringAfterLast('/')) } returns 0L
        coJustRun { fileService.deletePrivateFile(privateUrl.substringAfterLast('/')) }

        lifecycle.gcAfterWithdraw(message)

        coVerify(exactly = 1) { fileService.deletePrivateFile(privateUrl.substringAfterLast('/')) }
    }

    @Test
    fun gcKeepsAttachmentStillReferencedByOtherMessages() = runBlocking {
        val message = message(content)
        coEvery { messageRepository.countLiveFileReferences(privateUrl.substringAfterLast('/')) } returns 2L

        lifecycle.gcAfterWithdraw(message)

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
