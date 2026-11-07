package com.cprint.app.driver

import android.graphics.Bitmap
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The filter uses its Ink argument to determine the input row length. */
internal enum class EscprRasterFormat(val ink: String, val bytesPerPixel: Int) {
    RGB("COLOR", 3),
    GRAY("MONO", 1)
}

/** Converts PDF pixels in stripes to reduce Bitmap JNI calls and pipe writes. */
internal object EscprRasterWriter {
    suspend fun write(bitmap: Bitmap, output: OutputStream, format: EscprRasterFormat) {
        val stripeHeight = 32
        val pixels = IntArray(bitmap.width * stripeHeight)
        val bytes = ByteArray(pixels.size * format.bytesPerPixel)
        for (y in 0 until bitmap.height step stripeHeight) {
            currentCoroutineContext().ensureActive()
            val rows = minOf(stripeHeight, bitmap.height - y)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, rows)
            var offset = 0
            for (index in 0 until bitmap.width * rows) {
                val pixel = pixels[index]
                val red = (pixel ushr 16) and 0xff
                val green = (pixel ushr 8) and 0xff
                val blue = pixel and 0xff
                if (format == EscprRasterFormat.RGB) {
                    bytes[offset++] = red.toByte()
                    bytes[offset++] = green.toByte()
                    bytes[offset++] = blue.toByte()
                } else {
                    // The filter takes 8-bit luminance and handles printer dithering.
                    bytes[offset++] = ((299 * red + 587 * green + 114 * blue + 500) / 1000).toByte()
                }
            }
            output.write(bytes, 0, offset)
        }
    }
}
