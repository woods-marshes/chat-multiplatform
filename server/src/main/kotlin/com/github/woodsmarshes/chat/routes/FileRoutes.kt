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
import com.github.michaelbull.result.mapBoth
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.logging.Logger
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FileRoutes")

/** Per-type cap from [FileUploadConfig]; falls back to 100MB for unknown types. */
private fun maxBytesFor(type: FileType): Long =
    FileUploadConfig.maxFileSize[type.name.lowercase()] ?: 100L * 1024 * 1024


fun Route.fileRoutes() {
    val fileService by inject<FileService>()
    val settingsService by inject<ConversationSettingsService>()
    val userService by inject<UserService>()

    rateLimit(RateLimitName("uploads")) {
    post<V1.Files.Upload> { params ->
        val type = params.type
        val maxBytes = maxBytesFor(type)
        val multipartData = call.receiveMultipart()
        var result: MediaContent? = null
        var error: FileError? = null

        multipartData.forEachPart { part ->
                try {
                    if (error != null) return@forEachPart
                    when (part) {
                        is PartData.FileItem -> {
                            val rawFileName = part.originalFileName ?: "unknown"
                            val sanitized = fileService.sanitizeFileName(rawFileName)
                            if (sanitized == null) {
                                error = FileError.NoFileProvided
                                return@forEachPart
                            }
                            val mimeType = part.contentType?.toString() ?: "application/octet-stream"

                            val fileBytes = part.provider().readRemaining().readByteArray()
                            if (fileBytes.size > maxBytes) {
                                logger.warn("Upload rejected for {}: {} bytes exceeds cap", type, fileBytes.size)
                                error = FileError.FileTooLarge
                                return@forEachPart
                            }

                            result = fileService.uploadFile(
                                fileType = type,
                                fileName = sanitized,
                                fileData = fileBytes,
                                mimeType = mimeType
                            ).getOrThrow()
                        }
                        is PartData.FormItem -> { /* reserved */ }
                        else -> {}
                    }
                } finally {
                    part.dispose()
                }
            }

        when {
            error != null -> call.respond(HttpStatusCode.BadRequest, error!!)
            result != null -> call.respond(result!!)
            else -> call.respond(HttpStatusCode.BadRequest, FileError.NoFileProvided)
        }
    }

    post<V1.Files.Avatar> { params ->
        val userId = call.extractUserId()
        val maxBytes = maxBytesFor(FileType.AVATAR)
        val multipartData = call.receiveMultipart()
        var uploadedUrl: String? = null
        var error: FileError? = null

        multipartData.forEachPart { part ->
                try {
                    if (error != null) return@forEachPart
                    if (part is PartData.FileItem) {
                        val fileBytes = part.provider().readRemaining().readByteArray()
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

        if (error != null) {
            return@post call.respond(HttpStatusCode.BadRequest, error!!)
        }
        val url = uploadedUrl
        if (url == null) {
            return@post call.respond(HttpStatusCode.BadRequest, FileError.NoFileProvided)
        }

        val updateResult = if (params.isGroup) {
            val targetId = params.targetId
            if (targetId != null) {
                settingsService.updateGroupSettings(
                    conversationId = targetId,
                    userId = userId,
                    req = UpdateConversationSettingsRequest(avatarUrl = url),
                )
            } else {
                return@post call.respond(HttpStatusCode.BadRequest, "targetId is required for GROUP")
            }
        } else {
            userService.updateProfile(
                userId = userId,
                req = UpdateProfileRequest(avatarUrl = url),
            )
        }

        updateResult.mapBoth(
            success = { call.respond(mapOf("url" to url)) },
            failure = { err ->
                logger.error("Failed to update avatar reference: {}", err)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Avatar upload succeeded but profile update failed"))
            }
        )
    }
    }
}
