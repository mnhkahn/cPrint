package com.cprint.app.domain.repository

import android.hardware.usb.UsbDevice
import com.cprint.app.domain.model.Printer
import com.cprint.app.domain.model.PrinterStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for printer operations
 */
interface PrinterRepository {
    /**
     * Get all known printers
     */
    fun getAllPrinters(): Flow<List<Printer>>

    /**
     * Get default printer
     */
    fun getDefaultPrinter(): Flow<Printer?>

    /**
     * Get currently connected printer
     */
    fun getConnectedPrinter(): Flow<Printer?>

    /**
     * Get printer by ID
     */
    suspend fun getPrinterById(printerId: String): Printer?

    /**
     * Connect to a USB device
     */
    suspend fun connectPrinter(device: UsbDevice): Result<Printer>

    /**
     * Disconnect current printer
     */
    suspend fun disconnectPrinter(): Result<Unit>

    /**
     * Set default printer
     */
    suspend fun setDefaultPrinter(printerId: String): Result<Unit>

    /**
     * Update printer status
     */
    suspend fun updatePrinterStatus(printerId: String, status: PrinterStatus): Result<Unit>

    /**
     * Delete a printer
     */
    suspend fun deletePrinter(printerId: String): Result<Unit>

    /**
     * Check if a USB device is a supported printer
     */
    fun isSupportedPrinter(device: UsbDevice): Boolean

    /**
     * Get printer capabilities
     */
    suspend fun getPrinterCapabilities(printerId: String): Result<PrinterCapabilities>

    /**
     * Query printer status
     */
    suspend fun queryPrinterStatus(printerId: String): Result<PrinterStatus>
}

/**
 * Printer capabilities data class
 */
data class PrinterCapabilities(
    val supportedPaperSizes: List<String>,
    val supportsColor: Boolean,
    val supportsDuplex: Boolean,
    val maxResolution: String,
    val maxCopies: Int,
    val supportsScaling: Boolean
)
