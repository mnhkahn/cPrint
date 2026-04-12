package com.cprint.app.domain.usecase.printer

import com.cprint.app.domain.model.PrinterStatus
import com.cprint.app.domain.repository.PrinterRepository
import javax.inject.Inject

/**
 * Use case for querying printer status
 */
class QueryPrinterStatusUseCase @Inject constructor(
    private val printerRepository: PrinterRepository
) {
    suspend operator fun invoke(printerId: String): Result<PrinterStatus> {
        return printerRepository.queryPrinterStatus(printerId)
    }
}
