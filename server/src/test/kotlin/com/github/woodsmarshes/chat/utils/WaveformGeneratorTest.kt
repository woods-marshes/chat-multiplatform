package com.github.woodsmarshes.chat.utils

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaveformGeneratorTest {

    private class ChunkedInputStream(
        private val rawBytes: ByteArray,
        private val maxBytesPerRead: Int,
    ) : InputStream() {
        private var pos = 0

        override fun read(): Int =
            if (pos < rawBytes.size) rawBytes[pos++].toInt() and 0xff else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos >= rawBytes.size) return -1
            val toCopy = minOf(len, maxBytesPerRead, rawBytes.size - pos)
            System.arraycopy(rawBytes, pos, b, off, toCopy)
            pos += toCopy
            return toCopy
        }
    }

    @Test
    fun samplePcm16LeStreamHandlesSingleByteShortReadsCorrectly() {
        // 4 samples, 2 samples per bucket -> 2 buckets:
        // Bucket 0: [16384 (~50%), 32767 (100%)] -> max 32767 -> 100
        // Bucket 1: [-16384 (~50%), 0 (0%)]      -> max 16384 -> 50
        val samples = shortArrayOf(16384, 32767, -16384, 0)
        val rawBytes = ByteBuffer.allocate(samples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { samples.forEach { putShort(it) } }
            .array()

        val sampled = WaveformGenerator.samplePcm16LeStream(
            input = ChunkedInputStream(rawBytes, maxBytesPerRead = 1),
            samplesCount = 2,
            samplesPerBucket = 2L,
        )

        assertEquals(4L, sampled.totalSamplesRead)
        assertEquals(listOf(100, 50), sampled.waveform)
    }

    @Test
    fun samplePcm16LeStreamClampsShortMinValueAndFlushesPartialTrailingBucket() {
        // 3 samples with samplesPerBucket = 2 and samplesCount = 3:
        // Bucket 0: [Short.MIN_VALUE (-32768), 0] -> clamped to 32767 -> 100
        // Bucket 1 (partial at EOF): [8192]       -> ~25
        val samples = shortArrayOf(Short.MIN_VALUE, 0, 8192)
        val rawBytes = ByteBuffer.allocate(samples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { samples.forEach { putShort(it) } }
            .array()

        val sampled = WaveformGenerator.samplePcm16LeStream(
            input = ChunkedInputStream(rawBytes, maxBytesPerRead = 3),
            samplesCount = 3,
            samplesPerBucket = 2L,
        )

        assertEquals(3L, sampled.totalSamplesRead)
        assertEquals(listOf(100, 25), sampled.waveform)
    }

    @Test
    fun generateFailsWithLaunchFailedWhenExecutableCannotBeStarted() {
        val ex = assertFailsWith<WaveformGenerator.WaveformGenerationException> {
            WaveformGenerator.generateWithCommand(
                command = listOf("/definitely/nonexistent/ffmpeg-binary-xyz-987654"),
                durationMs = 1_000L,
                samplesCount = 10,
                watchdogTimeoutMs = 5_000L,
            )
        }

        assertEquals(WaveformGenerator.FailureReason.LAUNCH_FAILED, ex.reason)
    }

    @Test
    fun generateFailsWithLaunchFailedWhenResolverThrows() {
        val ex = assertFailsWith<WaveformGenerator.WaveformGenerationException> {
            WaveformGenerator.generate(
                audioFile = File("dummy.wav"),
                durationMs = 1_000L,
                samplesCount = 10,
                watchdogTimeoutMs = 5_000L,
                executableResolver = { throw IllegalStateException("Invalid FFMPEG_EXEC_PATH") },
            )
        }

        assertEquals(WaveformGenerator.FailureReason.LAUNCH_FAILED, ex.reason)
    }

    @Test
    fun generateFailsWithNonZeroExitInsteadOfReturningAllZeros() {
        val ex = assertFailsWith<WaveformGenerator.WaveformGenerationException> {
            WaveformGenerator.generateWithCommand(
                command = helperProcessCommand("exit-nonzero"),
                durationMs = 1_000L,
                samplesCount = 10,
                watchdogTimeoutMs = 5_000L,
            )
        }

        assertEquals(WaveformGenerator.FailureReason.NON_ZERO_EXIT, ex.reason)
    }

    @Test
    fun generateFailsWithNonZeroExitEvenWhenAllBucketsWereFilled() {
        // Even if the subprocess emitted enough PCM bytes to fill every bucket,
        // a non-zero exit code indicates a crash/decode failure and must not be
        // treated as a successful waveform.
        val ex = assertFailsWith<WaveformGenerator.WaveformGenerationException> {
            WaveformGenerator.generateWithCommand(
                command = helperProcessCommand("emit-pcm-then-fail"),
                durationMs = 1_000L,
                samplesCount = 4,
                watchdogTimeoutMs = 10_000L,
            )
        }

        assertEquals(WaveformGenerator.FailureReason.NON_ZERO_EXIT, ex.reason)
    }

    @Test
    fun generateFailsWithNoPcmDataWhenProcessExitsZeroWithoutOutput() {
        val ex = assertFailsWith<WaveformGenerator.WaveformGenerationException> {
            WaveformGenerator.generateWithCommand(
                command = helperProcessCommand("empty-ok"),
                durationMs = 1_000L,
                samplesCount = 10,
                watchdogTimeoutMs = 5_000L,
            )
        }

        assertEquals(WaveformGenerator.FailureReason.NO_PCM_DATA, ex.reason)
    }

    @Test
    fun generateFailsWithTimedOutWhenProcessHangs() {
        val ex = assertFailsWith<WaveformGenerator.WaveformGenerationException> {
            WaveformGenerator.generateWithCommand(
                command = helperProcessCommand("hang"),
                durationMs = 1_000L,
                samplesCount = 10,
                watchdogTimeoutMs = 300L,
            )
        }

        assertEquals(WaveformGenerator.FailureReason.TIMED_OUT, ex.reason)
    }

    @Test
    fun generateSucceedsOnValidPcmStreamAndDrainsTrailingOutputCleanly() {
        // Emits 9_000 samples (> 8_000 expected for 1s) so all 4 buckets fill
        // before EOF and the remaining 1_000 samples must be drained without
        // breaking the child's stdout pipe.
        val waveform = WaveformGenerator.generateWithCommand(
            command = helperProcessCommand("emit-pcm"),
            durationMs = 1_000L,
            samplesCount = 4,
            watchdogTimeoutMs = 10_000L,
        )

        assertEquals(4, waveform.size)
        assertTrue(waveform.all { it == 100 }, "Expected all 4 buckets to peak at 100, got $waveform")
    }

    companion object {
        private val pathingJar: File by lazy {
            val rawClasspath = System.getProperty("java.class.path").orEmpty()
            val entries = rawClasspath.split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .map { File(it).toURI().toURL().toString() }
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.CLASS_PATH] = entries.joinToString(" ")
            }
            Files.createTempFile("waveform-test-cp-", ".jar").toFile().apply {
                deleteOnExit()
                outputStream().use { out ->
                    JarOutputStream(out, manifest).close()
                }
            }
        }

        private fun helperProcessCommand(mode: String): List<String> {
            val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            val javaExe = if (isWindows) "bin/java.exe" else "bin/java"
            val javaBin = File(System.getProperty("java.home"), javaExe).absolutePath
            return listOf(
                javaBin,
                "-cp",
                pathingJar.absolutePath,
                PcmProcessHelper::class.java.name,
                mode,
            )
        }
    }
}

object PcmProcessHelper {
    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "exit-nonzero" -> System.exit(1)
            "empty-ok" -> System.exit(0)
            "hang" -> {
                Thread.sleep(30_000L)
            }
            "emit-pcm" -> {
                // 9000 samples (> 8000 expected for 1s) of 32767 (100% amplitude)
                val out = ByteArrayOutputStream(18_000)
                repeat(9_000) {
                    out.write(0xff)
                    out.write(0x7f)
                }
                System.out.write(out.toByteArray())
                System.out.flush()
                System.exit(0)
            }
            "emit-pcm-then-fail" -> {
                val out = ByteArrayOutputStream(18_000)
                repeat(9_000) {
                    out.write(0xff)
                    out.write(0x7f)
                }
                System.out.write(out.toByteArray())
                System.out.flush()
                System.exit(2)
            }
        }
    }
}
