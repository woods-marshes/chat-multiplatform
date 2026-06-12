package com.github.woodsmarshes.chat.utils

import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

// https://github.com/hsch/blurhash-java
object BlurHashEncoder {
    private val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~".toCharArray()

    fun encode(image: BufferedImage, componentX: Int = 4, componentY: Int = 4): String {
        val width = image.width
        val height = image.height
        val pixels = image.getRGB(0, 0, width, height, null, 0, width)

        if (componentX !in 1..9 || componentY < 1 || componentY > 9) {
            throw IllegalArgumentException("Components must be between 1 and 9")
        }

        val factors = Array(componentX * componentY) { DoubleArray(3) }
        for (j in 0 until componentY) {
            for (i in 0 until componentX) {
                val normalisation = if (i == 0 && j == 0) 1.0 else 2.0
                applyBasisFunction(pixels, width, height, normalisation, i, j, factors, j * componentX + i)
            }
        }

        val hash = CharArray(1 + 1 + 4 + 2 * (factors.size - 1))
        encodeBase83((componentX - 1 + (componentY - 1) * 9).toLong(), 1, hash, 0)

        val maximumValue: Double
        if (factors.size > 1) {
            val actualMaximumValue = factors.sliceArray(1 until factors.size).flatMap { it.asIterable() }.maxOrNull() ?: 0.0
            val quantisedMaximumValue = floor(max(0.0, min(82.0, floor(actualMaximumValue * 166 - 0.5))))
            maximumValue = (quantisedMaximumValue + 1) / 166
            encodeBase83(quantisedMaximumValue.toLong(), 1, hash, 1)
        } else {
            maximumValue = 1.0
            encodeBase83(0, 1, hash, 1)
        }

        val dc = factors[0]
        encodeBase83(encodeDC(dc), 4, hash, 2)

        for (i in 1 until factors.size) {
            encodeBase83(encodeAC(factors[i], maximumValue), 2, hash, 6 + 2 * (i - 1))
        }
        return String(hash)
    }

    private fun applyBasisFunction(pixels: IntArray, width: Int, height: Int, norm: Double, i: Int, j: Int, factors: Array<DoubleArray>, index: Int) {
        var r = 0.0; var g = 0.0; var b = 0.0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val basis = norm * cos(PI * i * x / width) * cos(PI * j * y / height)
                val pixel = pixels[y * width + x]
                r += basis * sRGBToLinear((pixel shr 16) and 0xff)
                g += basis * sRGBToLinear((pixel shr 8) and 0xff)
                b += basis * sRGBToLinear(pixel and 0xff)
            }
        }
        val scale = 1.0 / (width * height)
        factors[index][0] = r * scale
        factors[index][1] = g * scale
        factors[index][2] = b * scale
    }

    private fun encodeDC(value: DoubleArray): Long {
        val r = linearTosRGB(value[0])
        val g = linearTosRGB(value[1])
        val b = linearTosRGB(value[2])
        return (r shl 16) + (g shl 8) + b
    }

    private fun encodeAC(value: DoubleArray, maxVal: Double): Long {
        val r = floor(max(0.0, min(18.0, floor(signSqrt(value[0] / maxVal) * 9 + 9.5))))
        val g = floor(max(0.0, min(18.0, floor(signSqrt(value[0] / maxVal) * 9 + 9.5))))
        val b = floor(max(0.0, min(18.0, floor(signSqrt(value[0] / maxVal) * 9 + 9.5))))
        return (r * 19 * 19 + g * 19 + b).toLong()
    }

    private fun encodeBase83(value: Long, length: Int, buffer: CharArray, offset: Int) {
        var exp = 1L
        for (i in 1..length) {
            val digit = (value / exp % 83).toInt()
            buffer[offset + length - i] = ALPHABET[digit]
            exp *= 83
        }
    }

    private fun sRGBToLinear(v: Int): Double {
        val f = v / 255.0
        return if (f <= 0.04045) f / 12.92 else ((f + 0.055) / 1.055).pow(2.4)
    }

    private fun linearTosRGB(v: Double): Long {
        val f = v.coerceIn(0.0, 1.0)
        return if (f <= 0.0031308) (f * 12.92 * 255 + 0.5).toLong()
        else ((1.055 * f.pow(1 / 2.4) - 0.055) * 255 + 0.5).toLong()
    }

    private fun signSqrt(v: Double): Double {
        return v.sign * kotlin.math.sqrt(v.absoluteValue)
    }
}