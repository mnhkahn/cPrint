package com.cprint.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.repository.PrinterDeviceStatus
import com.cprint.app.domain.repository.UsbPrintRepository
import com.cprint.app.domain.repository.UsbPrinterInfo
import com.cprint.app.driver.PrintDriverEngine
import com.cprint.app.util.NativeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UsbPrintRepository for USB printer communication
 */
@Singleton
class UsbPrintRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UsbPrintRepository {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null
    private var bulkOutEndpoint: UsbEndpoint? = null
    private var bulkInEndpoint: UsbEndpoint? = null

    private val _printProgress = MutableStateFlow(0)
    override fun getPrintProgress(): Flow<Int> = _printProgress.asStateFlow()

    private var isCancelled = false

    override suspend fun sendPrintJob(job: PrintJob, settings: PrintSettings): Result<Unit> {
        android.util.Log.d("UsbPrintRepo", "sendPrintJob STARTED for: ${job.documentName}")
        Timber.d("sendPrintJob started for: ${job.documentName}, type: ${job.documentType}")
        return withContext(Dispatchers.IO) {
            try {
                isCancelled = false
                _printProgress.value = 0

                // Check USB connection and try to auto-reconnect if needed
                var connection = currentConnection
                android.util.Log.d("UsbPrintRepo", "currentConnection=$connection")
                if (connection == null) {
                    android.util.Log.w("UsbPrintRepo", "No USB connection available, attempting auto-reconnect...")
                    val reconnected = tryAutoReconnect()
                    if (!reconnected) {
                        android.util.Log.e("UsbPrintRepo", "Auto-reconnect failed - cannot print")
                        return@withContext Result.failure(IllegalStateException("打印机未连接，请重新插拔打印机"))
                    }
                    connection = currentConnection
                    android.util.Log.d("UsbPrintRepo", "Auto-reconnect successful, connection=$connection")
                }

                // Verify connection and endpoint
                if (currentConnection == null) {
                    Timber.e("Current connection is null after reconnect check!")
                    return@withContext Result.failure(IllegalStateException("打印机连接异常"))
                }
                if (bulkOutEndpoint == null) {
                    Timber.e("Bulk out endpoint is null!")
                    return@withContext Result.failure(IllegalStateException("打印机端点未初始化"))
                }
                Timber.d("USB connection verified: device=${currentDevice?.deviceName}, endpoint=$bulkOutEndpoint")

                // Prepare print data based on document type and settings
                android.util.Log.d("UsbPrintRepo", "Preparing print data for: ${job.documentName}, type: ${job.documentType}")
                val printData = preparePrintData(job, settings)
                android.util.Log.d("UsbPrintRepo", "Print data prepared: ${printData.size} bytes")
                Timber.d("Print data prepared: ${printData.size} bytes")

                // Validate print data
                if (printData.isEmpty()) {
                    Timber.e("Print data is empty!")
                    return@withContext Result.failure(IllegalStateException("打印数据为空"))
                }
                if (printData.size < 100) {
                    Timber.w("Print data is suspiciously small: ${printData.size} bytes")
                }

                // Send data in chunks
                // Limit data size to prevent hanging
                val maxDataSize = 64 * 1024 * 1024 // Driver output can exceed the old ESC/P prototype limit.
                if (printData.size > maxDataSize) {
                    Timber.w("Print data too large (${printData.size} bytes), truncating to ${maxDataSize} bytes")
                    // Return error instead of printing partial data
                    return@withContext Result.failure(
                        IllegalStateException("文档过大，请尝试打印较少页数")
                    )
                }

                // bulkTransfer 由内核按端点 maxPacketSize 自动拆包，
                // 应用层用大 buffer 才能跑满 Full-Speed 带宽（64 字节分包只有 ~50KB/s）
                val chunkSize = 16384
                val totalCopies = job.copies.coerceAtLeast(1)
                val totalChunks = ((printData.size + chunkSize - 1) / chunkSize) * totalCopies
                Timber.d("Sending print data in $totalChunks chunks (${printData.size} bytes x $totalCopies copies)")

                // Check if cancelled before starting
                if (isCancelled) {
                    return@withContext Result.failure(Exception("打印已取消"))
                }

                var chunkIndex = 0
                var totalBytesSent = 0
                val startTime = System.currentTimeMillis()
                // 按 20KB/s 的保守吞吐估算超时，下限 2 分钟
                val maxPrintTime = maxOf(120_000L, printData.size.toLong() * totalCopies / 20)

                for (copy in 0 until totalCopies) {
                    printData.inputStream().use { stream ->
                        val buffer = ByteArray(chunkSize)
                        var bytesRead: Int

                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            // Check timeout
                            if (System.currentTimeMillis() - startTime > maxPrintTime) {
                                Timber.e("Print timeout after ${maxPrintTime / 1000} seconds")
                                return@withContext Result.failure(Exception("打印超时，请检查打印机"))
                            }
                            if (isCancelled) {
                                return@withContext Result.failure(Exception("Print cancelled"))
                            }

                            val chunk = if (bytesRead < chunkSize) buffer.copyOf(bytesRead) else buffer
                            val result = sendRawData(chunk)

                            if (result.isFailure) {
                                Timber.e("Failed to send chunk $chunkIndex: ${result.exceptionOrNull()?.message}")
                                return@withContext Result.failure(
                                    result.exceptionOrNull() ?: Exception("Failed to send data")
                                )
                            }

                            chunkIndex++
                            totalBytesSent += bytesRead
                            val progress = (chunkIndex * 100) / totalChunks
                            _printProgress.value = progress

                            if (progress % 10 == 0 || chunkIndex % 100 == 0) {
                                Timber.d("Print progress: $progress% (copy ${copy + 1}/$totalCopies, chunk $chunkIndex/$totalChunks)")
                            }
                        }
                    }
                }

                Timber.d("Print data sent complete: $totalBytesSent bytes in $chunkIndex chunks, $totalCopies copies")

                _printProgress.value = 100
                Timber.d("Print job sent successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send print job: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun sendRawData(data: ByteArray): Result<Unit> {
        android.util.Log.d("UsbPrintRepo", "sendRawData: ${data.size} bytes")
        return withContext(Dispatchers.IO) {
            try {
                val connection = currentConnection
                    ?: run {
                        android.util.Log.e("UsbPrintRepo", "sendRawData: No USB connection!")
                        return@withContext Result.failure(IllegalStateException("No USB connection"))
                    }

                val endpoint = bulkOutEndpoint
                    ?: run {
                        android.util.Log.e("UsbPrintRepo", "sendRawData: No output endpoint!")
                        return@withContext Result.failure(IllegalStateException("No output endpoint"))
                    }

                android.util.Log.d("UsbPrintRepo", "Sending ${data.size} bytes via bulkTransfer...")
                val bytesWritten = connection.bulkTransfer(
                    endpoint,
                    data,
                    data.size,
                    5000 // 5 second timeout
                )

                android.util.Log.d("UsbPrintRepo", "bulkTransfer result: $bytesWritten bytes written (expected: ${data.size})")

                if (bytesWritten < 0) {
                    android.util.Log.e("UsbPrintRepo", "USB bulk transfer failed with error code: $bytesWritten, attempting reconnect...")
                    // Printer may be in error state (out of paper, etc.), try reconnect
                    disconnect()
                    val reconnected = tryAutoReconnect()
                    if (reconnected && currentConnection != null) {
                        android.util.Log.d("UsbPrintRepo", "Reconnected, retrying bulkTransfer...")
                        val retryBytes = currentConnection!!.bulkTransfer(
                            bulkOutEndpoint, data, data.size, 5000
                        )
                        android.util.Log.d("UsbPrintRepo", "Retry bulkTransfer result: $retryBytes")
                        if (retryBytes > 0) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception("USB bulk transfer failed after reconnect: $retryBytes"))
                        }
                    } else {
                        Result.failure(Exception("USB bulk transfer failed: $bytesWritten, reconnect failed"))
                    }
                } else if (bytesWritten == 0) {
                    android.util.Log.e("UsbPrintRepo", "USB bulk transfer wrote 0 bytes!")
                    Result.failure(Exception("USB bulk transfer wrote 0 bytes"))
                } else if (bytesWritten < data.size) {
                    android.util.Log.w("UsbPrintRepo", "USB bulk transfer partial write: $bytesWritten/${data.size} bytes")
                    Result.success(Unit) // Partial success, continue
                } else {
                    android.util.Log.d("UsbPrintRepo", "USB bulk transfer successful: $bytesWritten bytes")
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send raw data")
                Result.failure(e)
            }
        }
    }

    override suspend fun sendPclData(pclData: ByteArray): Result<Unit> {
        // PCL data is sent as raw bytes
        return sendRawData(pclData)
    }

    override suspend fun sendEscPData(escpData: ByteArray): Result<Unit> {
        // ESC/P data is sent as raw bytes
        return sendRawData(escpData)
    }

    override suspend fun sendPostScriptData(psData: ByteArray): Result<Unit> {
        // PostScript data is sent as raw bytes
        return sendRawData(psData)
    }

    override suspend fun queryPrinterStatus(): Result<PrinterDeviceStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = currentConnection
                    ?: return@withContext Result.failure(IllegalStateException("No USB connection"))

                val inEndpoint = bulkInEndpoint
                    ?: return@withContext Result.failure(IllegalStateException("No input endpoint"))

                // Send status request command (IEEE 1284 Device ID request)
                val statusRequest = byteArrayOf(0x1B, 0x01) // ESC + status request

                val bytesWritten = connection.bulkTransfer(
                    bulkOutEndpoint,
                    statusRequest,
                    statusRequest.size,
                    1000
                )

                if (bytesWritten < 0) {
                    return@withContext Result.failure(Exception("Failed to send status request"))
                }

                // Read response
                val buffer = ByteArray(64)
                val bytesRead = connection.bulkTransfer(
                    inEndpoint,
                    buffer,
                    buffer.size,
                    2000
                )

                if (bytesRead > 0) {
                    val response = buffer.copyOf(bytesRead)
                    val status = parsePrinterStatus(response)
                    Result.success(status)
                } else {
                    // Return default status if no response
                    Result.success(
                        PrinterDeviceStatus(
                            isOnline = true,
                            isReady = true,
                            paperOut = false,
                            paperJam = false,
                            coverOpen = false,
                            tonerLow = false,
                            errorCode = null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to query printer status")
                Result.failure(e)
            }
        }
    }

    override suspend fun cancelPrint(): Result<Unit> {
        isCancelled = true
        return Result.success(Unit)
    }

    override suspend fun isPrinterReady(): Boolean {
        val status = queryPrinterStatus().getOrNull()
        return status?.isReady == true
    }

    override suspend fun getPrinterInfo(): Result<UsbPrinterInfo> {
        val device = currentDevice
            ?: return Result.failure(IllegalStateException("No USB device connected"))

        return Result.success(
            UsbPrinterInfo(
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturer = device.manufacturerName ?: "Unknown",
                productName = device.productName ?: "Unknown",
                serialNumber = device.serialNumber,
                protocol = detectProtocol(device)
            )
        )
    }

    /**
     * Connect to a USB printer device
     */
    fun connect(device: UsbDevice, connection: UsbDeviceConnection): Boolean {
        currentDevice = device
        currentConnection = connection

        // Find printer interface and endpoints
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                if (connection.claimInterface(usbInterface, true)) {
                    findEndpoints(usbInterface)
                    return true
                }
            }
        }

        return false
    }

    /**
     * Disconnect from current printer
     */
    fun disconnect() {
        currentConnection?.let { connection ->
            currentDevice?.let { device ->
                for (i in 0 until device.interfaceCount) {
                    connection.releaseInterface(device.getInterface(i))
                }
            }
            connection.close()
        }

        currentDevice = null
        currentConnection = null
        bulkOutEndpoint = null
        bulkInEndpoint = null
    }

    /**
     * Find bulk transfer endpoints
     */
    private fun findEndpoints(usbInterface: UsbInterface) {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            when (endpoint.type) {
                UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                    if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        bulkOutEndpoint = endpoint
                    } else if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        bulkInEndpoint = endpoint
                    }
                }
            }
        }
    }

    /**
     * Detect printer protocol based on device descriptors
     */
    private fun detectProtocol(device: UsbDevice): String {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return when (usbInterface.interfaceProtocol) {
                    1 -> "Unidirectional"
                    2 -> "Bidirectional"
                    3 -> "IEEE 1284.4"
                    else -> "Unknown"
                }
            }
        }
        return "Unknown"
    }

    /**
     * Parse printer status response
     */
    private fun parsePrinterStatus(data: ByteArray): PrinterDeviceStatus {
        // Simplified status parsing - actual implementation would depend on printer protocol
        return PrinterDeviceStatus(
            isOnline = data.isNotEmpty(),
            isReady = data.getOrNull(0)?.toInt()?.and(0x10) != 0,
            paperOut = data.getOrNull(0)?.toInt()?.and(0x01) != 0,
            paperJam = data.getOrNull(0)?.toInt()?.and(0x02) != 0,
            coverOpen = data.getOrNull(0)?.toInt()?.and(0x04) != 0,
            tonerLow = data.getOrNull(0)?.toInt()?.and(0x08) != 0,
            errorCode = if (data.size > 1) data[1].toInt() else null
        )
    }

    /**
     * Prepare print data based on document type and settings
     */
    private suspend fun preparePrintData(job: PrintJob, settings: PrintSettings): ByteArray {
        // This is a simplified implementation
        // In a real app, you would:
        // 1. Load the document (PDF, image, etc.)
        // 2. Convert to printer-specific format (PCL, ESC/P, etc.)
        // 3. Apply settings (paper size, orientation, etc.)

        return when {
            job.documentType == "application/pdf" -> preparePdfData(job, settings)
            job.documentType.startsWith("image/") -> prepareImageData(job, settings)
            else -> throw IllegalArgumentException("Unsupported document type: ${job.documentType}")
        }
    }

    /**
     * Prepare PDF data for printing by converting to ESC/P raster commands
     */
    private suspend fun preparePdfData(job: PrintJob, settings: PrintSettings): ByteArray {
        android.util.Log.d("UsbPrintRepo", "preparePdfData START for: ${job.documentName}")
        Timber.d("Preparing PDF data for: ${job.documentName}, URI: ${job.documentUri}")
        Timber.d("Document type: ${job.documentType}, pages: ${job.totalPages}")

        return try {
            // Parse page range if specified
            val pageRange = parsePageRange(job.pageRange, job.totalPages)
            val pagesToPrint = pageRange ?: (0 until job.totalPages)
            Timber.d("Pages to print: $pagesToPrint")

            val driverOutput = PrintDriverEngine.renderPdfForUsb(
                context = context,
                documentUri = job.documentUri,
                pageIndexes = pagesToPrint.toList(),
                settings = settings
            )
            val result = driverOutput.bytes
            Timber.d("escpr driver prepared ${result.size} bytes for ${pagesToPrint.count()} pages; exit=${driverOutput.exitCode}")
            // Preserve the exact driver output for diagnosis on a debug build.
            try {
                val debugFile = java.io.File(context.getExternalFilesDir(null), "cprint_esc_data.bin")
                debugFile.writeBytes(result)
                android.util.Log.d("UsbPrintRepo", "escpr driver data dumped to: ${debugFile.absolutePath} (${result.size} bytes)")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Failed to dump debug file: ${e.message}")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("UsbPrintRepo", "preparePdfData EXCEPTION: ${e.message}", e)
            Timber.e(e, "Failed to prepare PDF data: ${e.message}")
            throw IllegalStateException("escpr 驱动未能生成打印数据: ${e.message}", e)
        }
    }

    /**
     * Parse page range string (e.g., "1-3,5" -> [0,1,2,4])
     */
    private fun parsePageRange(rangeStr: String?, totalPages: Int): List<Int>? {
        if (rangeStr.isNullOrBlank()) return null

        val pages = mutableSetOf<Int>()
        val parts = rangeStr.split(",", "，")

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size == 2) {
                    val start = range[0].trim().toIntOrNull()?.minus(1) ?: 0
                    val end = range[1].trim().toIntOrNull()?.minus(1) ?: (totalPages - 1)
                    for (i in start.coerceAtLeast(0)..end.coerceAtMost(totalPages - 1)) {
                        pages.add(i)
                    }
                }
            } else {
                trimmed.toIntOrNull()?.let { pages.add(it - 1) }
            }
        }

        return if (pages.isNotEmpty()) pages.sorted() else null
    }

    /**
     * Render a PDF page to ESC/P raster commands
     */
    private fun renderPdfPageToEscP(
        documentUri: String,
        pageIndex: Int,
        settings: PrintSettings
    ): ByteArray {
        val output = ByteArrayOutputStream()
        android.util.Log.d("UsbPrintRepo", "renderPdfPageToEscP START page $pageIndex")
        Timber.d("Starting to render PDF page $pageIndex from: $documentUri")

        var pfd: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            val uri = Uri.parse(documentUri)
            android.util.Log.d("UsbPrintRepo", "Parsed URI: $uri")

            android.util.Log.d("UsbPrintRepo", "Opening file descriptor...")
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                android.util.Log.e("UsbPrintRepo", "openFileDescriptor returned NULL!")
                throw IllegalStateException("Cannot open document: $documentUri")
            }
            android.util.Log.d("UsbPrintRepo", "File descriptor opened: $pfd, fd=${pfd.fd}")

            // Check if file descriptor is valid
            try {
                val statSize = pfd.statSize
                android.util.Log.d("UsbPrintRepo", "File descriptor valid, size: $statSize bytes")
                if (statSize <= 0) {
                    android.util.Log.e("UsbPrintRepo", "File size is 0 or negative!")
                    throw IllegalStateException("File is empty or invalid")
                }
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Failed to stat file descriptor: ${e.message}")
                throw IllegalStateException("Cannot read file: ${e.message}")
            }

            android.util.Log.d("UsbPrintRepo", "Creating PdfRenderer...")
            try {
                pdfRenderer = PdfRenderer(pfd)
                android.util.Log.d("UsbPrintRepo", "PdfRenderer created! Page count: ${pdfRenderer.pageCount}")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "PdfRenderer creation FAILED: ${e.message}")
                android.util.Log.e("UsbPrintRepo", "Exception type: ${e.javaClass.name}")
                throw e
            }

            if (pageIndex >= pdfRenderer.pageCount) {
                android.util.Log.e("UsbPrintRepo", "Invalid page index: $pageIndex, total pages: ${pdfRenderer.pageCount}")
                throw IllegalStateException("Invalid page index: $pageIndex")
            }

            android.util.Log.d("UsbPrintRepo", "Opening page $pageIndex...")
            try {
                page = pdfRenderer.openPage(pageIndex)
                android.util.Log.d("UsbPrintRepo", "Page opened: ${page.width}x${page.height}")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Failed to open page: ${e.message}")
                throw e
            }

            // Render at 360dpi-equivalent resolution for A4 width (~1488px @ 180dpi or 2976px @ 360dpi)
            // Using 512px width for faster testing
            val renderWidth = 512
            val scale = renderWidth.toFloat() / page.width
            val renderHeight = (page.height * scale).toInt()
            android.util.Log.d("UsbPrintRepo", "Render dims: ${renderWidth}x$renderHeight")

            android.util.Log.d("UsbPrintRepo", "Creating bitmap...")
            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            android.util.Log.d("UsbPrintRepo", "Bitmap created")

            android.util.Log.d("UsbPrintRepo", "Rendering PDF to bitmap...")
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                android.util.Log.d("UsbPrintRepo", "PDF rendered to bitmap successfully")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Page.render() FAILED: ${e.message}")
                bitmap.recycle()
                throw e
            }

            android.util.Log.d("UsbPrintRepo", "Converting bitmap to RGB...")
            android.util.Log.d("UsbPrintRepo", "Bitmap size before convert: ${bitmap.width}x${bitmap.height}")

            // Extract RGB bytes from ARGB bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            var nonWhitePixels = 0
            for (pixel in pixels) {
                if (pixel != Color.WHITE && pixel != 0xFFFFFFFF.toInt()) {
                    nonWhitePixels++
                }
            }
            android.util.Log.d("UsbPrintRepo", "Bitmap has $nonWhitePixels non-white pixels out of ${pixels.size}")

            val rgbBytes = ByteArray(bitmap.width * bitmap.height * 3)
            var idx = 0
            for (pixel in pixels) {
                rgbBytes[idx++] = Color.red(pixel).toByte()
                rgbBytes[idx++] = Color.green(pixel).toByte()
                rgbBytes[idx++] = Color.blue(pixel).toByte()
            }
            android.util.Log.d("UsbPrintRepo", "RGB bytes prepared: ${rgbBytes.size} bytes")

            // Generate ESC/P-R data via JNI
            val dpi = 360
            val escprData = NativeUtils.nativeGenerateEscprData(rgbBytes, bitmap.width, bitmap.height, dpi)
            if (escprData != null && escprData.size > 0) {
                output.write(escprData)
                android.util.Log.d("UsbPrintRepo", "Native ESC/P-R data: ${escprData.size} bytes")
            } else {
                android.util.Log.e("UsbPrintRepo", "ESC/P-R generation failed, trying ESC/P2 fallback...")
                // Fallback to ESC/P2 black/white raster
                val escp2Data = NativeUtils.nativeGenerateEscp2Data(rgbBytes, bitmap.width, bitmap.height)
                if (escp2Data != null && escp2Data.size > 0) {
                    output.write(escp2Data)
                    android.util.Log.d("UsbPrintRepo", "Native ESC/P2 fallback data: ${escp2Data.size} bytes")
                } else {
                    android.util.Log.e("UsbPrintRepo", "Both ESC/P-R and ESC/P2 generation failed!")
                    throw IllegalStateException("Failed to generate printer data")
                }
            }

            bitmap.recycle()
            android.util.Log.d("UsbPrintRepo", "Bitmap recycled, cleanup starting...")

        } catch (e: Exception) {
            android.util.Log.e("UsbPrintRepo", "renderPdfPageToEscP EXCEPTION: ${e.message}", e)
            Timber.e(e, "Error rendering PDF page $pageIndex: ${e.message}")
            // Return error message as text
            val errorMsg = "Error rendering page ${pageIndex + 1}: ${e.message}"
            output.write(errorMsg.toByteArray())
        } finally {
            // Clean up in reverse order
            try {
                page?.close()
                android.util.Log.d("UsbPrintRepo", "Page closed")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Error closing page: ${e.message}")
            }
            try {
                pdfRenderer?.close()
                android.util.Log.d("UsbPrintRepo", "PdfRenderer closed")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Error closing PdfRenderer: ${e.message}")
            }
            try {
                pfd?.close()
                android.util.Log.d("UsbPrintRepo", "File descriptor closed")
            } catch (e: Exception) {
                android.util.Log.e("UsbPrintRepo", "Error closing file descriptor: ${e.message}")
            }
        }

        val result = output.toByteArray()
        android.util.Log.d("UsbPrintRepo", "renderPdfPageToEscP completed: ${result.size} bytes")
        return result
    }

    /**
     * Build a simple ESC/P test page for Epson printers
     */
    private fun buildEscPTestPage(job: PrintJob, settings: PrintSettings): ByteArray {
        val output = ByteArrayOutputStream()
        android.util.Log.d("UsbPrintRepo", "buildEscPTestPage START")

        // ESC/P initialization sequence
        output.write(byteArrayOf(0x1B, 0x40)) // ESC @ - Initialize printer

        // Set line spacing to 1/8 inch
        output.write(byteArrayOf(0x1B, 0x30)) // ESC 0

        // Center alignment
        output.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 - Center align

        // Bold text
        output.write(byteArrayOf(0x1B, 0x45)) // ESC E - Bold on

        // Title
        output.write("cPrint Test Page\n".toByteArray(Charsets.UTF_8))

        // Bold off
        output.write(byteArrayOf(0x1B, 0x46)) // ESC F - Bold off

        // Left alignment
        output.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 - Left align

        // Document info
        output.write("\n".toByteArray(Charsets.UTF_8))
        output.write("Document: ${job.documentName}\n".toByteArray(Charsets.UTF_8))
        output.write("Pages: ${job.totalPages}\n".toByteArray(Charsets.UTF_8))
        output.write("Copies: ${job.copies}\n".toByteArray(Charsets.UTF_8))
        output.write("Paper: ${settings.paperSize.name}\n".toByteArray(Charsets.UTF_8))
        output.write("\n".toByteArray(Charsets.UTF_8))

        // Test pattern
        output.write("----------------------------------------\n".toByteArray(Charsets.UTF_8))
        output.write("Printer Test Pattern\n".toByteArray(Charsets.UTF_8))
        output.write("----------------------------------------\n".toByteArray(Charsets.UTF_8))
        output.write("0123456789ABCDEF0123456789ABCDEF01234567\n".toByteArray(Charsets.UTF_8))
        output.write("Test print from cPrint Android app\n".toByteArray(Charsets.UTF_8))
        output.write("----------------------------------------\n".toByteArray(Charsets.UTF_8))

        // Form feed (page eject)
        output.write(0x0C) // FF - Form feed

        // Cut paper (if supported)
        output.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 - Cut paper

        Timber.d("ESC/P test page prepared, size: ${output.size()} bytes")
        android.util.Log.d("UsbPrintRepo", "buildEscPTestPage END: ${output.size()} bytes")
        return output.toByteArray()
    }

    /**
     * Convert bitmap to ESC/P2 raster commands for Epson inkjet printers.
     * Uses ESC . command with correct 6-byte header format.
     * m=0 (black), v=1 (180dpi), h=1 (180dpi), nL/nH = dots per line.
     */
    private fun convertBitmapToEscp2(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()

        val maxWidth = 512
        val scaleFactor = if (bitmap.width > maxWidth) maxWidth.toFloat() / bitmap.width else 1.0f
        val finalWidth = (bitmap.width * scaleFactor).toInt()
        val finalHeight = (bitmap.height * scaleFactor).toInt()

        android.util.Log.d("UsbPrintRepo", "Converting bitmap ${bitmap.width}x${bitmap.height} to ESC/P2...")
        android.util.Log.d("UsbPrintRepo", "Scale factor: $scaleFactor -> ${finalWidth}x${finalHeight}")

        val scaledBitmap = if (scaleFactor < 1.0f)
            Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
        else bitmap

        val pixels = IntArray(finalWidth * finalHeight)
        scaledBitmap.getPixels(pixels, 0, finalWidth, 0, 0, finalWidth, finalHeight)

        val bytesPerLine = (finalWidth + 7) / 8
        android.util.Log.d("UsbPrintRepo", "Bytes per line: $bytesPerLine, total lines: $finalHeight")

        // ESC/P2 raster: ESC . m nL nH [data...]
        // m=0 (black), nL/nH = horizontal dots
        for (y in 0 until finalHeight) {
            val rowOffset = y * finalWidth

            val rowBytes = ByteArray(bytesPerLine)
            for (x in 0 until finalWidth step 8) {
                var byteVal = 0
                for (bit in 0 until 8) {
                    val px = x + bit
                    if (px < finalWidth) {
                        val pixel = pixels[rowOffset + px]
                        val gray = (Color.red(pixel) * 299 +
                                    Color.green(pixel) * 587 +
                                    Color.blue(pixel) * 114) / 1000
                        if (gray < 128) byteVal = byteVal or (0x80 ushr bit)  // MSB-first, 1=black
                    }
                }
                rowBytes[x / 8] = byteVal.toByte()
            }

            // ESC . m nL nH data  (5-byte header)
            output.write(0x1B)        // ESC
            output.write(0x2E)        // .
            output.write(0x00)        // m = 0 (black)
            output.write(finalWidth and 0xFF)          // nL = dots low
            output.write((finalWidth shr 8) and 0xFF)  // nH = dots high
            output.write(rowBytes)    // bitmap data
        }

        // Form Feed
        output.write(0x0C)             // FF

        if (scaledBitmap != bitmap) scaledBitmap.recycle()

        val result = output.toByteArray()
        android.util.Log.d("UsbPrintRepo", "ESC/P2 data: ${result.size} bytes")
        return result
    }

    /**
     * Prepare image data for printing
     */
    private fun prepareImageData(job: PrintJob, settings: PrintSettings): ByteArray {
        // In a real implementation, this would:
        // 1. Load the image
        // 2. Scale and convert to printer format
        // 3. Generate PCL/ESC-P commands

        return byteArrayOf() // Placeholder
    }

    /**
     * Try to auto-reconnect to a USB printer
     * Called when connection is lost (e.g., after app restart)
     */
    private fun tryAutoReconnect(): Boolean {
        android.util.Log.d("UsbPrintRepo", "tryAutoReconnect START")
        try {
            val devices = usbManager.deviceList.values
            android.util.Log.d("UsbPrintRepo", "Found ${devices.size} USB devices")

            devices.forEach { device ->
                val isPrinter = (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER
                }
                val hasPermission = usbManager.hasPermission(device)
                android.util.Log.d("UsbPrintRepo", "Device: ${device.deviceName}, isPrinter=$isPrinter, hasPermission=$hasPermission")
            }

            // Find a printer class device with permission
            val printerDevice = devices.firstOrNull { device ->
                val isPrinter = (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER
                }
                isPrinter && usbManager.hasPermission(device)
            }

            printerDevice?.let { device ->
                android.util.Log.d("UsbPrintRepo", "Found printer device for auto-reconnect: ${device.deviceName}")
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    android.util.Log.d("UsbPrintRepo", "Device opened, attempting to connect...")
                    val success = connect(device, connection)
                    if (success) {
                        android.util.Log.d("UsbPrintRepo", "Auto-reconnect successful for ${device.deviceName}")
                        return true
                    } else {
                        android.util.Log.e("UsbPrintRepo", "Failed to claim interface during auto-reconnect")
                        connection.close()
                    }
                } else {
                    android.util.Log.e("UsbPrintRepo", "Failed to open device during auto-reconnect")
                }
            } ?: run {
                android.util.Log.w("UsbPrintRepo", "No printer device found with permission for auto-reconnect")
            }
        } catch (e: Exception) {
            android.util.Log.e("UsbPrintRepo", "Exception during auto-reconnect: ${e.message}", e)
        }
        android.util.Log.d("UsbPrintRepo", "tryAutoReconnect FAILED")
        return false
    }

    /**
     * Get PCL paper size code
     */
    private fun getPaperSizeCode(paperSize: String): Int {
        return when (paperSize) {
            "A4" -> 26
            "A5" -> 25
            "A3" -> 27
            "LETTER" -> 2
            "LEGAL" -> 3
            "B5" -> 45
            else -> 26 // Default to A4
        }
    }
}
