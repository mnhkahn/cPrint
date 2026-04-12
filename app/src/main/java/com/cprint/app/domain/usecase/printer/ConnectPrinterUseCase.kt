package com.cprint.app.domain.usecase.printer

import android.hardware.usb.UsbDevice
import com.cprint.app.domain.model.Printer
import com.cprint.app.domain.repository.PrinterRepository
import javax.inject.Inject

/**
 * Use case for connecting to a USB printer
 */
class ConnectPrinterUseCase @Inject constructor(
    private val printerRepository: PrinterRepository
) {
    suspend operator fun invoke(device: UsbDevice): Result<Printer> {
        return printerRepository.connectPrinter(device)
    }
}
