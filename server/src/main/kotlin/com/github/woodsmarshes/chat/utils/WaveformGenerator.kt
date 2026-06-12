package com.github.woodsmarshes.chat.utils

import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object WaveformGenerator {
    private const val MAX_PROCESS_DURATION_MS = 5 * 60 * 1000L

    fun generate(audioFile: File, durationMs: Long, samplesCount: Int = 100): List<Int> {
        val ffmpegPath = DefaultFFMPEGLocator().executablePath

        val limitSeconds = (minOf(durationMs, MAX_PROCESS_DURATION_MS) / 1000.0).toString()

        // 使用 FFmpeg 提取 8bit 采样，单声道，采样率 8000Hz 足够计算波形
        val pb = ProcessBuilder(
            ffmpegPath,
            "-i", audioFile.absolutePath,
            "-t", limitSeconds,
            "-ac", "1",            // 单声道
            "-ar", "8000",         // 8000Hz 采样率
            "-f", "s16le",         // 16bit 小端 PCM 格式
            "-acodec", "pcm_s16le",
            "-"                    // 输出到 stdout (pipe)
        )

        val process = pb.start()
        val waveform = mutableListOf<Int>()

        process.inputStream.use { input ->
            val totalSeconds = minOf(durationMs, MAX_PROCESS_DURATION_MS) / 1000.0
            val totalExpectedSamples = (totalSeconds * 8000).toLong()
            val samplesPerBucket = (totalExpectedSamples / samplesCount).coerceAtLeast(1L)

            val buffer = ByteArray(2) // 每次读一个 16bit 采样
            var currentBucketMax = 0
            var samplesInCurrentBucket = 0L
            var bucketsProcessed = 0

            while (input.read(buffer) == 2 && bucketsProcessed < samplesCount) {
                // 将字节转为小端序的 Short 振幅
                val amplitude = abs(((buffer[1].toInt() shl 8) or (buffer[0].toInt() and 0xff)).toShort().toInt())
                if (amplitude > currentBucketMax) currentBucketMax = amplitude

                samplesInCurrentBucket++

                if (samplesInCurrentBucket >= samplesPerBucket) {
                    waveform.add((currentBucketMax.toDouble() / 32767.0 * 100).toInt())
                    currentBucketMax = 0
                    samplesInCurrentBucket = 0
                    bucketsProcessed++
                }
            }
        }

        process.destroy()

        while (waveform.size < samplesCount) waveform.add(0)
        return waveform
    }
}