package com.cprint.app.data.repository

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.repository.PrinterDeviceStatus
import com.cprint.app.domain.repository.UsbPrintRepository
import com.cprint.app.domain.repository.UsbPrinterInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
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
        return withContext(Dispatchers.IO) {
            try {
                isCancelled = false
                _printProgress.value = 0

                // Prepare print data based on document type and settings
                val printData = preparePrintData(job, settings)

                // Send data in chunks
                val chunkSize = 16384 // 16KB chunks
                val totalChunks = (printData.size + chunkSize - 1) / chunkSize

                printData.inputStream().use { stream ->
                    val buffer = ByteArray(chunkSize)
                    var bytesRead: Int
                    var chunkIndex = 0

                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            return@withContext Result.failure(Exception("Print cancelled"))
                        }

                        val chunk = if (bytesRead < chunkSize) buffer.copyOf(bytesRead) else buffer
                        val result = sendRawData(chunk)

                        if (result.isFailure) {
                            return@withContext Result.failure(
                                result.exceptionOrNull() ?: Exception("Failed to send data")
                            )
                        }

                        chunkIndex++
                        _printProgress.value = (chunkIndex * 100) / totalChunks

                        // Small delay to prevent overwhelming the printer
                        delay(10)
                    }
                }

                _printProgress.value = 100
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send print job")
                Result.failure(e)
            }
        }
    }

    override suspend fun sendRawData(data: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = currentConnection
                    ?: return@withContext Result.failure(IllegalStateException("No USB connection"))

                val endpoint = bulkOutEndpoint
                    ?: return@withContext Result.failure(IllegalStateException("No output endpoint"))

                val bytesWritten = connection.bulkTransfer(
                    endpoint,
                    data,
                    data.size,
                    5000 // 5 second timeout
                )

                if (bytesWritten < 0) {
                    Result.failure(Exception("USB bulk transfer failed: $bytesWritten"))
                } else {
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
    private fun preparePrintData(job: PrintJob, settings: PrintSettings): ByteArray {
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
     * Prepare PDF data for printing
     */
    private fun preparePdfData(job: PrintJob, settings: PrintSettings): ByteArray {
        // In a real implementation, this would:
        // 1. Parse the PDF
        // 2. Rasterize pages to bitmap
        // 3. Convert to PCL or other printer language

        // For now, return a placeholder PCL command sequence
        val pclHeader = buildString {
            append("\u001B%-12345X@PJL JOB\n")
            append("@PJL ENTER LANGUAGE=PCL\n")
            append("\u001BE") // Reset
            append("\u001B&l${getPaperSizeCode(settings.paperSize.name)}A") // Paper size
            append("\u001B&l${if (settings.orientation.name == "LANDSCAPE") "1" else "0"}O") // Orientation
        }

        val pclFooter = "\u001BE\u001B%-12345X@PJL EOJ\n\u001B%-12345X"

        return (pclHeader + pclFooter).toByteArray(Charsets.UTF_8)
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
