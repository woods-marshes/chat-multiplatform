package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.uuid.Uuid
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun pngBytes(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
            out.toByteArray()
        }

    @Test
    fun oversizedPixelImagesAreRejectedBeforeDecode() = runBlocking {
        // A tiny compressed payload whose decoded size explodes past the
        // budget — the byte cap alone does not bound processing cost.
        val smallBudget = FileService(
            uploadStore = uploadStore,
            privateFileRepository = privateFileRepository,
            participantRepository = participantRepository,
            maxImagePixels = 1_000_000L,
        )
        smallBudget.ensureUploadDirectories()
        // 1200x1200 = 1.44M pixels, yet only a few KB compressed.
        val bytes = pngBytes(1200, 1200)
        val before = File("uploads/image").listFiles()?.size ?: 0

        val result = smallBudget.uploadFile(FileType.IMAGE, "pixel-bomb.png", bytes, "image/png")

        assertEquals(FileError.FileTooLarge, result.getError())
        // The staged bytes were removed again: no file left behind.
        assertEquals(before, File("uploads/image").listFiles()?.size ?: 0)
    }

    @Test
    fun oversizedPixelAvatarsAreRejectedBeforeDecode() = runBlocking {
        // Avatars feed the same decoder, so the pixel budget must gate them
        // too — the 400x400 output size says nothing about decode cost.
        val smallBudget = FileService(
            uploadStore = uploadStore,
            privateFileRepository = privateFileRepository,
            participantRepository = participantRepository,
            maxImagePixels = 1_000_000L,
        )
        smallBudget.ensureUploadDirectories()
        val bytes = pngBytes(1200, 1200)
        val before = File("uploads/avatar").listFiles()?.size ?: 0

        val result = smallBudget.uploadFile(FileType.AVATAR, "avatar.jpg", bytes, "image/jpeg")

        assertEquals(FileError.FileTooLarge, result.getError())
        assertEquals(before, File("uploads/avatar").listFiles()?.size ?: 0)
    }

    @Test
    fun imagesThatFailProcessingLeaveNoFilesBehind() = runBlocking {
        val before = File("uploads/image").listFiles()?.size ?: 0
        val thumbnailsBefore = File("uploads/thumbnails").listFiles()?.size ?: 0

        val result = service.uploadFile(FileType.IMAGE, "garbage.png", "definitely not an image".toByteArray(), "image/png")

        assertEquals(FileError.UnsupportedFormat, result.getError())
        assertEquals(before, File("uploads/image").listFiles()?.size ?: 0)
        assertEquals(thumbnailsBefore, File("uploads/thumbnails").listFiles()?.size ?: 0)
    }

    @Test
    fun imagesKeepTheirDimensionsAndDerivedArtifacts() = runBlocking {
        val bytes = pngBytes(640, 480)

        val media = service.uploadFile(FileType.IMAGE, "photo.png", bytes, "image/png").get()

        val image = assertNotNull(media) as ImageContent
        assertEquals(640, image.width)
        assertEquals(480, image.height)
        assertNotNull(image.blurHash)
        val thumbnailUrl = assertNotNull(image.thumbnailUrl)
        val thumbFile = File(thumbnailUrl.removePrefix("/"))
        stored.add(thumbFile)
        assertTrue(thumbFile.isFile, "the thumbnail must be on disk")
        stored += File(image.url.removePrefix("/"))
        coVerify(exactly = 1) { uploadStore.register(any(), any()) }
    }

    @Test
    fun deletePublicUploadRemovesExactlyItsTarget() {
        val dir = File("uploads/avatar").apply { mkdirs() }
        val target = File(dir, "gone-${Uuid.random()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        val keep = File(dir, "keep-${Uuid.random()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        stored.add(keep)

        service.deletePublicUpload("/uploads/avatar/${target.name}")

        assertFalse(target.exists())
        assertTrue(keep.exists(), "unrelated files must survive")

        // Traversal and foreign prefixes resolve to nothing.
        service.deletePublicUpload("/uploads/../uploads/avatar/${keep.name}")
        service.deletePublicUpload("https://example.test/${keep.name}")
        assertTrue(keep.exists())
    }
}
