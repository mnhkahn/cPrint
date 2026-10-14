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
class RgbBitmapWriterTest {
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
            RgbBitmapWriter.write(bitmap, actual)
            assertArrayEquals(expected.toByteArray(), actual.toByteArray())
        } finally {
            bitmap.recycle()
        }
    }
}
