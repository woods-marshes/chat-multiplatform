package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.error.FileError
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.ConversationSettingsService
import com.github.woodsmarshes.chat.service.FileService
import com.github.woodsmarshes.chat.service.UserService
import com.github.woodsmarshes.chat.utils.FileUploadConfig
import com.github.woodsmarshes.chat.utils.extractUserId
import com.github.woodsmarshes.chat.utils.toHttpStatusCode
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapBoth
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondFile
import io.ktor.server.resources.post
import io.ktor.server.routing.get as routingGet
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.logging.Logger
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("FileRoutes")

/** Per-type cap from [FileUploadConfig]; falls back to 100MB for unknown types. */
private fun maxBytesFor(type: FileType): Long =
    FileUploadConfig.maxFileSize[type.name.lowercase()] ?: 100L * 1024 * 1024

private const val UPLOAD_CHUNK_BYTES = 64L * 1024

/**
 * Streams the part body into [file] without ever buffering it whole, up to
 * [cap] bytes. Returns the written byte count, or null when the cap was
 * exceeded (the partial file must then be removed by the caller).
 */
private suspend fun ByteReadChannel.copyToFileWithCap(file: File, cap: Long): Long? {
    var total = 0L
    file.outputStream().use { out ->
        while (true) {
            val packet = readRemaining(UPLOAD_CHUNK_BYTES)
            if (packet.exhausted()) return total
            val chunk = packet.readByteArray()
            total += chunk.size
            if (total > cap) return null
            out.write(chunk)
        }
    }
    return total
}

fun Route.fileRoutes() {
    val fileService by inject<FileService>()
    val settingsService by inject<ConversationSettingsService>()
    val userService by inject<UserService>()

    rateLimit(RateLimitName("uploads")) {
    post<V1.Files.Upload> { params ->
        val userId = call.extractUserId()
        val type = params.type
        val maxBytes = maxBytesFor(type)
        val multipartData = call.receiveMultipart()
        var result: MediaContent? = null
        var error: FileError? = null
        var staged: FileService.StagedUpload? = null

        multipartData.forEachPart { part ->
                try {
                    if (error != null) return@forEachPart
                    when (part) {
                        is PartData.FileItem -> {
                            // Exactly one file part per request: extra parts
                            // used to be fully processed and silently dropped
                            // (last one won), multiplying disk and media work
                            // behind a single rate-limited request.
                            if (staged != null) {
                                error = FileError.MultipleFiles
                                return@forEachPart
                            }
                            val stagedResult = fileService.stageUpload(type, part.originalFileName ?: "unknown")
                            val s = stagedResult.get()
                            if (s == null) {
                                error = stagedResult.getError()
                                return@forEachPart
                            }

                            // Ownership: the route holds the staged bytes from
                            // the moment streaming starts until finalizeUpload
                            // reports success. EVERY other exit from this block
                            // — size cap exceeded, stream failure, cancellation
                            // — goes through the finally, so no branch can
                            // forget to remove a half-written file.
                            var finalizeSucceeded = false
                            try {
                                // Stream one byte past the cap: an oversized
                                // upload is rejected without ever buffering
                                // the whole body.
                                val written = part.provider().copyToFileWithCap(s.file, maxBytes)
                                if (written == null) {
                                    logger.warn("Upload rejected for {}: exceeds cap of {} bytes", type, maxBytes)
                                    error = FileError.FileTooLarge
                                    return@forEachPart
                                }
                                staged = s
                                val media = fileService.finalizeUpload(
                                    staged = s,
                                    mimeType = part.contentType?.toString() ?: "application/octet-stream",
                                    byteSize = written,
                                    uploaderId = userId,
                                )
                                if (media.isOk) finalizeSucceeded = true
                                result = media.getOrThrow()
                            } finally {
                                if (!finalizeSucceeded) s.file.delete()
                            }
                        }
                        is PartData.FormItem -> { /* reserved */ }
                        else -> {}
                    }
                } finally {
                    part.dispose()
                }
            }

        val failure = error
        val success = result
        val outcome: Result<MediaContent, FileError> = when {
            failure != null -> Err(failure)
            success != null -> Ok(success)
            else -> Err(FileError.NoFileProvided)
        }
        outcome.mapBoth(
            success = { call.respond(it) },
            failure = { call.respond(it.toHttpStatusCode(), it) }
        )
    }

    post<V1.Files.Avatar> { params ->
        val userId = call.extractUserId()
        val targetId = params.targetId
        val maxBytes = maxBytesFor(FileType.AVATAR)

        // Group avatars need owner rights — checked BEFORE any bytes are
        // written; a late permission failure used to leave the file behind.
        if (params.isGroup) {
            if (targetId == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "targetId is required for GROUP")
            }
            settingsService.checkGroupAvatarPermission(targetId, userId).mapBoth(
                success = { },
                failure = { err -> return@post call.respond(err.toHttpStatusCode(), err) },
            )
        }

        val multipartData = call.receiveMultipart()
        var uploadedUrl: String? = null
        var error: FileError? = null

        // Ownership handover: the request owns the avatar file until the
        // reference update has been dispatched — any unwinding before that
        // (multipart failure, cancellation) deletes it in the finally below.
        // Once dispatched the outcome, not this request, owns the file:
        // - a returned Err means definitely not written → deleted below;
        // - any exception means the commit outcome is UNKNOWN — group
        //   settings commit the profile row before publishing events, and
        //   a commit-phase connection error is equally indeterminate —
        //   so the file is kept (a reclaimable orphan beats a dangling
        //   database reference).
        var referenceDispatched = false

        try {
            multipartData.forEachPart { part ->
                try {
                    if (error != null) return@forEachPart
                    if (part is PartData.FileItem) {
                        if (uploadedUrl != null) {
                            error = FileError.MultipleFiles
                            return@forEachPart
                        }
                        val fileBytes = part.provider().readRemaining(maxBytes + 1).readByteArray()
                        if (fileBytes.size > maxBytes) {
                            error = FileError.FileTooLarge
                            return@forEachPart
                        }

                        val media = fileService.uploadFile(
                            fileType = FileType.AVATAR,
                            fileName = "avatar.jpg",
                            fileData = fileBytes,
                            mimeType = part.contentType?.toString() ?: "image/jpeg"
                        ).getOrThrow()
                        uploadedUrl = media.url
                    }
                } finally {
                    part.dispose()
                }
            }

            error?.let { failure ->
                return@post call.respond(failure.toHttpStatusCode(), failure)
            }
            val url = uploadedUrl
            if (url == null) {
                return@post call.respond(HttpStatusCode.BadRequest, FileError.NoFileProvided)
            }

            referenceDispatched = true
            val updateResult = try {
                if (params.isGroup) {
                    settingsService.updateGroupSettings(
                        conversationId = targetId!!,
                        userId = userId,
                        req = UpdateConversationSettingsRequest(avatarUrl = url),
                    )
                } else {
                    userService.updateProfile(
                        userId = userId,
                        req = UpdateProfileRequest(avatarUrl = url),
                    )
                }
            } catch (e: CancellationException) {
                // Commit outcome unknown — the file is kept. Log the URL so a
                // future reconciliation pass can find the kept orphan.
                logger.warn("Avatar reference update cancelled; keeping {} for reconciliation", url)
                throw e
            } catch (e: Exception) {
                logger.error("Avatar reference update threw; keeping {} for reconciliation", url, e)
                throw e
            }

            updateResult.mapBoth(
                success = { call.respond(mapOf("url" to url)) },
                failure = { err ->
                    fileService.deletePublicUpload(url)
                    logger.error("Failed to update avatar reference: {}", err)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Avatar upload succeeded but profile update failed"))
                }
            )
        } finally {
            if (!referenceDispatched) {
                uploadedUrl?.let { fileService.deletePublicUpload(it) }
            }
        }
    }

    }

    rateLimit(RateLimitName("files")) {
    // Chat attachments are not part of the public /uploads tree: they are
    // served from here so that reaching them requires both a valid token and
    // membership in a conversation the file was actually sent to.
    routingGet("/v1/files/content/{fileName}") {
        val userId = call.extractUserId()
        val requested = call.parameters["fileName"].orEmpty()
        val file = fileService.resolveAuthorizedPrivateFile(requested, userId)
        if (file == null) {
            call.respond(HttpStatusCode.NotFound, FileError.NoFileProvided)
        } else {
            call.respondFile(file)
        }
    }
    }
}
