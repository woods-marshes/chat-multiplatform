package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapBoth
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.FileType.*
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.utils.BlurHashEncoder
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import com.github.woodsmarshes.chat.utils.WaveformGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions
import org.slf4j.LoggerFactory
import ws.schild.jave.MultimediaObject
import ws.schild.jave.ScreenExtractor
import java.io.File
import javax.imageio.ImageIO
import kotlin.uuid.Uuid

class FileService(
    private val uploadStore: TemporaryUploadStore,
) {
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    private val uploadDir: String = "uploads"
    private val privateUploadDir: String = "private-uploads"

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

    init {
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
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return null
        }
        val file = File("$privateUploadDir/file", fileName)
        return if (file.isFile) file else null
    }

    suspend fun uploadFile(
        fileType: FileType,
        fileName: String,
        fileData: ByteArray,
        mimeType: String
    ) : Result<MediaContent, FileError> = withContext(Dispatchers.IO) {
        var physicalFile: File? = null
        val result = coroutineBinding {
            try {
                val fileUuid = Uuid.random().toString()
                val subFolder = fileType.name.lowercase()

                val extension = fileName.substringAfterLast(".", "").lowercase()
                if (extension in BLOCKED_UPLOAD_EXTENSIONS) {
                    Err(FileError.UnsupportedFormat).bind()
                }
                val uniqueName = if (extension.isNotEmpty()) "$fileUuid.$extension" else fileUuid
                val isPrivate = fileType == FILE
                val targetDir = if (isPrivate) "$privateUploadDir/file" else "$uploadDir/$subFolder"
                val newFile = File(targetDir, uniqueName).also { physicalFile = it }

                try {
                    newFile.writeBytes(fileData)
                } catch (e: IOException) {
                    Err(FileError.IoError).bind()
                }

                val url = if (isPrivate) {
                    "/v1/files/content/$uniqueName"
                } else {
                    "/$uploadDir/$subFolder/$uniqueName"
                }
                val size = fileData.size.toLong()


                val media: MediaContent = when (fileType) {
                    IMAGE -> processImage(newFile, fileName, uniqueName, size, mimeType).bind()
                    VIDEO -> processVideo(newFile, fileName, fileUuid, size, mimeType).bind()
                    AUDIO -> processAudio(newFile, fileName, size, mimeType).bind()
                    FILE -> FileContent(url, fileName, mimeType, size)
                    AVATAR -> {
                        processAvatar(newFile).bind()
                        ImageContent(
                            url = url,
                            fileName = fileName,
                            width = 400,
                            height = 400,
                            size = newFile.length(),
                            mimeType = "image/jpeg"
                        )
                    }
                }

                if (fileType != FileType.AVATAR) {
                    registerToTempStore(media, newFile)
                }

                media
            } catch (e: Exception) {
                Err(FileError.Unknown(e.message)).bind()
            }
        }
        // A failure after the bytes were written must not leave the file behind.
        result.mapBoth(success = { }, failure = { physicalFile?.delete() })
        result
    }

    private suspend fun processAvatar(file: File): Result<Unit, FileError> = withContext(Dispatchers.IO) {
        try {
            Thumbnails.of(file)
                .size(400, 400)
                .crop(Positions.CENTER)
                .outputFormat("jpg")
                .outputQuality(1.0)
                .toFile(file)
            Ok(Unit)
        } catch (e: Exception) {
            logger.error("Failed to process avatar", e)
            Err(FileError.ProcessingFailed)
        }
    }

    private suspend fun processImage(
        file: File,
        fileName: String,
        uniqueName: String,
        size: Long,
        mimeType: String
    ): Result<ImageContent, FileError> = coroutineScope {
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

        val thumbTask = async<String?>(Dispatchers.Default) {
            val thumbName = "thumb_$uniqueName"
            val thumbFile = File("$uploadDir/thumbnails", thumbName)
            try {
                when {
                    ratio < 0.4 -> Thumbnails
                        .of(file)
                        .size(800, 2000)
                        .crop(Positions.TOP_CENTER)
                        .size(800, 1200)
                        .toFile(thumbFile)
                    ratio > 2.5 -> Thumbnails
                        .of(file)
                        .size(2000, 800)
                        .crop(Positions.CENTER)
                        .size(1200, 800)
                        .toFile(thumbFile)
                    else -> Thumbnails
                        .of(file)
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
                val tempScaledImg = Thumbnails.of(file).size(32, 32).asBufferedImage()
                BlurHashEncoder.encode(tempScaledImg, 4, 3)
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

    private suspend fun processVideo(
        file: File,
        fileName: String,
        fileUuid: String,
        size: Long,
        mimeType: String
    ): Result<VideoContent, FileError> = coroutineBinding {
        try {
            val multimediaObject = MultimediaObject(file)
            val info = withContext(Dispatchers.Default) { multimediaObject.info }

            val coverFile = File("$uploadDir/covers", "cover_$fileUuid.jpg")

            withContext(Dispatchers.IO) {
                try {
                    ScreenExtractor().renderOneImage(
                        multimediaObject,
                        -1,
                        -1,
                        0L,
                        coverFile,
                        5
                    )
                } catch (e: Exception) {
                    logger.error("Failed to extract video cover for $fileName", e)
                }
            }

            VideoContent(
                url = "/$uploadDir/video/${file.name}",
                fileName = fileName,
                coverUrl = if (coverFile.exists()) "/$uploadDir/covers/${coverFile.name}" else null,
                width = info.video.size.width,
                height = info.video.size.height,
                duration = info.duration,
                size = size,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Err(FileError.ProcessingFailed).bind()
        }
    }

    private suspend fun processAudio(
        file: File,
        fileName: String,
        size: Long,
        mimeType: String
    ): Result<AudioContent, FileError> = coroutineBinding {
        try {
            val multimediaObject = MultimediaObject(file)
            val info = withContext(Dispatchers.Default) { multimediaObject.info }

            val waveformData = withContext(Dispatchers.Default) {
                try {
                    WaveformGenerator.generate(file, info.duration, 80)
                } catch (e: Exception) {
                    logger.error("Failed to generate waveform for $fileName", e)
                    emptyList()
                }
            }

            AudioContent(
                url = "/$uploadDir/audio/${file.name}",
                fileName = fileName,
                duration = info.duration,
                waveform = waveformData,
                size = size,
                mimeType = mimeType
            )
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
