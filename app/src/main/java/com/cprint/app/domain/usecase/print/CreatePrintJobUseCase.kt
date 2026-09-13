package com.cprint.app.domain.usecase.print

import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.repository.PrintJobRepository
import com.cprint.app.domain.repository.UsbPrintRepository
import timber.log.Timber
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
        Timber.d("CreatePrintJobUseCase: Creating print job for $documentName")

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
        Timber.d("CreatePrintJobUseCase: PrintJob created with id=${job.id}")

        // Save job to repository
        val createResult = printJobRepository.createJob(job)
        if (createResult.isFailure) {
            Timber.e("CreatePrintJobUseCase: Failed to create job in repository")
            return Result.failure(createResult.exceptionOrNull()!!)
        }
        Timber.d("CreatePrintJobUseCase: Job saved to repository")

        // Execute print job
        return try {
            Timber.d("CreatePrintJobUseCase: Starting USB print...")
            printJobRepository.updateJobStatus(job.id, com.cprint.app.domain.model.PrintJobStatus.PRINTING)
            val printResult = usbPrintRepository.sendPrintJob(job, settings)

            if (printResult.isSuccess) {
                Timber.d("CreatePrintJobUseCase: USB print successful")
                printJobRepository.completeJob(job.id)
                Result.success(job.copy(status = com.cprint.app.domain.model.PrintJobStatus.COMPLETED))
            } else {
                val error = printResult.exceptionOrNull()
                Timber.e("CreatePrintJobUseCase: USB print failed: ${error?.message}")
                printJobRepository.failJob(job.id, error?.message ?: "Print failed")
                Result.failure(error ?: Exception("Print failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "CreatePrintJobUseCase: Exception during print")
            printJobRepository.failJob(job.id, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
}
