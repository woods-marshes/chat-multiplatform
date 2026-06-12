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
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.logging.Logger
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.mapBoth
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FileRoutes")
private const val MAX_FILE_SIZE = 100 * 1024 * 1024L // 100MB

fun Route.fileRoutes() {
    val fileService by inject<FileService>()
    val settingsService by inject<ConversationSettingsService>()
    val userService by inject<UserService>()
    post<V1.Files.Upload> { params ->
        val multipartData = call.receiveMultipart()
        var result: MediaContent? = null
        var errorMessage: String? = null

        multipartData.forEachPart { part ->
            if (errorMessage != null) return@forEachPart
            when (part) {
                is PartData.FileItem -> {
                    val rawFileName = part.originalFileName ?: "unknown"
                    val sanitized = fileService.sanitizeFileName(rawFileName)
                    if (sanitized == null) {
                        errorMessage = "Invalid file name"
                        part.dispose()
                        return@forEachPart
                    }
                    val mimeType = part.contentType?.toString() ?: "application/octet-stream"

                    val fileBytes = part.provider().readRemaining().readByteArray()
                    if (fileBytes.size > MAX_FILE_SIZE) {
                        errorMessage = "File too large (max 100MB)"
                        part.dispose()
                        return@forEachPart
                    }

                    result = fileService.uploadFile(
                        fileType = params.type,
                        fileName = sanitized,
                        fileData = fileBytes,
                        mimeType = mimeType
                    ).getOrThrow()
                }
                is PartData.FormItem -> { /* reserved */ }
                else -> {}
            }
            part.dispose()
        }

        when {
            errorMessage != null -> call.respond(HttpStatusCode.BadRequest, errorMessage!!)
            result != null -> call.respond(result!!)
            else -> call.respond(HttpStatusCode.BadRequest, FileError.NoFileProvided)
        }
    }

    post<V1.Files.Avatar> { params ->
        val userId = call.extractUserId()
        val multipartData = call.receiveMultipart()
        var uploadedUrl: String? = null
        var errorMessage: String? = null

        multipartData.forEachPart { part ->
            if (errorMessage != null) return@forEachPart
            if (part is PartData.FileItem) {
                val fileBytes = part.provider().readRemaining().readByteArray()
                if (fileBytes.size > MAX_FILE_SIZE) {
                    errorMessage = "File too large"
                    part.dispose()
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
            part.dispose()
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
            failure = { error ->
                logger.error("Failed to update avatar reference: {}", error)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Avatar upload succeeded but profile update failed"))
            }
        )
    }
}
