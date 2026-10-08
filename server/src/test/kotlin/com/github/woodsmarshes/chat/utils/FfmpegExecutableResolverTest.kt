package com.github.woodsmarshes.chat.utils

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FfmpegExecutableResolverTest {

    @Test
    fun blankOrNullExplicitPathUsesFallbackProvider() {
        var fallbackCalls = 0
        val resolvedNull = FfmpegExecutableResolver.resolve(
            explicitPath = null,
            fallbackProvider = {
                fallbackCalls++
                "/fallback/ffmpeg"
            },
        )
        val resolvedBlank = FfmpegExecutableResolver.resolve(
            explicitPath = "   ",
            fallbackProvider = {
                fallbackCalls++
                "/fallback/ffmpeg"
            },
        )

        assertEquals("/fallback/ffmpeg", resolvedNull)
        assertEquals("/fallback/ffmpeg", resolvedBlank)
        assertEquals(2, fallbackCalls)
    }

    @Test
    fun explicitMissingPathFailsLoudlyWithoutCallingFallback() {
        var fallbackCalled = false
        val missingPath = Files.createTempDirectory("ffmpeg-resolver-missing")
            .resolve("no-such-ffmpeg")
            .toFile()
            .also { it.parentFile.deleteOnExit() }

        val ex = assertFailsWith<IllegalStateException> {
            FfmpegExecutableResolver.resolve(
                explicitPath = missingPath.absolutePath,
                fallbackProvider = {
                    fallbackCalled = true
                    "/fallback/ffmpeg"
                },
            )
        }

        assertTrue(!fallbackCalled, "Must not fall back when an explicit path is invalid")
        assertTrue(ex.message.orEmpty().contains("does not exist"))
    }

    @Test
    fun explicitDirectoryPathFailsLoudlyWithoutCallingFallback() {
        var fallbackCalled = false
        val tempDir = Files.createTempDirectory("ffmpeg-resolver-dir").toFile().apply {
            deleteOnExit()
        }

        val ex = assertFailsWith<IllegalStateException> {
            FfmpegExecutableResolver.resolve(
                explicitPath = tempDir.absolutePath,
                fallbackProvider = {
                    fallbackCalled = true
                    "/fallback/ffmpeg"
                },
            )
        }

        assertTrue(!fallbackCalled, "Must not fall back when an explicit path is a directory")
        assertTrue(ex.message.orEmpty().contains("not a regular file"))
    }

    @Test
    fun validExplicitExecutablePathIsReturnedWithoutCallingFallback() {
        var fallbackCalled = false
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val suffix = if (isWindows) ".cmd" else ".sh"
        val tempExec = Files.createTempFile("ffmpeg-resolver-valid", suffix).toFile().apply {
            deleteOnExit()
            writeText(if (isWindows) "@echo off\r\n" else "#!/bin/sh\n")
            setExecutable(true)
        }

        val resolved = FfmpegExecutableResolver.resolve(
            explicitPath = tempExec.absolutePath,
            fallbackProvider = {
                fallbackCalled = true
                "/fallback/ffmpeg"
            },
        )

        assertTrue(!fallbackCalled)
        assertEquals(tempExec.absolutePath, resolved)
    }
}
