package com.cprint.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.cprint.app.domain.model.PaperSize
import com.cprint.app.domain.model.PrintSettings
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Utility class for converting print data to printer-specific formats
 */
object PrintDataConverter {

    /**
     * Convert a bitmap to PCL5 format
     */
    fun bitmapToPcl(
        bitmap: Bitmap,
        settings: PrintSettings
    ): ByteArray {
        val output = ByteArrayOutputStream()

        // PCL5 header
        output.write("\u001BE".toByteArray()) // Reset
        output.write("\u001B&l0O".toByteArray()) // Portrait orientation
        output.write("\u001B&l${getPaperSizeCode(settings.paperSize)}A".toByteArray()) // Paper size
        output.write("\u001B*r1A".toByteArray()) // Start raster graphics

        // Convert bitmap to raster data
        val rasterData = convertBitmapToRaster(bitmap)
        output.write(rasterData)

        // PCL5 footer
        output.write("\u001B*rB".toByteArray()) // End raster graphics
        output.write("\u001B&l0H".toByteArray()) // Eject page
        output.write("\u001BE".toByteArray()) // Reset

        return output.toByteArray()
    }

    /**
     * Convert a bitmap to ESC/P format (Epson)
     */
    fun bitmapToEscP(
        bitmap: Bitmap,
        settings: PrintSettings
    ): ByteArray {
        val output = ByteArrayOutputStream()

        // ESC/P header
        output.write(0x1B) // ESC
        output.write("@".toByteArray()) // Initialize

        // Set line spacing
        output.write(0x1B)
        output.write(0x33)
        output.write(24)

        // Convert bitmap to ESC/P raster data
        val width = bitmap.width
        val height = bitmap.height

        for (y in 0 until height step 24) {
            // Set absolute horizontal position
            output.write(0x1D)
            output.write(0x4C)
            output.write(0)
            output.write(0)

            // Select bit image mode
            output.write(0x1B)
            output.write(0x2A)
            output.write(33) // 24-bit double density
            output.write(width % 256)
            output.write(width / 256)

            // Write raster data
            for (x in 0 until width) {
                for (k in 0..2) {
                    var byteValue = 0
                    for (j in 0..7) {
                        val pixelY = y + k * 8 + j
                        if (pixelY < height) {
                            val pixel = bitmap.getPixel(x, pixelY)
                            val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                            if (gray < 128) {
                                byteValue = byteValue or (1 shl (7 - j))
                            }
                        }
                    }
                    output.write(byteValue)
                }
            }

            // Line feed
            output.write(0x0A)
        }

        // Cut paper
        output.write(0x1D)
        output.write(0x56)
        output.write(1)

        return output.toByteArray()
    }

    /**
     * Create PCL5 header for a page
     */
    fun createPclHeader(settings: PrintSettings): ByteArray {
        val output = ByteArrayOutputStream()

        output.write("\u001BE".toByteArray()) // Reset
        output.write("\u001B&l${if (settings.orientation.name == "LANDSCAPE") "1" else "0"}O".toByteArray()) // Orientation
        output.write("\u001B&l${getPaperSizeCode(settings.paperSize)}A".toByteArray()) // Paper size

        // Set print quality
        val qualityCode = when (settings.quality.name) {
            "DRAFT" -> "0"
            "NORMAL" -> "1"
            "HIGH" -> "2"
            "BEST" -> "3"
            else -> "1"
        }
        output.write("\u001B*o${qualityCode}M".toByteArray())

        return output.toByteArray()
    }

    /**
     * Create PCL5 footer
     */
    fun createPclFooter(): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("\u001B&l0H".toByteArray()) // Eject page
        output.write("\u001BE".toByteArray()) // Reset
        return output.toByteArray()
    }

    /**
     * Convert bitmap to raster data for PCL5
     */
    private fun convertBitmapToRaster(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        val width = bitmap.width
        val height = bitmap.height

        // Set raster resolution
        output.write("\u001B*t300R".toByteArray()) // 300 DPI

        // Set raster width
        output.write("\u001B*r${width}S".toByteArray())

        // Start raster graphics
        output.write("\u001B*r1A".toByteArray())

        // Compression mode 3 (RLE)
        output.write("\u001B*b3M".toByteArray())

        for (y in 0 until height) {
            val rowData = ByteArray((width + 7) / 8)

            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (gray < 128) {
                    rowData[x / 8] = (rowData[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }

            // Transfer raster row
            output.write("\u001B*b${rowData.size}W".toByteArray())
            output.write(rowData)
        }

        // End raster graphics
        output.write("\u001B*rB".toByteArray())

        return output.toByteArray()
    }

    /**
     * Get PCL paper size code
     */
    private fun getPaperSizeCode(paperSize: PaperSize): Int {
        return when (paperSize) {
            PaperSize.A4 -> 26
            PaperSize.A5 -> 25
            PaperSize.A3 -> 27
            PaperSize.LETTER -> 2
            PaperSize.LEGAL -> 3
            PaperSize.B5 -> 45
        }
    }

    /**
     * Scale bitmap to fit paper size
     */
    fun scaleBitmapToFit(
        bitmap: Bitmap,
        paperSize: PaperSize,
        orientation: com.cprint.app.domain.model.Orientation
    ): Bitmap {
        val targetWidth: Int
        val targetHeight: Int

        val dpi = 300 // Standard print DPI
        val widthMm = paperSize.widthMm
        val heightMm = paperSize.heightMm

        if (orientation == com.cprint.app.domain.model.Orientation.PORTRAIT) {
            targetWidth = (widthMm / 25.4 * dpi).toInt()
            targetHeight = (heightMm / 25.4 * dpi).toInt()
        } else {
            targetWidth = (heightMm / 25.4 * dpi).toInt()
            targetHeight = (widthMm / 25.4 * dpi).toInt()
        }

        // Calculate scale factor to fit within page while maintaining aspect ratio
        val scaleX = targetWidth.toFloat() / bitmap.width
        val scaleY = targetHeight.toFloat() / bitmap.height
        val scale = minOf(scaleX, scaleY, 1.0f) // Don't upscale

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Create a blank white bitmap for page background
     */
    fun createBlankPage(
        paperSize: PaperSize,
        orientation: com.cprint.app.domain.model.Orientation
    ): Bitmap {
        val dpi = 300
        val widthMm = paperSize.widthMm
        val heightMm = paperSize.heightMm

        val width: Int
        val height: Int

        if (orientation == com.cprint.app.domain.model.Orientation.PORTRAIT) {
            width = (widthMm / 25.4 * dpi).toInt()
            height = (heightMm / 25.4 * dpi).toInt()
        } else {
            width = (heightMm / 25.4 * dpi).toInt()
            height = (widthMm / 25.4 * dpi).toInt()
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        return bitmap
    }

    /**
     * Composite multiple bitmaps onto a single page (for pages per sheet)
     */
    fun compositePages(
        pages: List<Bitmap>,
        pagesPerSheet: Int,
        paperSize: PaperSize,
        orientation: com.cprint.app.domain.model.Orientation
    ): Bitmap {
        val pageBitmap = createBlankPage(paperSize, orientation)
        val canvas = Canvas(pageBitmap)

        val cols = when (pagesPerSheet) {
            1 -> 1
            2 -> 1
            4 -> 2
            6 -> 2
            9 -> 3
            16 -> 4
            else -> 1
        }

        val rows = (pagesPerSheet + cols - 1) / cols

        val cellWidth = pageBitmap.width / cols
        val cellHeight = pageBitmap.height / rows

        pages.take(pagesPerSheet).forEachIndexed { index, bitmap ->
            val col = index % cols
            val row = index / cols

            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                cellWidth - 20, // Margin
                cellHeight - 20,
                true
            )

            val x = col * cellWidth + 10
            val y = row * cellHeight + 10

            canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), null)
        }

        return pageBitmap
    }
}
