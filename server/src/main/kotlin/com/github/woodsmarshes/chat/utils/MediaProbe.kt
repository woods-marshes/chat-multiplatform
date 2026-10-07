package com.github.woodsmarshes.chat.utils

import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Container metadata (duration, video dimensions) parsed from ffmpeg's own
 * input report. Running the probe as a managed process replaces jave's
 * `MultimediaObject.info`, whose internal process has no handle and no
 * timeout — a hung probe could hold a request and a processing slot forever.
 */
object MediaProbe {
    data class Result(val durationMs: Long, val width: Int?, val height: Int?)

    enum class FailureReason { TIMED_OUT, OUTPUT_EXCEEDED, UNPARSEABLE, INTERRUPTED }

    sealed interface Outcome {
        data class Success(val result: Result) : Outcome
        data class Failure(val reason: FailureReason) : Outcome
    }

    /** Hard wall-clock limit before a hung ffmpeg probe is killed. */
    private const val PROBE_TIMEOUT_MS = 10_000L

    /**
     * Cap on the collected metadata report. ffmpeg reports are a few KB; the
     * input media is attacker-controlled, so the read is bounded and the
     * process killed when the cap trips — the watchdog bounds time, not bytes.
     */
    internal const val MAX_REPORT_BYTES = 256 * 1024

    // Serializes the tiny watchdog tasks that kill probes past their deadline.
    private val watchdogExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ffmpeg-probe-watchdog").apply { isDaemon = true }
    }

    /**
     * Probes [file] under a hard wall-clock limit: a hung ffmpeg is destroyed
     * forcibly, which closes the pipe and unwinds the blocked read.
     */
    fun probe(file: File): Outcome = probe(file, PROBE_TIMEOUT_MS)

    fun probe(file: File, timeoutMs: Long): Outcome {
        val process = ProcessBuilder(
            DefaultFFMPEGLocator().executablePath,
            "-hide_banner",
            "-i", file.absolutePath,
        )
            // No output file is passed, so ffmpeg exits non-zero — but only
            // after dumping the container metadata to stderr. stdout is
            // discarded; stderr carries the (bounded) report.
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val killedByWatchdog = AtomicBoolean(false)
        return try {
            val watchdog = watchdogExecutor.schedule(
                {
                    killedByWatchdog.set(true)
                    process.destroyForcibly()
                },
                timeoutMs,
                TimeUnit.MILLISECONDS,
            )
            try {
                val report = readBounded(process.errorStream, MAX_REPORT_BYTES)
                when {
                    // The cap tripped mid-read: the process would block on a
                    // full stderr pipe forever, so kill it before unwinding.
                    report == null -> {
                        process.destroyForcibly()
                        process.waitFor(5, TimeUnit.SECONDS)
                        Outcome.Failure(FailureReason.OUTPUT_EXCEEDED)
                    }
                    killedByWatchdog.get() -> Outcome.Failure(FailureReason.TIMED_OUT)
                    else -> {
                        // The report may be complete while the process is not
                        // done (it closes stderr before exiting). Confirm the
                        // exit: a wait that ran out, or a watchdog kill during
                        // the wait, must not yield Success even when the
                        // collected report still parses. Exit code is
                        // deliberately not checked — `ffmpeg -i` without an
                        // output file exits non-zero by design.
                        val exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                        when {
                            killedByWatchdog.get() || !exited -> {
                                process.destroyForcibly()
                                Outcome.Failure(FailureReason.TIMED_OUT)
                            }
                            else -> parseReport(report.decodeToString())
                                ?.let { Outcome.Success(it) }
                                ?: Outcome.Failure(FailureReason.UNPARSEABLE)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                process.destroyForcibly()
                Outcome.Failure(FailureReason.INTERRUPTED)
            } finally {
                watchdog.cancel(false)
            }
        } finally {
            process.destroy()
        }
    }

    /**
     * Reads [stream] to EOF, collecting at most [maxBytes]. Returns null as
     * soon as the source exceeds the cap — the caller must terminate the
     * producing process, or a full pipe will deadlock it.
     */
    internal fun readBounded(stream: InputStream, maxBytes: Int): ByteArray? {
        val collected = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(chunk)
            if (n < 0) return collected.toByteArray()
            total += n
            if (total > maxBytes) return null
            collected.write(chunk, 0, n)
        }
    }

    /** Parses an `ffmpeg -i` metadata report; null when no duration was found. */
    fun parseReport(report: String): Result? {
        val durationMs = Regex("Duration:\\s*(\\d+):(\\d+):(\\d+)\\.(\\d+)")
            .find(report)
            ?.destructured
            ?.let { (h, m, s, fraction) ->
                h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1_000 +
                        fraction.padEnd(3, '0').take(3).toLong()
            }
            ?: return null
        // Dimensions live on the first Video: stream line. Hex stream ids
        // like "0x31637661" cannot match: the digit group before 'x' needs
        // at least two digits.
        val videoLine = report.lineSequence().firstOrNull { "Video:" in it }
        val size = videoLine?.let { Regex("(\\d{2,5})x(\\d{2,5})").find(it) }
        return Result(
            durationMs = durationMs,
            width = size?.groupValues?.get(1)?.toInt(),
            height = size?.groupValues?.get(2)?.toInt(),
        )
    }
}
