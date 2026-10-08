package com.github.woodsmarshes.chat.utils

import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File

/**
 * Resolves the ffmpeg executable path to use for managed media subprocesses.
 *
 * `DefaultFFMPEGLocator` from JAVE2 always extracts its bundled glibc binary into
 * `~/.jave2` and never consults environment variables or `PATH`. On Alpine/musl
 * containers (where system ffmpeg is installed via `apk add ffmpeg`), that
 * bundled binary fails to start or link unless an explicit path overrides it.
 *
 * Resolution rules:
 * 1. If an explicit path is configured (via argument or the `FFMPEG_EXEC_PATH`
 *    environment variable), validate that it points to an existing executable
 *    file and return its absolute path.
 * 2. If an explicit path is configured (non-blank) but invalid (missing, a
 *    directory, or not executable), fail immediately with
 *    [IllegalStateException] — never silently fall back to the bundled binary.
 * 3. If no explicit path is configured (`null` or blank), fall back to the
 *    existing development locator (`DefaultFFMPEGLocator().executablePath`).
 */
object FfmpegExecutableResolver {
    const val ENV_VAR_NAME: String = "FFMPEG_EXEC_PATH"

    /**
     * Resolves the ffmpeg binary path.
     *
     * @param explicitPath Optional explicit override; defaults to reading
     *   `System.getenv("FFMPEG_EXEC_PATH")`.
     * @param fallbackProvider Fallback used only when [explicitPath] is null or blank.
     * @throws IllegalStateException when an explicit path is provided but does
     *   not point to an executable file, or when the fallback fails.
     */
    fun resolve(
        explicitPath: String? = System.getenv(ENV_VAR_NAME),
        fallbackProvider: () -> String = { DefaultFFMPEGLocator().executablePath },
    ): String {
        val configured = explicitPath?.trim()
        if (!configured.isNullOrEmpty()) {
            val file = File(configured)
            if (!file.exists()) {
                throw IllegalStateException(
                    "Configured $ENV_VAR_NAME does not exist: '$configured'"
                )
            }
            if (!file.isFile) {
                throw IllegalStateException(
                    "Configured $ENV_VAR_NAME is not a regular file: '$configured'"
                )
            }
            if (!file.canExecute()) {
                throw IllegalStateException(
                    "Configured $ENV_VAR_NAME is not executable: '$configured'"
                )
            }
            return file.absolutePath
        }
        return try {
            fallbackProvider()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to resolve fallback ffmpeg executable", e)
        }
    }
}
