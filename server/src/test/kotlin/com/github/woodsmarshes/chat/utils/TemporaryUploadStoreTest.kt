package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.core.model.FileContent
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemporaryUploadStoreTest {

    private val store = TemporaryUploadStoreImpl()
    private val tempFiles = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    private fun newTempFile(): File =
        File.createTempFile("upload-store-test", ".bin").also { tempFiles.add(it) }

    private fun media(url: String) = FileContent(
        url = url, fileName = "report.pdf", mimeType = "application/pdf", size = 1,
    )

    @Test
    fun confirmIsIdempotentSoForwardingKeepsWorking() {
        val file = newTempFile()
        val content = media("/v1/files/content/a.pdf")
        store.register(content, listOf(file.absolutePath))

        assertNotNull(store.retrieveAndConfirm(content.url))
        // A second send of the same URL (forward / re-attach) must not fail.
        assertNotNull(store.retrieveAndConfirm(content.url))
    }

    @Test
    fun unknownUrlYieldsNull() {
        assertNull(store.retrieveAndConfirm("/v1/files/content/missing.pdf"))
    }

    @Test
    fun cleanupKeepsConfirmedFilesAndDropsUnconfirmedOnes() {
        val confirmedFile = newTempFile()
        val abandonedFile = newTempFile()
        store.register(media("/v1/files/content/kept.pdf"), listOf(confirmedFile.absolutePath))
        store.register(media("/v1/files/content/dropped.pdf"), listOf(abandonedFile.absolutePath))
        assertNotNull(store.retrieveAndConfirm("/v1/files/content/kept.pdf"))

        // Let the uploads age past the (zero) retention window: the store
        // stamps them with the wall clock, so a same-instant cleanup must not
        // race the assertion.
        Thread.sleep(5)
        store.cleanExpiredFiles(0)

        assertTrue(confirmedFile.exists(), "confirmed upload must survive cleanup")
        assertFalse(abandonedFile.exists(), "unconfirmed upload must be deleted")

        // The bookkeeping for both is gone; the confirmed URL re-attaches as
        // an already-persisted file via its message mapping, not the store.
        assertNull(store.retrieveAndConfirm("/v1/files/content/kept.pdf"))
        assertNull(store.retrieveAndConfirm("/v1/files/content/dropped.pdf"))
    }
}
