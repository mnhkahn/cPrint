package com.cprint.app.driver

import android.graphics.Bitmap
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Converts PDF pixels in stripes to reduce Bitmap JNI calls and pipe writes. */
internal object RgbBitmapWriter {
    suspend fun write(bitmap: Bitmap, output: OutputStream) {
        val stripeHeight = 32
        val pixels = IntArray(bitmap.width * stripeHeight)
        val bytes = ByteArray(pixels.size * 3)
        for (y in 0 until bitmap.height step stripeHeight) {
            currentCoroutineContext().ensureActive()
            val rows = minOf(stripeHeight, bitmap.height - y)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, rows)
            var offset = 0
            for (index in 0 until bitmap.width * rows) {
                val pixel = pixels[index]
                bytes[offset++] = (pixel shr 16).toByte()
                bytes[offset++] = (pixel shr 8).toByte()
                bytes[offset++] = pixel.toByte()
            }
            output.write(bytes, 0, offset)
        }
    }
}
