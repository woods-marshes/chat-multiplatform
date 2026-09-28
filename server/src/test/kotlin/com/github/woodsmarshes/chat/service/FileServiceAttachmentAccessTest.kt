package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FileServiceAttachmentAccessTest {

    private val uploadStore = mockk<TemporaryUploadStore>(relaxUnitFun = true)
    private val privateFileRepository = mockk<PrivateFileRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()

    private val service = FileService(
        uploadStore = uploadStore,
        privateFileRepository = privateFileRepository,
        participantRepository = participantRepository,
    )

    private val requesterId = Uuid.random()
    private val fileName = "${Uuid.random()}.pdf"
    private val storedFile = File("private-uploads/file", fileName)

    @BeforeTest
    fun seedStoredFile() {
        storedFile.parentFile.mkdirs()
        storedFile.writeBytes(byteArrayOf(1, 2, 3))
    }

    @AfterTest
    fun removeStoredFile() {
        storedFile.delete()
    }

    @Test
    fun memberOfAConversationTheFileReachedCanDownload() {
        val conversationId = Uuid.random()
        coEvery { privateFileRepository.getConversationsForFile(fileName) } returns listOf(conversationId)
        coEvery { participantRepository.getConversationParticipant(requesterId, conversationId) } returns mockk()

        runBlocking { assertNotNull(service.resolveAuthorizedPrivateFile(fileName, requesterId)) }
    }

    @Test
    fun nonMemberIsDeniedEvenWithTheExactFileName() {
        val conversationId = Uuid.random()
        coEvery { privateFileRepository.getConversationsForFile(fileName) } returns listOf(conversationId)
        coEvery { participantRepository.getConversationParticipant(requesterId, conversationId) } returns null

        runBlocking { assertNull(service.resolveAuthorizedPrivateFile(fileName, requesterId)) }
    }

    @Test
    fun fileThatWasNeverSentIsDenied() {
        coEvery { privateFileRepository.getConversationsForFile(fileName) } returns emptyList()

        runBlocking { assertNull(service.resolveAuthorizedPrivateFile(fileName, requesterId)) }
    }

    @Test
    fun publicFileExistsOnlyForRealFilesUnderUploads() {
        val present = File("uploads/image", "probe-${Uuid.random()}.png")
        present.parentFile.mkdirs()
        present.writeBytes(byteArrayOf(9))

        try {
            assertTrue(service.publicFileExists("/uploads/image/${present.name}"))
            assertFalse(service.publicFileExists("/uploads/image/missing-${Uuid.random()}.png"))
            assertFalse(service.publicFileExists("/uploads/../secrets.txt"))
            assertFalse(service.publicFileExists("/v1/files/content/${fileName}"))
        } finally {
            present.delete()
        }
    }
}
