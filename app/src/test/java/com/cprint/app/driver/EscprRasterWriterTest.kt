package com.cprint.app.driver

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class EscprRasterWriterTest {
    @Test fun `RGB channel order and final partial stripe preserve exact pixels`() = runBlocking {
        val bitmap = Bitmap.createBitmap(2, 35, Bitmap.Config.ARGB_8888)
        val expected = ByteArrayOutputStream()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(x, y, Color.rgb(y, x * 100, 255 - y))
                expected.write(byteArrayOf(y.toByte(), (x * 100).toByte(), (255 - y).toByte()))
            }
        }
        val actual = ByteArrayOutputStream()
        try {
            EscprRasterWriter.write(bitmap, actual, EscprRasterFormat.RGB)
            assertArrayEquals(expected.toByteArray(), actual.toByteArray())
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun `MONO sends one luminance byte per pixel including the final stripe`() = runBlocking {
        val bitmap = Bitmap.createBitmap(3, 33, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.GREEN)
        bitmap.setPixel(2, 0, Color.BLUE)
        bitmap.setPixel(1, 32, Color.BLACK)
        val expected = ByteArray(3 * 33) { 0xff.toByte() }.apply {
            this[0] = 76
            this[1] = 150.toByte()
            this[2] = 29
            this[32 * 3 + 1] = 0
        }
        val actual = ByteArrayOutputStream()
        try {
            EscprRasterWriter.write(bitmap, actual, EscprRasterFormat.GRAY)
            assertArrayEquals(expected, actual.toByteArray())
        } finally {
            bitmap.recycle()
        }
    }
}
