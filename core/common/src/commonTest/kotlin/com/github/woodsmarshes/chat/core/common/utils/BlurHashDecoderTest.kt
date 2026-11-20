package com.github.woodsmarshes.chat.core.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlurHashDecoderTest {
    @Test
    fun decodeProducesExpectedSizeAndAlpha() {
        // "LEHV6nWB2yk8pyo0adR*.7kCMdnj" is the canonical blurhash.org example.
        val pixels = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 8, 6)
        assertEquals(8 * 6, pixels.size)
        // Every pixel must be opaque.
        assertTrue(pixels.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun singlePixelMatchesDcColor() {
        // At 1x1 only the DC component contributes; it must be a mid-tone,
        // not black or clamped.
        val pixels = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 1, 1)
        val argb = pixels[0]
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // The DC component is the image's average color; it must be a real
        // mid-tone, neither clamped to black nor blown out to white.
        for (channel in listOf(r, g, b)) {
            assertTrue(channel in 40..220, "channel out of expected range: $channel")
        }
    }

    @Test
    fun malformedInputRejected() {
        assertFailsWith<IllegalArgumentException> {
            BlurHashDecoder.decode("", 4, 4)
        }
        assertFailsWith<IllegalArgumentException> {
            BlurHashDecoder.decode("!!!!", 4, 4)
        }
    }
}
