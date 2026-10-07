package com.github.woodsmarshes.chat.utils

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The probe parses ffmpeg's own `ffmpeg -i` report (written to stderr).
 * These tests pin the parsing against real report shapes, including the hex
 * stream ids that must not be mistaken for video dimensions, plus the
 * bounded-read contract that guards against runaway process output.
 */
class MediaProbeTest {

    @Test
    fun parsesDurationAndDimensionsFromAVideoReport() {
        val report = """
            Input #0, mov,mp4,m4a,3gp,3g2,mj2, from '/uploads/video/x.mp4':
              Metadata:
                major_brand     : isom
                minor_version   : 512
                compatible_brands: isomiso2avc1mp41
                encoder         : Lavf59.27.100
              Duration: 00:00:05.07, start: 0.000000, bitrate: 1243 kb/s
              Stream #0:0[0x1](und): Video: h264 (High) (avc1 / 0x31637661), yuv420p(tv, bt709), 1920x1080 [SAR 1:1 DAR 16:9], 1024 kb/s, 30 fps, 30 tbr, 15360 tbn (default)
              Stream #0:1[0x2](und): Audio: aac (LC) (mp4a / 0x63700061), 44100 Hz, stereo, fltp, 128 kb/s (default)
            At least one output file must be specified
        """.trimIndent()

        val probe = MediaProbe.parseReport(report)

        assertNotNull(probe)
        assertEquals(5070, probe.durationMs)
        assertEquals(1920, probe.width)
        assertEquals(1080, probe.height)
    }

    @Test
    fun parsesAudioOnlyReportsWithoutDimensions() {
        val report = """
            Input #0, aac, from '/uploads/audio/x.aac':
              Duration: 00:01:02.34, start: 0.023056, bitrate: 63 kb/s
              Stream #0:0: Audio: aac (LC), 44100 Hz, mono, fltp, 62 kb/s
            At least one output file must be specified
        """.trimIndent()

        val probe = MediaProbe.parseReport(report)

        assertNotNull(probe)
        assertEquals(62340, probe.durationMs)
        assertNull(probe.width)
        assertNull(probe.height)
    }

    @Test
    fun reportsWithoutADurationLineAreNotParseable() {
        assertNull(MediaProbe.parseReport("Invalid data found when processing input"))
    }

    @Test
    fun boundedReadCollectsEverythingWhenTheSourceEndsInTime() {
        val data = ByteArray(1000) { 'a'.code.toByte() }

        val read = MediaProbe.readBounded(ByteArrayInputStream(data), maxBytes = 10_000)

        assertNotNull(read)
        assertEquals(1000, read.size)
    }

    @Test
    fun boundedReadRejectsSourcesBeyondTheCapInsteadOfBlockingForever() {
        val endlessSource = object : InputStream() {
            override fun read(): Int = 'x'.code
        }

        // Must return promptly once the cap is exceeded (the caller then
        // kills the producing process), never block collecting to EOF.
        assertNull(MediaProbe.readBounded(endlessSource, maxBytes = 1_000))
    }
}
