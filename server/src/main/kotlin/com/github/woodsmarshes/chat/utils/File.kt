package com.github.woodsmarshes.chat.utils

object FileUploadConfig {
    val maxFileSize = mapOf(
        "avatar" to 5L * 1024 * 1024,     // 5MB
        "image" to 5L * 1024 * 1024,     // 5MB
        "audio" to 20L * 1024 * 1024,    // 20MB
        "video" to 100L * 1024 * 1024,   // 100MB
        "file" to 50L * 1024 * 1024      // 50MB
    )

    /**
     * Upper bound on DECODED image pixels. A small compressed file can still
     * decode into a huge in-memory buffer, so the byte cap alone does not
     * bound media processing cost.
     */
    const val maxImagePixels: Long = 20_000_000L
}