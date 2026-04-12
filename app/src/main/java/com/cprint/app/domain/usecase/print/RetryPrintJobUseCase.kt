package com.cprint.app.domain.usecase.print

import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.repository.PrintJobRepository
import javax.inject.Inject

/**
 * Use case for retrying a failed print job
 */
class RetryPrintJobUseCase @Inject constructor(
    private val printJobRepository: PrintJobRepository
) {
    suspend operator fun invoke(jobId: String): Result<PrintJob> {
        return printJobRepository.retryJob(jobId)
    }
}
