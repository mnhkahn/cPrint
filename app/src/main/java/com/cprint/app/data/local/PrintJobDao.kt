package com.cprint.app.data.local

import androidx.room.*
import com.cprint.app.data.model.entity.PrintJobEntity
import com.cprint.app.domain.model.PrintJobStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for print job operations
 */
@Dao
interface PrintJobDao {

    @Query("SELECT * FROM print_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE status = :status ORDER BY createdAt DESC")
    fun getJobsByStatus(status: PrintJobStatus): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE status IN ('PENDING', 'PREPARING', 'PRINTING') ORDER BY createdAt DESC")
    fun getActiveJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY createdAt DESC")
    fun getJobHistory(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: String): PrintJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: PrintJobEntity)

    @Update
    suspend fun updateJob(job: PrintJobEntity)

    @Query("UPDATE print_jobs SET status = :status, progress = :progress WHERE id = :jobId")
    suspend fun updateJobStatus(jobId: String, status: PrintJobStatus, progress: Int)

    @Query("UPDATE print_jobs SET progress = :progress WHERE id = :jobId")
    suspend fun updateJobProgress(jobId: String, progress: Int)

    @Query("DELETE FROM print_jobs WHERE id = :jobId")
    suspend fun deleteJob(jobId: String)

    @Query("DELETE FROM print_jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearCompletedJobs()

    @Query("SELECT COUNT(*) FROM print_jobs WHERE status IN ('PENDING', 'PREPARING', 'PRINTING')")
    suspend fun getActiveJobCount(): Int
}
