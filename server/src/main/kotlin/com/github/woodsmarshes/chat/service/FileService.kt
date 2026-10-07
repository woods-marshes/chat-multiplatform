package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapBoth
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.FileType.*
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.PrivateFileRepository
import com.github.woodsmarshes.chat.utils.BlurHashEncoder
import com.github.woodsmarshes.chat.utils.FileUploadConfig
import com.github.woodsmarshes.chat.utils.MediaProbe
import com.github.woodsmarshes.chat.utils.PUBLIC_UPLOAD_URL_PREFIX
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import com.github.woodsmarshes.chat.utils.WaveformGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions
import org.slf4j.LoggerFactory
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Upper bound for one media processing unit (decode, thumbnail, ffmpeg frame). */
private val MEDIA_PROCESSING_TIMEOUT: Duration = 60.seconds

/** How long an upload may wait for a free media processing slot before rejection. */
private val MEDIA_QUEUE_TIMEOUT: Duration = 30.seconds

private const val MEDIA_PROCESSING_CONCURRENCY = 2

/** Hard wall-clock limit for one ffmpeg cover extraction before the process is killed. */
private const val FFMPEG_COVER_TIMEOUT_MS = 10_000L

/** Per-stage cap for the managed metadata probe. */
private const val PROBE_STAGE_CAP_MS = 10_000L

/** Per-stage cap for waveform generation (the watchdog kill, not the input duration cap). */
private const val WAVEFORM_STAGE_CAP_MS = 60_000L

/**
 * Stage budget under the overall media processing deadline: the smaller of
 * the stage's own cap and the remaining overall time. Null when the budget
 * is exhausted — the caller fails or skips the stage instead of starting a
 * subprocess that could outlive the request deadline.
 */
private fun stageTimeoutMs(deadlineNanos: Long, stageCapMs: Long): Long? {
    val remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000
    if (remainingMs <= 0) return null
    return minOf(stageCapMs, remainingMs)
}

class FileService(
    private val uploadStore: TemporaryUploadStore,
    private val privateFileRepository: PrivateFileRepository,
    private val participantRepository: ConversationParticipantRepository,
    private val maxImagePixels: Long = FileUploadConfig.maxImagePixels,
) {
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    private val uploadDir: String = "uploads"
    private val privateUploadDir: String = "private-uploads"

    // Media decoding and ffmpeg work are memory- and CPU-heavy; cap how many
    // run concurrently so a burst of uploads cannot exhaust the heap or CPUs.
    private val mediaProcessingSlots = Semaphore(MEDIA_PROCESSING_CONCURRENCY)

    // Extensions that must never be served back from /uploads
    // (stored-XSS and malware vectors); such uploads are rejected.
    private val BLOCKED_UPLOAD_EXTENSIONS = setOf(
        "html", "htm", "xhtml", "xht", "svg", "js", "mjs", "css",
        "php", "phtml", "jsp", "asp", "aspx",
        "exe", "dll", "bat", "cmd", "com", "scr", "ps1", "vbs", "wsf", "sh",
    )

    fun sanitizeFileName(fileName: String): String? {
        // Reject path traversal attempts
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return null
        }
        // Remove null bytes and trim
        val cleaned = fileName.replace("\u0000", "").trim()
        if (cleaned.isEmpty() || cleaned == ".") return null
        return cleaned
    }

    /**
     * Creates the upload directory tree. Called once at boot by the
     * composition layer — deliberately not a constructor side effect, so
     * constructing the service (e.g. in tests) never touches the filesystem.
     */
    fun ensureUploadDirectories() {
        val dirs = FileType.entries.map { it.name.lowercase() }.toMutableList()
        dirs.add("thumbnails")
        dirs.add("covers")

        dirs.forEach {
            val dir = File("$uploadDir/$it")
            if (!dir.exists()) dir.mkdirs()
        }
        val privateDir = File("$privateUploadDir/file")
        if (!privateDir.exists()) privateDir.mkdirs()
    }

    fun resolvePrivateFile(fileName: String): File? {
        if (!isSafeFileName(fileName)) return null
        val file = File("$privateUploadDir/file", fileName)
        return if (file.isFile) file else null
    }

    /**
     * Private attachments are downloadable only by members of a conversation
     * the file was actually sent to. The mapping is recorded when the message
     * is persisted, so a leaked URL alone grants nothing.
     */
    suspend fun resolveAuthorizedPrivateFile(fileName: String, requesterId: Uuid): File? {
        val conversations = privateFileRepository.getConversationsForFile(fileName)
        if (conversations.isEmpty()) return null
        val isMember = conversations.any { conversationId ->
            participantRepository.getConversationParticipant(requesterId, conversationId) != null
        }
        if (!isMember) return null
        return resolvePrivateFile(fileName)
    }

    /**
     * Removes a private attachment that no live message references anymore:
     * the bytes on disk and every conversation mapping recorded for it.
     */
    suspend fun deletePrivateFile(fileName: String) {
        if (!isSafeFileName(fileName)) return
        val file = File("$privateUploadDir/file", fileName)
        if (file.isFile) file.delete()
        privateFileRepository.deleteMappings(fileName)
    }

    /**
     * Whether a public /uploads/… URL currently resolves to a file on disk.
     * Used to let old messages re-attach their media; the tree is public by
     * design, so existence here grants no new access.
     */
    fun publicFileExists(url: String): Boolean {
        if (!url.startsWith(PUBLIC_UPLOAD_URL_PREFIX)) return false
        val relative = url.removePrefix("/").replace('/', File.separatorChar)
        if (relative.contains("..")) return false
        return File(relative).isFile
    }

    /**
     * Removes an unreferenced public upload — e.g. an avatar whose profile or
     * group update failed after the bytes were written. Avatars are
     * deliberately not registered in the temp store, so this is their only
     * cleanup path.
     */
    fun deletePublicUpload(url: String) {
        if (!url.startsWith(PUBLIC_UPLOAD_URL_PREFIX)) return
        val relative = url.removePrefix("/").replace('/', File.separatorChar)
        if (relative.contains("..")) return
        File(relative).delete()
    }

    private fun isSafeFileName(fileName: String): Boolean {
        // Reject path traversal attempts
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false
        }
        // Remove null bytes and trim
        val cleaned = fileName.replace("\u0000", "").trim()
        return cleaned.isNotEmpty() && cleaned != "."
    }

    /** A validated upload staged on disk at its final path. */
    data class StagedUpload(
        val file: File,
        val fileType: FileType,
        /** Sanitized display name as it should appear in message metadata. */
        val fileName: String,
        val uniqueName: String,
    )

    /**
     * Validates the upload metadata and reserves the final on-disk path, so
     * the caller can stream bytes into place without buffering the whole body.
     */
    fun stageUpload(fileType: FileType, rawFileName: String): Result<StagedUpload, FileError> {
        val sanitized = sanitizeFileName(rawFileName)
            ?: return Err(FileError.NoFileProvided)
        val extension = sanitized.substringAfterLast(".", "").lowercase()
        if (extension in BLOCKED_UPLOAD_EXTENSIONS) {
            return Err(FileError.UnsupportedFormat)
        }
        val fileUuid = Uuid.random().toString()
        val uniqueName = if (extension.isNotEmpty()) "$fileUuid.$extension" else fileUuid
        val isPrivate = fileType == FILE
        val targetDir = if (isPrivate) "$privateUploadDir/file" else "$uploadDir/${fileType.name.lowercase()}"
        return Ok(
            StagedUpload(
                file = File(targetDir, uniqueName),
                fileType = fileType,
                fileName = sanitized,
                uniqueName = uniqueName,
            )
        )
    }

    /**
     * Processes a staged file: builds the URL, derives thumbnail/blurhash/
     * waveform, registers the artifact for expiry cleanup. Media processing
     * is slot-limited (bounded queue, time-boxed work); on any failure —
     * including cancellation — the staged bytes and any half-written derived
     * artifacts are removed, so nothing is left behind.
     */
    suspend fun finalizeUpload(
        staged: StagedUpload,
        mimeType: String,
        byteSize: Long,
    ): Result<MediaContent, FileError> = withContext(Dispatchers.IO) {
        try {
            val result = coroutineBinding {
                try {
                    val url = if (staged.fileType == FILE) {
                        "/v1/files/content/${staged.uniqueName}"
                    } else {
                        "/$uploadDir/${staged.fileType.name.lowercase()}/${staged.uniqueName}"
                    }

                    val media: MediaContent = when (staged.fileType) {
                        FILE -> FileContent(url, staged.fileName, mimeType, byteSize)
                        else -> {
                            // Queue for a slot with a deadline: acquire()
                            // suspends (no thread is pinned while waiting) and
                            // a saturated media pipeline rejects the request
                            // instead of letting it wait forever.
                            try {
                                withTimeout(MEDIA_QUEUE_TIMEOUT) { mediaProcessingSlots.acquire() }
                            } catch (e: TimeoutCancellationException) {
                                logger.warn("Media processing queue saturated; rejecting upload of {}", staged.fileName)
                                Err(FileError.ProcessingFailed).bind()
                            }
                            try {
                                withTimeout(MEDIA_PROCESSING_TIMEOUT) {
                                    // One monotonic deadline governs every
                                    // subprocess stage of this upload; each
                                    // stage below runs under min(its own cap,
                                    // the remaining budget), so stage limits
                                    // cannot stack past the total.
                                    val deadlineNanos = System.nanoTime() + MEDIA_PROCESSING_TIMEOUT.inWholeNanoseconds
                                    when (staged.fileType) {
                                        IMAGE -> processImage(staged.file, staged.fileName, staged.uniqueName, byteSize, mimeType).bind()
                                        VIDEO -> processVideo(staged.file, staged.fileName, staged.uniqueName.substringBeforeLast("."), byteSize, mimeType, deadlineNanos).bind()
                                        AUDIO -> processAudio(staged.file, staged.fileName, byteSize, mimeType, deadlineNanos).bind()
                                        AVATAR -> {
                                            processAvatar(staged.file, staged.fileName).bind()
                                            ImageContent(
                                                url = url,
                                                fileName = staged.fileName,
                                                width = 400,
                                                height = 400,
                                                size = staged.file.length(),
                                                mimeType = "image/jpeg"
                                            )
                                        }
                                        FILE -> error("unreachable")
                                    }
                                }
                            } catch (e: TimeoutCancellationException) {
                                Err(FileError.ProcessingFailed).bind()
                            } finally {
                                mediaProcessingSlots.release()
                            }
                        }
                    }

                    if (staged.fileType != FileType.AVATAR) {
                        registerToTempStore(media, staged.file)
                    }

                    media
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Err(FileError.Unknown(e.message)).bind()
                }
            }
            result.mapBoth(
                success = { },
                failure = { cleanupStagedUpload(staged) },
            )
            result
        } catch (e: CancellationException) {
            // Cancellation unwinds the binding before the failure cleanup
            // above can run — the staged bytes would otherwise be orphaned.
            cleanupStagedUpload(staged)
            throw e
        }
    }

    /** Removes the staged bytes plus any derived thumbnail/cover artifacts. */
    private fun cleanupStagedUpload(staged: StagedUpload) {
        staged.file.delete()
        derivedArtifactFiles(staged).forEach { artifact -> artifact.delete() }
    }

    /**
     * Deterministic names of the artifacts a successful/failed media pass may
     * have produced; files that were never created are a no-op to delete.
     */
    private fun derivedArtifactFiles(staged: StagedUpload): List<File> = when (staged.fileType) {
        FileType.IMAGE -> listOf(File("$uploadDir/thumbnails", "thumb_${staged.uniqueName}"))
        FileType.VIDEO -> listOf(File("$uploadDir/covers", "cover_${staged.uniqueName.substringBeforeLast(".")}.jpg"))
        else -> emptyList()
    }

    suspend fun uploadFile(
        fileType: FileType,
        fileName: String,
        fileData: ByteArray,
        mimeType: String
    ): Result<MediaContent, FileError> {
        val stagedResult = stageUpload(fileType, fileName)
        stagedResult.getError()?.let { return Err(it) }
        val staged = stagedResult.get()!!

        try {
            staged.file.writeBytes(fileData)
        } catch (e: IOException) {
            staged.file.delete()
            return Err(FileError.IoError)
        }
        return finalizeUpload(staged, mimeType, fileData.size.toLong())
    }

    private suspend fun processAvatar(file: File, fileName: String): Result<Unit, FileError> = withContext(Dispatchers.IO) {
        // Avatars feed the same image decoder as photos: the 400x400 output
        // size does not bound the decode, so the pixel budget applies here too.
        checkImagePixelBudget(file, fileName)?.let { return@withContext Err(it) }
        try {
            Thumbnails.of(file)
                .size(400, 400)
                .crop(Positions.CENTER)
                .outputFormat("jpg")
                .outputQuality(1.0)
                .toFile(file)
            Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to process avatar", e)
            Err(FileError.ProcessingFailed)
        }
    }

    /**
     * Header-dimension precheck shared by every path that feeds an image
     * decoder — photos and avatars alike. A small compressed file can still
     * decode into a huge pixel buffer, so the byte cap alone does not bound
     * the decode cost. Returns the error to fail with, or null when the image
     * may be decoded.
     */
    private fun checkImagePixelBudget(file: File, fileName: String): FileError? {
        val dimensions = readImageDimensions(file) ?: return FileError.UnsupportedFormat
        val (width, height) = dimensions
        if (width <= 0 || height <= 0) return FileError.UnsupportedFormat
        if (width.toLong() * height > maxImagePixels) {
            logger.warn(
                "Image rejected for {}: {}x{} exceeds the {} pixel cap",
                fileName, width, height, maxImagePixels
            )
            return FileError.FileTooLarge
        }
        return null
    }

    /**
     * Reads the image dimensions from the file header WITHOUT decoding pixel
     * data, so oversized images are rejected before claiming their decoded-
     * size memory. Null when the file is not a readable image.
     */
    private fun readImageDimensions(file: File): Pair<Int, Int>? = try {
        ImageIO.createImageInputStream(file)?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    private suspend fun processImage(
        file: File,
        fileName: String,
        uniqueName: String,
        size: Long,
        mimeType: String
    ): Result<ImageContent, FileError> = coroutineScope {
        // Guard the decode before any pixel data is read (shared with avatars).
        checkImagePixelBudget(file, fileName)?.let { return@coroutineScope Err(it) }

        val img = withContext(Dispatchers.IO) {
            try {
                ImageIO.read(file)
            } catch (e: Exception) {
                null
            }
        } ?: return@coroutineScope Err(FileError.UnsupportedFormat)

        val width = img.width
        val height = img.height
        val ratio = width.toDouble() / height
        val url = "/$uploadDir/image/$uniqueName"

        // Decode once: the blurhash source is scaled down eagerly and the
        // thumbnail reuses the decoded image instead of re-reading and
        // re-decoding the file from disk.
        val blurSource = try {
            Thumbnails.of(img).size(32, 32).asBufferedImage()
        } catch (e: Exception) {
            logger.error("Failed to scale BlurHash source for $fileName", e)
            null
        }

        val thumbTask = async<String?>(Dispatchers.Default) {
            val thumbName = "thumb_$uniqueName"
            val thumbFile = File("$uploadDir/thumbnails", thumbName)
            try {
                when {
                    ratio < 0.4 -> Thumbnails
                        .of(img)
                        .size(800, 2000)
                        .crop(Positions.TOP_CENTER)
                        .size(800, 1200)
                        .toFile(thumbFile)
                    ratio > 2.5 -> Thumbnails
                        .of(img)
                        .size(2000, 800)
                        .crop(Positions.CENTER)
                        .size(1200, 800)
                        .toFile(thumbFile)
                    else -> Thumbnails
                        .of(img)
                        .size(800, 800)
                        .keepAspectRatio(true)
                        .toFile(thumbFile)
                }
                "/$uploadDir/thumbnails/$thumbName"
            } catch (e: Exception) {
                logger.error("Failed to generate thumbnail for $fileName", e)
                null
            }
        }

        val blurHashTask = async<String?>(Dispatchers.Default) {
            try {
                blurSource?.let { BlurHashEncoder.encode(it, 4, 3) }
            } catch (e: Exception) {
                logger.error("Failed to encode BlurHash for $fileName", e)
                null
            }
        }

        Ok(
            ImageContent(
                url = url,
                fileName = fileName,
                width = width,
                height = height,
                size = size,
                blurHash = blurHashTask.await(),
                thumbnailUrl = thumbTask.await(),
                mimeType = mimeType
            )
        )
    }

    /**
     * Extracts the first video frame into [output] as a JPEG under a hard
     * wall-clock limit: a hung ffmpeg is destroyed forcibly instead of
     * holding the request or a processing slot. False when no cover was
     * produced.
     */
    private fun extractVideoCover(videoFile: File, output: File, timeoutMs: Long): Boolean {
        val process = ProcessBuilder(
            DefaultFFMPEGLocator().executablePath,
            "-y",
            "-i", videoFile.absolutePath,
            "-vframes", "1",
            "-an",
            "-q:v", "2",
            output.absolutePath,
        )
            .redirectErrorStream(true)
            // ffmpeg logs to stderr; discarding the merged stream removes the
            // pipe-full deadlock without a drain thread.
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        return try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                logger.warn("Video cover extraction timed out for {}", videoFile.name)
                false
            } else {
                process.exitValue() == 0 && output.isFile
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            false
        } finally {
            process.destroy()
        }
    }

    private suspend fun processVideo(
        file: File,
        fileName: String,
        fileUuid: String,
        size: Long,
        mimeType: String,
        deadlineNanos: Long,
    ): Result<VideoContent, FileError> = coroutineBinding {
        try {
            val probeTimeoutMs = stageTimeoutMs(deadlineNanos, PROBE_STAGE_CAP_MS)
                ?: Err(FileError.ProcessingFailed).bind()
            val probe = withContext(Dispatchers.IO) { MediaProbe.probe(file, probeTimeoutMs) }
            val info = when (probe) {
                is MediaProbe.Outcome.Success -> probe.result
                else -> {
                    logger.warn("Media probe failed for {}: {}", fileName, probe)
                    Err(FileError.ProcessingFailed).bind()
                }
            }

            val coverFile = File("$uploadDir/covers", "cover_$fileUuid.jpg")

            val coverTimeoutMs = stageTimeoutMs(deadlineNanos, FFMPEG_COVER_TIMEOUT_MS)
            if (coverTimeoutMs == null) {
                logger.warn("Skipping cover extraction for {}: media budget exhausted", fileName)
            } else {
                withContext(Dispatchers.IO) {
                    if (!extractVideoCover(file, coverFile, coverTimeoutMs)) {
                        logger.warn("No cover extracted for {}", fileName)
                    }
                }
            }

            VideoContent(
                url = "/$uploadDir/video/${file.name}",
                fileName = fileName,
                coverUrl = if (coverFile.exists()) "/$uploadDir/covers/${coverFile.name}" else null,
                width = info.width ?: 0,
                height = info.height ?: 0,
                duration = info.durationMs,
                size = size,
                mimeType = mimeType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Err(FileError.ProcessingFailed).bind()
        }
    }

    private suspend fun processAudio(
        file: File,
        fileName: String,
        size: Long,
        mimeType: String,
        deadlineNanos: Long,
    ): Result<AudioContent, FileError> = coroutineBinding {
        try {
            val probeTimeoutMs = stageTimeoutMs(deadlineNanos, PROBE_STAGE_CAP_MS)
                ?: Err(FileError.ProcessingFailed).bind()
            val probe = withContext(Dispatchers.IO) { MediaProbe.probe(file, probeTimeoutMs) }
            val info = when (probe) {
                is MediaProbe.Outcome.Success -> probe.result
                else -> {
                    logger.warn("Media probe failed for {}: {}", fileName, probe)
                    Err(FileError.ProcessingFailed).bind()
                }
            }

            val waveformTimeoutMs = stageTimeoutMs(deadlineNanos, WAVEFORM_STAGE_CAP_MS)
            val waveformData = if (waveformTimeoutMs == null) {
                logger.warn("Skipping waveform for {}: media budget exhausted", fileName)
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    try {
                        WaveformGenerator.generate(file, info.durationMs, 80, waveformTimeoutMs)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.error("Failed to generate waveform for $fileName", e)
                        emptyList()
                    }
                }
            }

            AudioContent(
                url = "/$uploadDir/audio/${file.name}",
                fileName = fileName,
                duration = info.durationMs,
                waveform = waveformData,
                size = size,
                mimeType = mimeType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Err(FileError.ProcessingFailed).bind()
        }
    }

    private fun registerToTempStore(media: MediaContent, originalFile: File) {
        val paths = mutableListOf(originalFile.absolutePath)
        when (media) {
            is ImageContent -> media.thumbnailUrl?.let { paths.add(toPhysicalPath(it)) }
            is VideoContent -> media.coverUrl?.let { paths.add(toPhysicalPath(it)) }
            else -> {}
        }
        uploadStore.register(media, paths)
    }

    private fun toPhysicalPath(url: String): String = url.removePrefix("/").replace("/", File.separator)
}
