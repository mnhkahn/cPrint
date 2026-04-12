package com.cprint.app.domain.repository

import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for USB printing operations
 */
interface UsbPrintRepository {
    /**
     * Send print job to USB printer
     */
    suspend fun sendPrintJob(job: PrintJob, settings: PrintSettings): Result<Unit>

    /**
     * Send raw data to printer
     */
    suspend fun sendRawData(data: ByteArray): Result<Unit>

    /**
     * Send PCL data to printer
     */
    suspend fun sendPclData(pclData: ByteArray): Result<Unit>

    /**
     * Send ESC/P data to printer
     */
    suspend fun sendEscPData(escpData: ByteArray): Result<Unit>

    /**
     * Send PostScript data to printer
     */
    suspend fun sendPostScriptData(psData: ByteArray): Result<Unit>

    /**
     * Get printer status from device
     */
    suspend fun queryPrinterStatus(): Result<PrinterDeviceStatus>

    /**
     * Get print progress
     */
    fun getPrintProgress(): Flow<Int>

    /**
     * Cancel current print operation
     */
    suspend fun cancelPrint(): Result<Unit>

    /**
     * Check if printer is ready
     */
    suspend fun isPrinterReady(): Boolean

    /**
     * Get printer information
     */
    suspend fun getPrinterInfo(): Result<UsbPrinterInfo>
}

/**
 * Printer device status
 */
data class PrinterDeviceStatus(
    val isOnline: Boolean,
    val isReady: Boolean,
    val paperOut: Boolean,
    val paperJam: Boolean,
    val coverOpen: Boolean,
    val tonerLow: Boolean,
    val errorCode: Int?
)

/**
 * USB Printer information
 */
data class UsbPrinterInfo(
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String,
    val productName: String,
    val serialNumber: String?,
    val protocol: String
)
