package com.cprint.app.driver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cprint.app.domain.model.ColorMode
import com.cprint.app.domain.model.DuplexMode
import com.cprint.app.domain.model.PrintQuality
import com.cprint.app.domain.model.PrintSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/**
 * End-to-end pipeline test: PDF -> bitmap -> native driver (raw pixel stream
 * protocol) -> printer-language bytes captured to a file.
 */
object PrintDriverEngine {

    /** Output from the real escpr filter, ready to be written to the USB bulk endpoint. */
    data class DriverOutput(
        val bytes: ByteArray,
        val stderrLog: String,
        val exitCode: Int
    )

    data class TestResult(
        val success: Boolean,
        val message: String,
        val outFile: File? = null,
        val outBytes: Long = 0,
        val headHex: String = "",
        val stderrLog: String = "",
        val exitCode: Int = -1
    )

    private const val DPI = 300
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842
    private const val PRINTER_MODEL = "L3118"
    private const val MAX_DRIVER_OUTPUT_BYTES = 64 * 1024 * 1024

    /**
     * Runs the downloaded escpr filter for a user PDF.  The filter's stdout is
     * the only byte stream that may be sent to an Epson USB printer; stderr and
     * the process exit code are retained so a failed filter is never reported
     * as a successful print merely because USB accepted some bytes.
     */
    suspend fun renderPdfForUsb(
        context: Context,
        documentUri: String,
        pageIndexes: List<Int>,
        settings: PrintSettings
    ): DriverOutput = withContext(Dispatchers.IO) {
        require(pageIndexes.isNotEmpty()) { "没有可打印的页面" }

        val dpi = DPI
        val widthPx = (settings.paperSize.widthMm / 25.4f * dpi).toInt()
        val heightPx = (settings.paperSize.heightMm / 25.4f * dpi).toInt()
        val (pageWidth, pageHeight) = if (settings.orientation.name == "LANDSCAPE") {
            heightPx to widthPx
        } else {
            widthPx to heightPx
        }
        val so = DriverPackager.ensureInstalled(context, DriverPackager.DRV_ESCPR)
        val argv = arrayOf(
            so.absolutePath,
            PRINTER_MODEL,
            pageWidth.toString(),
            pageHeight.toString(),
            dpi.toString(),
            if (settings.colorMode == ColorMode.COLOR) "COLOR" else "MONO",
            settings.paperSize.value,
            mediaQuality(settings.quality),
            duplexValue(settings.duplexMode),
            "Auto",
            "0", "0", "0"
        )
        val session = NativeDriver.start(so, argv)
        val output = ByteArrayOutputStream()
        val stderr = StringBuilder()
        var readerFailure: Throwable? = null

        val stdoutThread = Thread {
            try {
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = session.stdout.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_DRIVER_OUTPUT_BYTES) {
                        throw IllegalStateException("驱动输出超过 ${MAX_DRIVER_OUTPUT_BYTES / 1024 / 1024}MB")
                    }
                    output.write(buffer, 0, count)
                }
            } catch (e: Throwable) {
                readerFailure = e
            }
        }.apply { name = "escpr-stdout"; start() }
        val stderrThread = Thread {
            try {
                val buffer = ByteArray(4096)
                while (stderr.length < 32 * 1024) {
                    val count = session.stderr.read(buffer)
                    if (count < 0) break
                    stderr.append(String(buffer, 0, count, Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // The driver exit status remains authoritative if stderr closes early.
            }
        }.apply { name = "escpr-stderr"; start() }

        try {
            context.contentResolver.openFileDescriptor(Uri.parse(documentUri), "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(pageIndexes.all { it in 0 until renderer.pageCount }) { "PDF 页码无效" }
                    session.stdin.write(byteArrayOf(pageIndexes.size.toByte()))
                    val rowPixels = IntArray(pageWidth)
                    val rowBytes = ByteArray(pageWidth * 3)
                    pageIndexes.forEachIndexed { outputIndex, pageIndex ->
                        renderer.openPage(pageIndex).use { page ->
                            val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                val matrix = android.graphics.Matrix().apply {
                                    setScale(pageWidth.toFloat() / page.width, pageHeight.toFloat() / page.height)
                                }
                                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                session.stdin.write(byteArrayOf(outputIndex.toByte()))
                                for (y in 0 until pageHeight) {
                                    bitmap.getPixels(rowPixels, 0, pageWidth, 0, y, pageWidth, 1)
                                    var offset = 0
                                    for (pixel in rowPixels) {
                                        rowBytes[offset++] = ((pixel shr 16) and 0xff).toByte()
                                        rowBytes[offset++] = ((pixel shr 8) and 0xff).toByte()
                                        rowBytes[offset++] = (pixel and 0xff).toByte()
                                    }
                                    session.stdin.write(rowBytes)
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    session.stdin.flush()
                }
            } ?: throw IllegalStateException("无法打开待打印 PDF")
            session.closeStdin()

            val exitCode = session.waitFor()
            stdoutThread.join(10_000)
            stderrThread.join(5_000)
            readerFailure?.let { throw IllegalStateException("读取驱动输出失败: ${it.message}", it) }
            if (exitCode != 0) {
                throw IllegalStateException("escpr 驱动退出码 $exitCode: ${stderr.toString().trim()}")
            }
            if (output.size() == 0) {
                throw IllegalStateException("escpr 驱动没有生成打印数据: ${stderr.toString().trim()}")
            }
            Timber.d("Engine: escpr produced ${output.size()} bytes for ${pageIndexes.size} page(s)")
            DriverOutput(output.toByteArray(), stderr.toString().trim(), exitCode)
        } finally {
            try { session.closeStdin() } catch (_: Exception) {}
            session.destroy()
        }
    }

    private fun mediaQuality(quality: PrintQuality): String = when (quality) {
        PrintQuality.DRAFT -> "PLAIN_DRAFT"
        PrintQuality.NORMAL -> "PLAIN_NORMAL"
        PrintQuality.HIGH, PrintQuality.BEST -> "PLAIN_HIGH"
    }

    private fun duplexValue(duplex: DuplexMode): String = when (duplex) {
        DuplexMode.SINGLE -> "None"
        DuplexMode.LONG_EDGE -> "DuplexNoTumble"
        DuplexMode.SHORT_EDGE -> "DuplexTumble"
    }

    suspend fun runEscprPipelineTest(context: Context): TestResult = withContext(Dispatchers.IO) {
        try {
            // 1. Install driver
            val so = DriverPackager.ensureInstalled(context, DriverPackager.DRV_ESCPR)

            // 2. Built-in one-page test PDF (no user file needed)
            val pdfFile = makeTestPdf(context)

            // 3. Render to bitmaps at 300 DPI
            val widthPx = A4_WIDTH_PT * DPI / 72   // 2479
            val heightPx = A4_HEIGHT_PT * DPI / 72 // 3508
            val bitmaps = renderPdf(pdfFile, widthPx, heightPx)
            Timber.d("Engine: rendered ${bitmaps.size} page(s) at ${widthPx}x${heightPx}")

            // 4. Run the driver
            // argv layout (matches escpr filter.c main): model, width_px,
            // height_px, HWResolution(dpi), Ink, PageSize, MediaType_Quality,
            // Duplex, InputSlot, Brightness, Contrast, Saturation
            val argv = arrayOf(
                so.absolutePath,
                PRINTER_MODEL,         // model id in the escpr table (no "Epson " prefix)
                widthPx.toString(),
                heightPx.toString(),
                DPI.toString(),       // must be one of 300/360/600/720
                "COLOR",
                "A4",
                "PLAIN_NORMAL",       // mediaType_printQuality
                "None",               // duplex
                "Auto",               // input slot
                "0", "0", "0"
            )
            val session = NativeDriver.start(so, argv)

            val outFile = File(context.getExternalFilesDir(null), "escpr_test_output.bin")
            val stderrLog = StringBuilder()

            val stdoutThread = Thread {
                try {
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = session.stdout.read(buf)
                            if (n < 0) break
                            fos.write(buf, 0, n)
                            total += n
                            if (total % (1024 * 1024) < 64 * 1024) {
                                Timber.d("Engine: stdout read $total bytes")
                            }
                        }
                        Timber.d("Engine: stdout EOF, total=$total")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "stdout reader ended with error")
                }
            }.apply { start() }

            val stderrThread = Thread {
                try {
                    val buf = ByteArray(4096)
                    while (stderrLog.length < 8192) {
                        val n = session.stderr.read(buf)
                        if (n < 0) break
                        stderrLog.append(String(buf, 0, n, Charsets.UTF_8))
                    }
                } catch (_: Exception) {}
            }.apply { start() }

            // 5. Raw pixel protocol: 1 byte page count, then per page 1 byte
            //    page index followed by width*3 raw RGB bytes per row.
            val stdin = session.stdin
            var writeError: Throwable? = null
            try {
                stdin.write(byteArrayOf((bitmaps.size and 0xff).toByte()))
                val rowPixels = IntArray(widthPx)
                val rowBytes = ByteArray(widthPx * 3)
                bitmaps.forEachIndexed { index, bitmap ->
                    stdin.write(byteArrayOf((index and 0xff).toByte()))
                    for (y in 0 until bitmap.height) {
                        bitmap.getPixels(rowPixels, 0, widthPx, 0, y, widthPx, 1)
                        var o = 0
                        for (x in 0 until widthPx) {
                            val p = rowPixels[x]
                            rowBytes[o++] = ((p shr 16) and 0xff).toByte()
                            rowBytes[o++] = ((p shr 8) and 0xff).toByte()
                            rowBytes[o++] = (p and 0xff).toByte()
                        }
                        stdin.write(rowBytes)
                    }
                    bitmap.recycle()
                    Timber.d("Engine: page $index written")
                }
                Timber.d("Engine: all pages written, calling flush")
                stdin.flush()
                Timber.d("Engine: stdin flushed, closing")
            } catch (e: Throwable) {
                writeError = e
                Timber.w(e, "Engine: stdin write failed (filter may have exited early)")
            }
            try {
                session.closeStdin()
                Timber.d("Engine: stdin closed")
            } catch (e: Exception) {
                Timber.w(e, "Engine: stdin close threw")
            }
            Timber.d("Engine: stdin closed, waiting for filter")

            val waitDone = java.util.concurrent.CountDownLatch(1)
            val exitCodeRef = java.util.concurrent.atomic.AtomicInteger(-3)
            Thread {
                exitCodeRef.set(try {
                    session.waitFor()
                } catch (e: Exception) {
                    Timber.w(e, "Engine: doWait failed")
                    -2
                })
                waitDone.countDown()
            }.apply { isDaemon = true }.start()
            if (!waitDone.await(90, java.util.concurrent.TimeUnit.SECONDS)) {
                Timber.w("Engine: doWait still blocked after 90s (filter alive)")
            }
            val exitCode = exitCodeRef.get()
            stdoutThread.join(10_000)
            stderrThread.join(5_000)
            session.destroy()

            val outBytes = if (outFile.exists()) outFile.length() else 0
            val headHex = if (outBytes > 0) {
                outFile.inputStream().use { it.readNBytes(16) }
                    .joinToString(" ") { "%02X".format(it) }
            } else ""
            Timber.d("Engine: exit=$exitCode out=$outBytes bytes head=$headHex stderr=$stderrLog writeError=${writeError?.message}")

            TestResult(
                success = exitCode == 0 && outBytes > 0,
                message = when {
                    exitCode == 0 && outBytes > 0 -> "管线跑通"
                    writeError != null -> "写入失败(${writeError.message}), exit=$exitCode"
                    else -> "驱动返回 exit=$exitCode"
                },
                outFile = outFile,
                outBytes = outBytes,
                headHex = headHex,
                stderrLog = stderrLog.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Throwable) {
            Timber.e(e, "Engine: pipeline test failed")
            TestResult(success = false, message = "失败: ${e.message}", stderrLog = e.stackTraceToString().take(2000))
        }
    }

    private fun makeTestPdf(context: Context): File {
        val file = File(context.cacheDir, "driver_test_page.pdf")
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }
        canvas.drawText("cPrint driver pipeline test", 60f, 100f, textPaint)
        canvas.drawText("ESC/P-R via libdrvescprJNI.so", 60f, 145f, textPaint)

        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK)
        colors.forEachIndexed { i, c ->
            val paint = Paint().apply { color = c }
            val left = 60f + i * 120f
            canvas.drawRect(RectF(left, 200f, left + 100f, 300f), paint)
        }
        canvas.drawText("The quick brown fox jumps over the lazy dog 0123456789", 60f, 380f, textPaint)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun renderPdf(pdfFile: File, widthPx: Int, heightPx: Int): List<Bitmap> {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val bitmaps = ArrayList<Bitmap>(renderer.pageCount)
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                bitmaps.add(bitmap)
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return bitmaps
    }
}
