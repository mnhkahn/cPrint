package com.cprint.app.domain.usecase.printer

import com.cprint.app.domain.model.Printer
import com.cprint.app.domain.repository.PrinterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting the currently connected printer
 */
class GetConnectedPrinterUseCase @Inject constructor(
    private val printerRepository: PrinterRepository
) {
    operator fun invoke(): Flow<Printer?> {
        return printerRepository.getConnectedPrinter()
    }
}
