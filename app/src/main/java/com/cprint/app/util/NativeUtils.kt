package com.cprint.app.util

object NativeUtils {

    init {
        System.loadLibrary("cprint-native")
    }

    @JvmStatic
    external fun processBitmap(bitmap: Any, width: Int, height: Int): Any

    @JvmStatic
    external fun generatePclData(imageData: ByteArray, width: Int, height: Int): ByteArray

    @JvmStatic
    external fun generateEscPData(imageData: ByteArray, width: Int, height: Int): ByteArray

    /**
     * Generate complete ESC/P-R command stream for Epson inkjet printers.
     *
     * @param rgbData RGB byte array (3 bytes per pixel, row-major order)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param dpi Resolution (360 or 720 typical for Epson)
     * @return Complete ESC/P-R data ready to send to printer via USB bulk transfer
     */
    @JvmStatic
    external fun nativeGenerateEscprData(
        rgbData: ByteArray,
        width: Int,
        height: Int,
        dpi: Int
    ): ByteArray?

    /**
     * Generate ESC/P2 black/white raster command stream for Epson inkjet printers.
     * Fallback when ESC/P-R is not supported by the printer.
     *
     * @param rgbData RGB byte array (3 bytes per pixel, row-major order)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return Complete ESC/P2 data ready to send to printer via USB bulk transfer
     */
    @JvmStatic
    external fun nativeGenerateEscp2Data(
        rgbData: ByteArray,
        width: Int,
        height: Int
    ): ByteArray?
}
