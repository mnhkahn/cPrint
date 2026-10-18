package com.cprint.app.domain.usecase.print

import com.cprint.app.analytics.AppAnalytics
import com.cprint.app.analytics.PrintSource
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
    private val usbPrintRepository: UsbPrintRepository,
    private val analytics: AppAnalytics
) {
    suspend operator fun invoke(
        documentName: String,
        documentUri: String,
        documentType: String,
        totalPages: Int,
        settings: PrintSettings,
        source: PrintSource = PrintSource.APP
    ): Result<PrintJob> {
        val started = System.nanoTime()
        var stage = "create_job"
        analytics.printRequested(documentType, totalPages, settings, source)
        var failure: Throwable? = null
        try {
            val lease = com.cprint.app.data.repository.ImportedDocumentStore.retain(documentUri)
            val result = try {
                execute(documentName, documentUri, documentType, totalPages, settings) {
                    stage = "processing"
                    analytics.printStarted(documentType, totalPages, settings, source)
                }
            } finally {
                lease.close()
            }
            failure = result.exceptionOrNull()
            return result
        } catch (error: kotlinx.coroutines.CancellationException) {
            failure = error
            throw error
        } catch (error: Exception) {
            failure = error
            return Result.failure(error)
        } finally {
            analytics.printFinished(documentType, totalPages, settings, source, failure,
                if (failure == null) "transfer" else stage, AppAnalytics.elapsedMs(started))
        }
    }

    private suspend fun execute(documentName: String, documentUri: String, documentType: String,
                                totalPages: Int, settings: PrintSettings, onStarted: () -> Unit): Result<PrintJob> {
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
            onStarted()
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                printJobRepository.cancelJob(job.id)
            }
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CreatePrintJobUseCase: Exception during print")
            printJobRepository.failJob(job.id, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
}
