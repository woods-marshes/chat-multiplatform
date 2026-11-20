package com.github.woodsmarshes.chat.core.common.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * Pure-Kotlin BlurHash decoder (port of the reference algorithm from
 * https://github.com/woltapp/blurhash). Produces packed ARGB pixels so any
 * Compose target can render the result without platform bitmap APIs.
 */
object BlurHashDecoder {
    private const val BASE83_DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    /**
     * Decodes [blurHash] into [width]x[height] pixels.
     *
     * @return IntArray of packed ARGB colors, row-major, size width * height.
     * @throws IllegalArgumentException when [blurHash] is malformed.
     */
    fun decode(
        blurHash: String,
        width: Int,
        height: Int,
        punch: Float = 1f,
    ): IntArray {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(blurHash.length >= 6) { "The blurhash string must be at least 6 characters" }

        val sizeFlag = decode83(blurHash[0])
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        require(blurHash.length == 4 + 2 * numX * numY) {
            "blurhash length mismatch: length is ${blurHash.length} but it should be ${4 + 2 * numX * numY}"
        }

        val quantisedMaximumValue = decode83(blurHash[1])
        val maximumValue = (quantisedMaximumValue + 1) / 166f * punch

        // colors[i + j * numX] holds the linear-space RGB components of each basis function
        val colors = Array(numX * numY) { FloatArray(3) }
        for (i in colors.indices) {
            colors[i] =
                if (i == 0) {
                    decodeDC(decode83(blurHash.substring(2, 6)))
                } else {
                    decodeAC(decode83(blurHash.substring(4 + i * 2, 6 + i * 2)), maximumValue)
                }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numY) {
                    val basisY = cos(PI * y * j / height)
                    for (i in 0 until numX) {
                        val basis = (cos(PI * x * i / width) * basisY).toFloat()
                        val color = colors[i + j * numX]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }
                pixels[y * width + x] =
                    (0xFF shl 24) or
                    (linearToSrgb(r) shl 16) or
                    (linearToSrgb(g) shl 8) or
                    linearToSrgb(b)
            }
        }
        return pixels
    }

    private fun decode83(str: String): Int {
        var value = 0
        for (c in str) {
            val digit = BASE83_DIGITS.indexOf(c)
            require(digit >= 0) { "Invalid character '$c' in blurhash" }
            value = value * 83 + digit
        }
        return value
    }

    private fun decode83(c: Char): Int = decode83(c.toString())

    private fun decodeDC(value: Int): FloatArray {
        val intR = value shr 16
        val intG = (value shr 8) and 0xFF
        val intB = value and 0xFF
        return floatArrayOf(srgbToLinear(intR), srgbToLinear(intG), srgbToLinear(intB))
    }

    private fun decodeAC(
        value: Int,
        maximumValue: Float,
    ): FloatArray {
        val quantR = value / (19 * 19)
        val quantG = (value / 19) % 19
        val quantB = value % 19
        return floatArrayOf(
            signPow((quantR - 9) / 9f, 2f) * maximumValue,
            signPow((quantG - 9) / 9f, 2f) * maximumValue,
            signPow((quantB - 9) / 9f, 2f) * maximumValue,
        )
    }

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb =
            if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun signPow(
        value: Float,
        exp: Float,
    ): Float = kotlin.math.sign(value) * kotlin.math.abs(value).pow(exp)
}
