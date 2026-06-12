package com.github.woodsmarshes.chat.core.ui.utils

import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.size - 1)
    val value = bytes.toDouble() / 1024.0.pow(digitGroups)
    val display = if (value >= 100) value.toLong().toString() else (kotlin.math.round(value * 10) / 10).toString()
    return "$display ${units[digitGroups]}"
}

fun fileExtension(fileName: String): String {
    val lastDot = fileName.lastIndexOf('.')
    return if (lastDot >= 0) fileName.substring(lastDot + 1).lowercase() else ""
}

fun mimeTypeToFileCategory(mimeType: String?): FileCategory {
    if (mimeType == null) return FileCategory.OTHER
    return when {
        mimeType.startsWith("image/") -> FileCategory.IMAGE
        mimeType.startsWith("video/") -> FileCategory.VIDEO
        mimeType.startsWith("audio/") -> FileCategory.AUDIO
        mimeType == "application/pdf" -> FileCategory.PDF
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("tar") || mimeType.contains("7z") -> FileCategory.ARCHIVE
        mimeType.contains("word") || mimeType.contains("document") -> FileCategory.DOCUMENT
        mimeType.contains("sheet") || mimeType.contains("excel") -> FileCategory.SPREADSHEET
        mimeType.contains("presentation") || mimeType.contains("powerpoint") -> FileCategory.PRESENTATION
        mimeType.startsWith("text/") -> FileCategory.TEXT
        else -> FileCategory.OTHER
    }
}

enum class FileCategory {
    IMAGE, VIDEO, AUDIO, PDF, ARCHIVE, DOCUMENT, SPREADSHEET, PRESENTATION, TEXT, OTHER
}
