package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.model.FileType
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.network.api.V1
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlin.uuid.Uuid

class FileApi(
    private val client: HttpClient,
) {
    suspend fun uploadFile(
        source: Source,
        fileName: String,
        fileType: FileType,
        onProgress: suspend (bytesSent: Long, total: Long?) -> Unit
    ): MediaContent {
        return client.post(V1.Files.Upload(type = fileType)) {
            // 设置多部分表单内容
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            InputProvider {
                                source.buffered()
                            },
                            Headers.build {
                                append(HttpHeaders.ContentType, fileType.toContentType().toString())
                                // 必须设置 filename 字段，否则服务端 part.originalFileName 为 null
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }
                )
            )

            onUpload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength)
            }
        }.body()
    }

    suspend fun uploadAvatar(
        source: Source,
        isGroup: Boolean,
        targetId: Uuid? = null,
        onProgress: suspend (bytesSent: Long, total: Long?) -> Unit
    ): String {
        val response = client.post(V1.Files.Avatar(isGroup = isGroup, targetId = targetId)) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            InputProvider {
                            source.buffered()
                            },
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                            }
                        )
                    }
                )
            )
            onUpload { bytesSent, total ->
                onProgress(bytesSent, total)
            }
        }
        return response.body<Map<String, String>>()["url"] ?: ""
    }

}

internal fun FileType.toContentType(): ContentType = when (this) {
    FileType.IMAGE -> ContentType.Image.JPEG
    FileType.AVATAR -> ContentType.Image.JPEG
    FileType.AUDIO -> ContentType.Audio.MPEG
    FileType.VIDEO -> ContentType.Video.MP4
    FileType.FILE -> ContentType.Application.OctetStream
}