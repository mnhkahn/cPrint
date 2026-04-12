package com.cprint.app.domain.usecase.print

import com.cprint.app.domain.repository.PrintJobRepository
import com.cprint.app.domain.repository.UsbPrintRepository
import javax.inject.Inject

/**
 * Use case for cancelling a print job
 */
class CancelPrintJobUseCase @Inject constructor(
    private val printJobRepository: PrintJobRepository,
    private val usbPrintRepository: UsbPrintRepository
) {
    suspend operator fun invoke(jobId: String): Result<Unit> {
        // Cancel USB print operation first
        usbPrintRepository.cancelPrint()

        // Update job status
        return printJobRepository.cancelJob(jobId)
    }
}
