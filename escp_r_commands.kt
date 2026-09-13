/**
 * ESC/P-R 指令生成代码 - 用于 Epson L3118 喷墨打印机
 *
 * 问题：打印机接收到数据但无打印输出
 * 当前实现：ESC ( S 命令包含 8 字节参数头 + 像素数据
 */

private fun convertBitmapToEscpRaster(bitmap: Bitmap): ByteArray {
    val output = ByteArrayOutputStream()
    val width = bitmap.width
    val height = bitmap.height

    // Scale to max 576 pixels (8 inches @ 72 DPI)
    val maxWidth = 576
    val scaleFactor = if (width > maxWidth) maxWidth.toFloat() / width else 1.0f
    val finalWidth = (width * scaleFactor).toInt()
    val finalHeight = (height * scaleFactor).toInt()

    val scaledBitmap = if (scaleFactor < 1.0f) {
        Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    } else {
        bitmap
    }

    val pixels = IntArray(finalWidth * finalHeight)
    scaledBitmap.getPixels(pixels, 0, finalWidth, 0, 0, finalWidth, finalHeight)

    // Calculate bytes per line (8 dots per byte)
    val bytesPerLine = (finalWidth + 7) / 8

    // ===== Header Commands =====

    // 1. Initialize printer
    // HEX: 1B 40
    output.write(byteArrayOf(0x1B, 0x40)) // ESC @

    // 2. Enter Remote Mode
    // HEX: 1B 28 52 02 00 01 01
    // Format: ESC ( R <param_len=2> <mode=1> <submode=1>
    output.write(byteArrayOf(0x1B, 0x28, 0x52, 0x02, 0x00, 0x01, 0x01))

    // 3. Set raster resolution to 360 DPI
    // HEX: 1B 28 44 02 00 01 00
    // Format: ESC ( D <param_len=2> <unit=1>
    output.write(byteArrayOf(0x1B, 0x28, 0x44, 0x02, 0x00, 0x01, 0x00))

    // 4. Set graphics mode
    // HEX: 1B 28 47 01 00 01
    // Format: ESC ( G <param_len=1> <mode=1>
    output.write(byteArrayOf(0x1B, 0x28, 0x47, 0x01, 0x00, 0x01))

    // 5. Start raster graphics
    // HEX: 1B 28 41 01 00 01
    // Format: ESC ( A <param_len=1> <start=1>
    output.write(byteArrayOf(0x1B, 0x28, 0x41, 0x01, 0x00, 0x01))

    // ===== Raster Data (ESC ( S) =====
    for (y in 0 until finalHeight) {
        val rowOffset = y * finalWidth

        // Check if row has content
        var hasContent = false
        for (x in 0 until finalWidth) {
            val pixel = pixels[rowOffset + x]
            val gray = (((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)) / 3
            if (gray < 200) {
                hasContent = true
                break
            }
        }

        if (!hasContent) {
            // Skip white line: ESC ( S with 0 length
            // HEX: 1B 28 53 00 00
            output.write(byteArrayOf(0x1B, 0x28, 0x53, 0x00, 0x00))
            continue
        }

        // ESC ( S command with 8-byte header
        // Format: ESC ( S <total_len_lo> <total_len_hi> <8-byte-header> <pixel-data>

        val headerSize = 8
        val dataSize = bytesPerLine
        val totalLength = headerSize + dataSize

        output.write(0x1B) // ESC
        output.write(0x28) // (
        output.write(0x53) // S
        output.write(totalLength and 0xFF)          // total_len_lo
        output.write((totalLength shr 8) and 0xFF)  // total_len_hi

        // 8-byte header (little endian):
        // Byte 0-1: Raster width in dots
        // Byte 2-3: Raster height (always 1 for single line)
        // Byte 4-5: Offset from left margin
        // Byte 6-7: Reserved
        output.write(finalWidth and 0xFF)           // width_lo
        output.write((finalWidth shr 8) and 0xFF)   // width_hi
        output.write(0x01)                          // height_lo
        output.write(0x00)                          // height_hi
        output.write(0x00)                          // offset_lo
        output.write(0x00)                          // offset_hi
        output.write(0x00)                          // reserved1
        output.write(0x00)                          // reserved2

        // Pixel data (1 bit per pixel, MSB first)
        for (x in 0 until finalWidth step 8) {
            var byteVal = 0
            for (bit in 0 until 8) {
                val px = x + bit
                if (px < finalWidth) {
                    val pixel = pixels[rowOffset + px]
                    val gray = (((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)) / 3
                    if (gray < 128) {
                        byteVal = byteVal or (0x80 ushr bit)  // MSB first
                    }
                }
            }
            output.write(byteVal)
        }
    }

    // ===== Footer Commands =====

    // 6. End raster graphics
    // HEX: 1B 28 41 01 00 00
    // Format: ESC ( A <param_len=1> <end=0>
    output.write(byteArrayOf(0x1B, 0x28, 0x41, 0x01, 0x00, 0x00))

    // 7. Exit Remote Mode
    // HEX: 1B 28 52 02 00 00 00
    // Format: ESC ( R <param_len=2> <mode=0> <submode=0>
    output.write(byteArrayOf(0x1B, 0x28, 0x52, 0x02, 0x00, 0x00, 0x00))

    // 8. Form feed
    // HEX: 0C
    output.write(0x0C) // FF

    // 9. Initialize (reset)
    // HEX: 1B 40
    output.write(byteArrayOf(0x1B, 0x40))

    return output.toByteArray()
}

/**
 * 已知问题：
 * 1. 打印机接收到数据（bulkTransfer 成功返回）
 * 2. 但打印机无任何反应（不打印、不走纸）
 * 3. 可能原因：
 *    - ESC ( S 命令的 8 字节参数格式不正确
 *    - 分辨率设置问题（360 DPI 可能不被 L3118 支持）
 *    - 需要先设置纸张大小
 *    - Remote Mode 进入方式不对
 */
