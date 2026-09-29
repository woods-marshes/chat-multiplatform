package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileServiceUploadTest {

    private val uploadStore = mockk<TemporaryUploadStore>(relaxUnitFun = true)
    private val privateFileRepository = mockk<PrivateFileRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()

    private val service = FileService(
        uploadStore = uploadStore,
        privateFileRepository = privateFileRepository,
        participantRepository = participantRepository,
    )

    private val stored = mutableListOf<File>()

    @BeforeTest
    fun ensureDirectories() {
        service.ensureUploadDirectories()
    }

    @AfterTest
    fun cleanStoredFiles() {
        stored.forEach { it.delete() }
    }

    @Test
    fun sanitizeFileNameRejectsTraversalAndEmptyNames() {
        assertNull(service.sanitizeFileName(".."))
        assertNull(service.sanitizeFileName("a/b"))
        assertNull(service.sanitizeFileName("a\\b"))
        assertNull(service.sanitizeFileName(""))
        assertNull(service.sanitizeFileName("."))
        assertEquals("report.pdf", service.sanitizeFileName("report.pdf"))
    }

    @Test
    fun uploadRejectsBlockedExtensions() = runBlocking {
        val before = File("private-uploads/file").listFiles()?.size ?: 0

        val html = service.uploadFile(FileType.FILE, "payload.html", byteArrayOf(1), "text/html")
        assertEquals(FileError.UnsupportedFormat, html.getError())

        val svg = service.uploadFile(FileType.FILE, "payload.svg", byteArrayOf(1), "image/svg+xml")
        assertEquals(FileError.UnsupportedFormat, svg.getError())

        // Nothing was written to disk for the rejected uploads.
        val after = File("private-uploads/file").listFiles()?.size ?: 0
        assertEquals(before, after)
    }

    @Test
    fun uploadStoresTheFileUnderAUniquePrivateUrl() = runBlocking {
        val result = service.uploadFile(
            fileType = FileType.FILE,
            fileName = "quarterly report.pdf",
            fileData = byteArrayOf(1, 2, 3),
            mimeType = "application/pdf",
        )

        val media = result.get()
        assertNotNull(media)
        assertTrue(media.url.startsWith("/v1/files/content/"), "private attachments must be served authenticated")
        assertTrue(media.url.endsWith(".pdf"))
        media.url.substringAfterLast('/').let { name ->
            val file = File("private-uploads/file", name)
            stored.add(file)
            assertTrue(file.isFile, "the bytes must be on disk")
        }
        coVerify(exactly = 1) { uploadStore.register(any(), any()) }
    }
}
