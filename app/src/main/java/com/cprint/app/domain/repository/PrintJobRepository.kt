package com.cprint.app.domain.repository

import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintJobStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for print job operations
 */
interface PrintJobRepository {
    /**
     * Get all print jobs
     */
    fun getAllJobs(): Flow<List<PrintJob>>

    /**
     * Get jobs by status
     */
    fun getJobsByStatus(status: PrintJobStatus): Flow<List<PrintJob>>

    /**
     * Get active jobs (pending, preparing, printing)
     */
    fun getActiveJobs(): Flow<List<PrintJob>>

    /**
     * Get job history (completed, failed, cancelled)
     */
    fun getJobHistory(): Flow<List<PrintJob>>

    /**
     * Get a specific job by ID
     */
    suspend fun getJobById(jobId: String): PrintJob?

    /**
     * Create a new print job
     */
    suspend fun createJob(job: PrintJob): Result<PrintJob>

    /**
     * Update job status
     */
    suspend fun updateJobStatus(jobId: String, status: PrintJobStatus, progress: Int = 0): Result<Unit>

    /**
     * Update job progress
     */
    suspend fun updateJobProgress(jobId: String, progress: Int): Result<Unit>

    /**
     * Mark job as completed
     */
    suspend fun completeJob(jobId: String): Result<Unit>

    /**
     * Mark job as failed
     */
    suspend fun failJob(jobId: String, errorMessage: String): Result<Unit>

    /**
     * Cancel a job
     */
    suspend fun cancelJob(jobId: String): Result<Unit>

    /**
     * Delete a job
     */
    suspend fun deleteJob(jobId: String): Result<Unit>

    /**
     * Clear all completed jobs
     */
    suspend fun clearCompletedJobs(): Result<Unit>

    /**
     * Retry a failed job
     */
    suspend fun retryJob(jobId: String): Result<PrintJob>
}
