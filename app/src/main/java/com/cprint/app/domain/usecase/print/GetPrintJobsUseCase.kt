package com.cprint.app.domain.usecase.print

import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintJobStatus
import com.cprint.app.domain.repository.PrintJobRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting print jobs
 */
class GetPrintJobsUseCase @Inject constructor(
    private val printJobRepository: PrintJobRepository
) {
    /**
     * Get all print jobs
     */
    operator fun invoke(): Flow<List<PrintJob>> {
        return printJobRepository.getAllJobs()
    }

    /**
     * Get jobs by status
     */
    fun byStatus(status: PrintJobStatus): Flow<List<PrintJob>> {
        return printJobRepository.getJobsByStatus(status)
    }

    /**
     * Get active jobs
     */
    fun active(): Flow<List<PrintJob>> {
        return printJobRepository.getActiveJobs()
    }

    /**
     * Get job history
     */
    fun history(): Flow<List<PrintJob>> {
        return printJobRepository.getJobHistory()
    }

    /**
     * Get job by ID
     */
    suspend fun byId(jobId: String): PrintJob? {
        return printJobRepository.getJobById(jobId)
    }
}
