package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TemporaryUploadStoreTest {

    private val store = TemporaryUploadStoreImpl()
    private val tempFiles = mutableListOf<File>()
    private val tempDirs = mutableListOf<File>()
    private val uploader = Uuid.random()

    @AfterTest
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        tempDirs.forEach { dir ->
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
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
        store.register(content, listOf(file.absolutePath), uploaderId = uploader)

        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))
        // A second send of the same URL (forward / re-attach) must not fail.
        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))
    }

    @Test
    fun unknownUrlYieldsNull() {
        assertNull(store.retrieveAndConfirm("/v1/files/content/missing.pdf", senderId = uploader))
    }

    @Test
    fun cleanupKeepsConfirmedFilesAndDropsUnconfirmedOnes() {
        val confirmedFile = newTempFile()
        val abandonedFile = newTempFile()
        store.register(media("/v1/files/content/kept.pdf"), listOf(confirmedFile.absolutePath), uploaderId = uploader)
        store.register(media("/v1/files/content/dropped.pdf"), listOf(abandonedFile.absolutePath), uploaderId = uploader)
        assertNotNull(store.retrieveAndConfirm("/v1/files/content/kept.pdf", senderId = uploader))

        // Let the uploads age past the (zero) retention window: the store
        // stamps them with the wall clock, so a same-instant cleanup must not
        // race the assertion.
        Thread.sleep(5)
        store.cleanExpiredFiles(0)

        assertTrue(confirmedFile.exists(), "confirmed upload must survive cleanup")
        assertFalse(abandonedFile.exists(), "unconfirmed upload must be deleted")

        // The bookkeeping for both is gone; the confirmed URL re-attaches as
        // an already-persisted file via its message mapping, not the store.
        assertNull(store.retrieveAndConfirm("/v1/files/content/kept.pdf", senderId = uploader))
        assertNull(store.retrieveAndConfirm("/v1/files/content/dropped.pdf", senderId = uploader))
    }

    @Test
    fun pendingUploadBoundToUploaderCannotBeClaimedByAnotherSender() {
        val file = newTempFile()
        val attacker = Uuid.random()
        val content = media("/v1/files/content/bound.pdf")
        store.register(content, listOf(file.absolutePath), uploaderId = uploader)

        assertNull(store.retrieveAndConfirm(content.url, senderId = attacker))
        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))
    }

    @Test
    fun pendingUploadBoundToUploaderRejectsNullSender() {
        val file = newTempFile()
        val content = media("/v1/files/content/bound-null.pdf")
        store.register(content, listOf(file.absolutePath), uploaderId = uploader)

        assertNull(store.retrieveAndConfirm(content.url, senderId = null))
        // Still unclaimed by the legitimate uploader afterwards.
        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))
    }

    @Test
    fun privatePendingUploadWithoutOwnerCannotBeClaimed() {
        val file = newTempFile()
        val sender = Uuid.random()
        val content = media("/v1/files/content/ownerless.pdf")
        store.register(content, listOf(file.absolutePath), uploaderId = null)

        assertNull(store.retrieveAndConfirm(content.url, senderId = sender))
        assertNull(store.retrieveAndConfirm(content.url, senderId = null))
    }

    @Test
    fun publicPendingUploadWithoutOwnerRemainsClaimableWhileFileExists() {
        val file = newTempFile()
        val content = ImageContent(
            url = "/uploads/image/public-ownerless.png",
            fileName = "public.png",
            width = 1,
            height = 1,
            size = 1,
        )
        store.register(content, listOf(file.absolutePath), uploaderId = null)

        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))
    }

    @Test
    fun invalidateEvictsConfirmedUploadSoDeletedFileCannotBeReattached() {
        val file = newTempFile()
        val content = media("/v1/files/content/withdrawn.pdf")
        store.register(content, listOf(file.absolutePath), uploaderId = uploader)
        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))

        store.invalidate(content.url)

        assertNull(store.retrieveAndConfirm(content.url, senderId = uploader))
    }

    @Test
    fun retrieveAndConfirmEvictsEntryWhenPhysicalFileWasDeleted() {
        val file = newTempFile()
        val content = media("/v1/files/content/gone.pdf")
        store.register(content, listOf(file.absolutePath), uploaderId = uploader)
        assertNotNull(store.retrieveAndConfirm(content.url, senderId = uploader))

        assertTrue(file.delete())

        assertNull(store.retrieveAndConfirm(content.url, senderId = uploader))
    }

    @Test
    fun cleanupRetainsEntryForRetryWhenFileDeletionFails() {
        val nonEmptyDir = File.createTempFile("upload-dir-test", "").apply {
            delete()
            mkdirs()
            tempDirs.add(this)
        }
        val childInsideDir = File(nonEmptyDir, "child.bin").apply {
            writeBytes(byteArrayOf(1))
            tempFiles.add(this)
        }
        // File.delete() on a non-empty directory returns false while exists() stays true.
        val url = "/v1/files/content/undeletable.pdf"
        store.register(media(url), listOf(nonEmptyDir.absolutePath), uploaderId = uploader)

        Thread.sleep(5)
        store.cleanExpiredFiles(0)
        assertTrue(nonEmptyDir.exists(), "directory could not be deleted while non-empty")

        // Remove the blocker so the next sweep can succeed.
        assertTrue(childInsideDir.delete())
        store.cleanExpiredFiles(0)
        assertFalse(nonEmptyDir.exists(), "retained entry must be deleted on subsequent retry sweep")
    }

    @Test
    fun concurrentConfirmAndCleanupNeverLeavesConfirmedUploadWithDeletedFile() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(100) { idx ->
                val file = newTempFile()
                val url = "/v1/files/content/race-$idx.pdf"
                store.register(media(url), listOf(file.absolutePath), uploaderId = uploader)
                Thread.sleep(2)

                val startLatch = CountDownLatch(1)
                val confirmFuture = pool.submit(Callable<MediaContent?> {
                    startLatch.await()
                    store.retrieveAndConfirm(url, senderId = uploader)
                })
                val cleanFuture = pool.submit {
                    startLatch.await()
                    store.cleanExpiredFiles(0)
                }
                startLatch.countDown()

                val confirmed = confirmFuture.get(5, TimeUnit.SECONDS)
                cleanFuture.get(5, TimeUnit.SECONDS)

                if (confirmed != null) {
                    assertTrue(
                        file.exists(),
                        "iteration $idx: retrieveAndConfirm succeeded so cleanup must not delete the physical file",
                    )
                } else {
                    assertFalse(
                        file.exists(),
                        "iteration $idx: cleanup won the race so retrieveAndConfirm returned null and file was deleted",
                    )
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
