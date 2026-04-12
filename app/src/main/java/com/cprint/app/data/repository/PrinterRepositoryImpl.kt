package com.cprint.app.data.repository

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.cprint.app.data.local.PrinterDao
import com.cprint.app.data.model.entity.PrinterEntity
import com.cprint.app.domain.model.KnownPrinters
import com.cprint.app.domain.model.Printer
import com.cprint.app.domain.repository.PrinterCapabilities
import com.cprint.app.domain.model.PrinterStatus
import com.cprint.app.domain.repository.PrinterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PrinterRepository
 */
@Singleton
class PrinterRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val printerDao: PrinterDao
) : PrinterRepository {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun getAllPrinters(): Flow<List<Printer>> {
        return printerDao.getAllPrinters().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getDefaultPrinter(): Flow<Printer?> {
        return printerDao.getDefaultPrinter().map { it?.toDomainModel() }
    }

    override fun getConnectedPrinter(): Flow<Printer?> {
        return printerDao.getConnectedPrinter().map { it?.toDomainModel() }
    }

    override suspend fun getPrinterById(printerId: String): Printer? {
        return printerDao.getPrinterById(printerId)?.toDomainModel()
    }

    override suspend fun connectPrinter(device: UsbDevice): Result<Printer> {
        return try {
            val printerInfo = KnownPrinters.findPrinter(device.vendorId, device.productId)
                ?: return Result.failure(IllegalArgumentException("Unsupported printer"))

            val existingPrinter = printerDao.getPrinterByVidPid(device.vendorId, device.productId)

            val printer = if (existingPrinter != null) {
                existingPrinter.copy(
                    status = PrinterStatus.READY,
                    lastConnectedAt = Date(),
                    connectionCount = existingPrinter.connectionCount + 1
                )
            } else {
                PrinterEntity(
                    id = UUID.randomUUID().toString(),
                    name = "${printerInfo.manufacturer} ${printerInfo.model}",
                    manufacturer = printerInfo.manufacturer,
                    model = printerInfo.model,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    serialNumber = device.serialNumber,
                    protocol = printerInfo.protocol,
                    supportedPaperSizes = listOf("A4", "A5", "Letter"),
                    supportsColor = false,
                    supportsDuplex = true,
                    maxResolution = "600x600",
                    status = PrinterStatus.READY,
                    isDefault = false,
                    lastConnectedAt = Date(),
                    connectionCount = 1
                )
            }

            printerDao.insertPrinter(printer)
            Result.success(printer.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnectPrinter(): Result<Unit> {
        return try {
            val connectedPrinter = printerDao.getConnectedPrinter().first()
            connectedPrinter?.let {
                printerDao.updatePrinterStatus(it.id, PrinterStatus.DISCONNECTED)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDefaultPrinter(printerId: String): Result<Unit> {
        return try {
            printerDao.clearDefaultPrinter()
            printerDao.setDefaultPrinter(printerId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePrinterStatus(printerId: String, status: PrinterStatus): Result<Unit> {
        return try {
            printerDao.updatePrinterStatus(printerId, status)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePrinter(printerId: String): Result<Unit> {
        return try {
            printerDao.deletePrinterById(printerId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isSupportedPrinter(device: UsbDevice): Boolean {
        // Check if device is a printer class
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }

        // Check against known printers list
        return KnownPrinters.findPrinter(device.vendorId, device.productId) != null
    }

    override suspend fun getPrinterCapabilities(printerId: String): Result<PrinterCapabilities> {
        val printer = printerDao.getPrinterById(printerId)
            ?: return Result.failure(IllegalArgumentException("Printer not found"))

        return Result.success(
            PrinterCapabilities(
                supportedPaperSizes = printer.supportedPaperSizes,
                supportsColor = printer.supportsColor,
                supportsDuplex = printer.supportsDuplex,
                maxResolution = printer.maxResolution,
                maxCopies = 99,
                supportsScaling = true
            )
        )
    }

    override suspend fun queryPrinterStatus(printerId: String): Result<PrinterStatus> {
        val printer = printerDao.getPrinterById(printerId)
            ?: return Result.failure(IllegalArgumentException("Printer not found"))

        // In a real implementation, this would query the actual printer
        // For now, return the stored status
        return Result.success(printer.status)
    }
}
