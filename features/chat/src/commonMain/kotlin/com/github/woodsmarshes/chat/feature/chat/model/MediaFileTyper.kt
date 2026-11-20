package com.github.woodsmarshes.chat.feature.chat.model

import com.github.woodsmarshes.chat.core.model.FileType

/**
 * Maps a picked file name to the server-side [FileType]. The server derives the
 * storage location and processing pipeline from this value, so the extension
 * table must stay aligned with what the media pipeline can actually handle.
 */
object MediaFileTyper {
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "avif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm", "m4v")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus")

    fun infer(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in IMAGE_EXTENSIONS -> FileType.IMAGE
            ext in VIDEO_EXTENSIONS -> FileType.VIDEO
            ext in AUDIO_EXTENSIONS -> FileType.AUDIO
            else -> FileType.FILE
        }
    }
}
