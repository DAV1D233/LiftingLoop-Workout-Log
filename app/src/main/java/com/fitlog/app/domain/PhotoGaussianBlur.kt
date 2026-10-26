package com.fitlog.app.domain

import kotlin.math.exp
import kotlin.math.roundToInt

/** Separable Gaussian convolution for the small, opaque photographic backdrop only. */
object PhotoGaussianBlur {
    fun apply(pixels: IntArray, width: Int, height: Int, radius: Int = 8,
        checkCancellation: () -> Unit = {}): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        require(radius in 1..32)
        val sigma = radius / 2f
        val kernel = FloatArray(radius * 2 + 1) { i -> exp(-((i - radius) * (i - radius)) / (2f * sigma * sigma)) }
        val sum = kernel.sum()
        kernel.indices.forEach { kernel[it] /= sum }
        val red = FloatArray(pixels.size)
        val green = FloatArray(pixels.size)
        val blue = FloatArray(pixels.size)
        for (y in 0 until height) {
            if (y % 16 == 0) checkCancellation()
            for (x in 0 until width) {
                val target = y * width + x
                for (k in -radius..radius) {
                    val c = pixels[y * width + (x + k).coerceIn(0, width - 1)]
                    val weight = kernel[k + radius]
                    red[target] += (c ushr 16 and 255) * weight
                    green[target] += (c ushr 8 and 255) * weight
                    blue[target] += (c and 255) * weight
                }
            }
        }
        val output = IntArray(pixels.size)
        for (y in 0 until height) {
            if (y % 16 == 0) checkCancellation()
            for (x in 0 until width) {
                var r = 0f; var g = 0f; var b = 0f
                for (k in -radius..radius) {
                    val source = (y + k).coerceIn(0, height - 1) * width + x
                    val weight = kernel[k + radius]
                    r += red[source] * weight; g += green[source] * weight; b += blue[source] * weight
                }
                output[y * width + x] = (255 shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
            }
        }
        return output
    }
}
