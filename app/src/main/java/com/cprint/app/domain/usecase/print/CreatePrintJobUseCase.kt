package com.cprint.app.domain.usecase.print

import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.repository.PrintJobRepository
import com.cprint.app.domain.repository.UsbPrintRepository
import javax.inject.Inject

/**
 * Use case for creating and executing a print job
 */
class CreatePrintJobUseCase @Inject constructor(
    private val printJobRepository: PrintJobRepository,
    private val usbPrintRepository: UsbPrintRepository
) {
    suspend operator fun invoke(
        documentName: String,
        documentUri: String,
        documentType: String,
        totalPages: Int,
        settings: PrintSettings
    ): Result<PrintJob> {
        val job = PrintJob(
            documentName = documentName,
            documentUri = documentUri,
            documentType = documentType,
            totalPages = totalPages,
            copies = settings.copies,
            pageRange = settings.getPageRangeString(),
            colorMode = settings.colorMode.value,
            paperSize = settings.paperSize.value,
            orientation = settings.orientation.value,
            duplexMode = settings.duplexMode.value,
            quality = settings.quality.value,
            pagesPerSheet = settings.pagesPerSheet
        )

        // Save job to repository
        val createResult = printJobRepository.createJob(job)
        if (createResult.isFailure) {
            return Result.failure(createResult.exceptionOrNull()!!)
        }

        // Execute print job
        return try {
            printJobRepository.updateJobStatus(job.id, com.cprint.app.domain.model.PrintJobStatus.PRINTING)
            val printResult = usbPrintRepository.sendPrintJob(job, settings)

            if (printResult.isSuccess) {
                printJobRepository.completeJob(job.id)
                Result.success(job.copy(status = com.cprint.app.domain.model.PrintJobStatus.COMPLETED))
            } else {
                printJobRepository.failJob(job.id, printResult.exceptionOrNull()?.message ?: "Print failed")
                Result.failure(printResult.exceptionOrNull() ?: Exception("Print failed"))
            }
        } catch (e: Exception) {
            printJobRepository.failJob(job.id, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
}
