package com.cprint.app.domain.usecase.printer

import com.cprint.app.domain.repository.PrinterRepository
import javax.inject.Inject

/**
 * Use case for disconnecting from the current printer
 */
class DisconnectPrinterUseCase @Inject constructor(
    private val printerRepository: PrinterRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return printerRepository.disconnectPrinter()
    }
}
