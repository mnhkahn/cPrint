package com.cprint.app.data.repository

import com.cprint.app.data.local.PrintJobDao
import com.cprint.app.data.model.entity.PrintJobEntity
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintJobStatus
import com.cprint.app.domain.repository.PrintJobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PrintJobRepository
 */
@Singleton
class PrintJobRepositoryImpl @Inject constructor(
    private val printJobDao: PrintJobDao
) : PrintJobRepository {

    override fun getAllJobs(): Flow<List<PrintJob>> {
        return printJobDao.getAllJobs().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getJobsByStatus(status: PrintJobStatus): Flow<List<PrintJob>> {
        return printJobDao.getJobsByStatus(status).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getActiveJobs(): Flow<List<PrintJob>> {
        return printJobDao.getActiveJobs().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getJobHistory(): Flow<List<PrintJob>> {
        return printJobDao.getJobHistory().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getJobById(jobId: String): PrintJob? {
        return printJobDao.getJobById(jobId)?.toDomainModel()
    }

    override suspend fun createJob(job: PrintJob): Result<PrintJob> {
        return try {
            printJobDao.insertJob(PrintJobEntity.fromDomainModel(job))
            Result.success(job)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateJobStatus(
        jobId: String,
        status: PrintJobStatus,
        progress: Int
    ): Result<Unit> {
        return try {
            printJobDao.updateJobStatus(jobId, status, progress)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateJobProgress(jobId: String, progress: Int): Result<Unit> {
        return try {
            printJobDao.updateJobProgress(jobId, progress)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeJob(jobId: String): Result<Unit> {
        return try {
            val job = printJobDao.getJobById(jobId)
            job?.let {
                val updatedJob = it.copy(
                    status = PrintJobStatus.COMPLETED,
                    progress = 100,
                    completedAt = Date()
                )
                printJobDao.updateJob(updatedJob)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun failJob(jobId: String, errorMessage: String): Result<Unit> {
        return try {
            val job = printJobDao.getJobById(jobId)
            job?.let {
                val updatedJob = it.copy(
                    status = PrintJobStatus.FAILED,
                    completedAt = Date(),
                    errorMessage = errorMessage
                )
                printJobDao.updateJob(updatedJob)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelJob(jobId: String): Result<Unit> {
        return try {
            val job = printJobDao.getJobById(jobId)
            job?.let {
                val updatedJob = it.copy(
                    status = PrintJobStatus.CANCELLED,
                    completedAt = Date()
                )
                printJobDao.updateJob(updatedJob)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteJob(jobId: String): Result<Unit> {
        return try {
            printJobDao.deleteJob(jobId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearCompletedJobs(): Result<Unit> {
        return try {
            printJobDao.clearCompletedJobs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun retryJob(jobId: String): Result<PrintJob> {
        return try {
            val job = printJobDao.getJobById(jobId)
            job?.let {
                val newJob = it.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    status = PrintJobStatus.PENDING,
                    progress = 0,
                    createdAt = Date(),
                    startedAt = null,
                    completedAt = null,
                    errorMessage = null
                )
                printJobDao.insertJob(newJob)
                Result.success(newJob.toDomainModel())
            } ?: Result.failure(IllegalArgumentException("Job not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
