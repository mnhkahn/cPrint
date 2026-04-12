package com.cprint.app.domain.usecase.printer

import com.cprint.app.domain.repository.PrinterCapabilities
import com.cprint.app.domain.repository.PrinterRepository
import javax.inject.Inject

/**
 * Use case for getting printer capabilities
 */
class GetPrinterCapabilitiesUseCase @Inject constructor(
    private val printerRepository: PrinterRepository
) {
    suspend operator fun invoke(printerId: String): Result<PrinterCapabilities> {
        return printerRepository.getPrinterCapabilities(printerId)
    }
}
