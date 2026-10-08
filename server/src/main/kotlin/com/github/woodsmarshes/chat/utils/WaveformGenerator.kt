package com.github.woodsmarshes.chat.utils

import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

object WaveformGenerator {
    private const val MAX_PROCESS_DURATION_MS = 5 * 60 * 1000L
    private const val WATCHDOG_TIMEOUT_MS = 60_000L
    private const val PROCESS_EXIT_CONFIRM_MS = 5_000L
    private const val SAMPLE_RATE_HZ = 8_000
    private const val PCM_READ_CHUNK_BYTES = 8 * 1024

    enum class FailureReason {
        LAUNCH_FAILED,
        TIMED_OUT,
        NON_ZERO_EXIT,
        NO_PCM_DATA,
        INTERRUPTED,
    }

    class WaveformGenerationException(
        val reason: FailureReason,
        message: String,
        cause: Throwable? = null,
    ) : IllegalStateException(message, cause)

    private val watchdogExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ffmpeg-waveform-watchdog").apply { isDaemon = true }
    }

    fun generate(
        audioFile: File,
        durationMs: Long,
        samplesCount: Int = 100,
        watchdogTimeoutMs: Long = WATCHDOG_TIMEOUT_MS,
    ): List<Int> = generate(
        audioFile = audioFile,
        durationMs = durationMs,
        samplesCount = samplesCount,
        watchdogTimeoutMs = watchdogTimeoutMs,
        executableResolver = { FfmpegExecutableResolver.resolve() },
    )

    internal fun generate(
        audioFile: File,
        durationMs: Long,
        samplesCount: Int = 100,
        watchdogTimeoutMs: Long = WATCHDOG_TIMEOUT_MS,
        executableResolver: () -> String,
    ): List<Int> {
        val ffmpegPath = try {
            executableResolver()
        } catch (e: Exception) {
            throw WaveformGenerationException(FailureReason.LAUNCH_FAILED, "Failed to resolve ffmpeg executable", e)
        }
        val effectiveDurationMs = durationMs.coerceAtLeast(1L)
        val limitSeconds = (minOf(effectiveDurationMs, MAX_PROCESS_DURATION_MS) / 1000.0).toString()
        return generateWithCommand(
            command = listOf(
                ffmpegPath,
                "-i", audioFile.absolutePath,
                "-t", limitSeconds,
                "-ac", "1",
                "-ar", SAMPLE_RATE_HZ.toString(),
                "-f", "s16le",
                "-acodec", "pcm_s16le",
                "-"
            ),
            durationMs = effectiveDurationMs,
            samplesCount = samplesCount,
            watchdogTimeoutMs = watchdogTimeoutMs,
        )
    }

    internal fun generateWithCommand(
        command: List<String>,
        durationMs: Long,
        samplesCount: Int = 100,
        watchdogTimeoutMs: Long = WATCHDOG_TIMEOUT_MS,
    ): List<Int> {
        require(samplesCount > 0) { "samplesCount must be positive, got $samplesCount" }
        if (watchdogTimeoutMs <= 0L) {
            throw WaveformGenerationException(FailureReason.TIMED_OUT, "Waveform budget exhausted ($watchdogTimeoutMs ms)")
        }

        val process = try {
            ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            throw WaveformGenerationException(FailureReason.LAUNCH_FAILED, "Failed to start ffmpeg process", e)
        }

        val killedByWatchdog = AtomicBoolean(false)
        val watchdog = watchdogExecutor.schedule(
            {
                killedByWatchdog.set(true)
                process.destroyForcibly()
            },
            watchdogTimeoutMs,
            TimeUnit.MILLISECONDS,
        )

        val totalSeconds = minOf(durationMs.coerceAtLeast(1L), MAX_PROCESS_DURATION_MS) / 1000.0
        val totalExpectedSamples = (totalSeconds * SAMPLE_RATE_HZ).toLong().coerceAtLeast(1L)
        val samplesPerBucket = (totalExpectedSamples / samplesCount).coerceAtLeast(1L)

        try {
            val sampled = try {
                samplePcm16LeStream(process.inputStream, samplesCount, samplesPerBucket)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                confirmProcessExit(process)
                throw WaveformGenerationException(FailureReason.INTERRUPTED, "Interrupted while reading PCM stream", e)
            } catch (e: Exception) {
                confirmProcessExit(process)
                val reason = if (killedByWatchdog.get()) FailureReason.TIMED_OUT else FailureReason.NO_PCM_DATA
                throw WaveformGenerationException(reason, "Failed while reading PCM stream", e)
            }

            if (killedByWatchdog.get()) {
                confirmProcessExit(process)
                throw WaveformGenerationException(FailureReason.TIMED_OUT, "Waveform timed out after ${watchdogTimeoutMs}ms")
            }

            val exited = try {
                process.waitFor(watchdogTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                confirmProcessExit(process)
                throw WaveformGenerationException(FailureReason.INTERRUPTED, "Interrupted waiting for ffmpeg exit", e)
            }

            if (killedByWatchdog.get() || !exited) {
                confirmProcessExit(process)
                throw WaveformGenerationException(FailureReason.TIMED_OUT, "ffmpeg did not exit within ${watchdogTimeoutMs}ms")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw WaveformGenerationException(FailureReason.NON_ZERO_EXIT, "ffmpeg exited with code $exitCode")
            }
            if (sampled.totalSamplesRead == 0L) {
                throw WaveformGenerationException(FailureReason.NO_PCM_DATA, "ffmpeg produced 0 PCM samples")
            }

            val result = sampled.waveform.toMutableList()
            while (result.size < samplesCount) result.add(0)
            return result
        } finally {
            watchdog.cancel(false)
            if (process.isAlive) confirmProcessExit(process) else process.destroy()
        }
    }

    private fun confirmProcessExit(process: Process) {
        process.destroyForcibly()
        try {
            process.waitFor(PROCESS_EXIT_CONFIRM_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    internal data class SampledPcm(val waveform: List<Int>, val totalSamplesRead: Long)

    internal fun samplePcm16LeStream(
        input: InputStream,
        samplesCount: Int,
        samplesPerBucket: Long,
    ): SampledPcm {
        val effectiveSamplesPerBucket = samplesPerBucket.coerceAtLeast(1L)
        val waveform = ArrayList<Int>(samplesCount)
        val chunk = ByteArray(PCM_READ_CHUNK_BYTES)

        var pendingLowByte = -1
        var currentBucketMax = 0
        var samplesInCurrentBucket = 0L
        var totalSamplesRead = 0L

        fun toPercent(maxAmp: Int): Int =
            (maxAmp.toDouble() / 32767.0 * 100.0).toInt().coerceIn(0, 100)

        while (true) {
            val bytesRead = input.read(chunk)
            if (bytesRead < 0) break
            for (i in 0 until bytesRead) {
                val b = chunk[i].toInt() and 0xff
                if (pendingLowByte == -1) {
                    pendingLowByte = b
                } else {
                    val rawShort = ((b shl 8) or pendingLowByte).toShort().toInt()
                    pendingLowByte = -1
                    totalSamplesRead++
                    if (waveform.size < samplesCount) {
                        val amp = abs(rawShort).coerceAtMost(32767)
                        if (amp > currentBucketMax) currentBucketMax = amp
                        samplesInCurrentBucket++
                        if (samplesInCurrentBucket >= effectiveSamplesPerBucket) {
                            waveform.add(toPercent(currentBucketMax))
                            currentBucketMax = 0
                            samplesInCurrentBucket = 0L
                        }
                    }
                }
            }
        }

        if (samplesInCurrentBucket > 0L && waveform.size < samplesCount) {
            waveform.add(toPercent(currentBucketMax))
        }
        return SampledPcm(waveform, totalSamplesRead)
    }
}
