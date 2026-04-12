package com.cprint.app.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.cprint.app.data.local.Converters
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintJobStatus
import java.util.Date

/**
 * Room entity for print job storage
 */
@Entity(tableName = "print_jobs")
@TypeConverters(Converters::class)
data class PrintJobEntity(
    @PrimaryKey
    val id: String,
    val documentName: String,
    val documentUri: String,
    val documentType: String,
    val totalPages: Int,
    val copies: Int,
    val pageRange: String?,
    val colorMode: String,
    val paperSize: String,
    val orientation: String,
    val duplexMode: String,
    val quality: String,
    val pagesPerSheet: Int,
    val status: PrintJobStatus,
    val progress: Int,
    val createdAt: Date,
    val startedAt: Date?,
    val completedAt: Date?,
    val errorMessage: String?
) {
    /**
     * Convert entity to domain model
     */
    fun toDomainModel(): PrintJob = PrintJob(
        id = id,
        documentName = documentName,
        documentUri = documentUri,
        documentType = documentType,
        totalPages = totalPages,
        copies = copies,
        pageRange = pageRange,
        colorMode = colorMode,
        paperSize = paperSize,
        orientation = orientation,
        duplexMode = duplexMode,
        quality = quality,
        pagesPerSheet = pagesPerSheet,
        status = status,
        progress = progress,
        createdAt = createdAt,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage
    )

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(job: PrintJob): PrintJobEntity = PrintJobEntity(
            id = job.id,
            documentName = job.documentName,
            documentUri = job.documentUri,
            documentType = job.documentType,
            totalPages = job.totalPages,
            copies = job.copies,
            pageRange = job.pageRange,
            colorMode = job.colorMode,
            paperSize = job.paperSize,
            orientation = job.orientation,
            duplexMode = job.duplexMode,
            quality = job.quality,
            pagesPerSheet = job.pagesPerSheet,
            status = job.status,
            progress = job.progress,
            createdAt = job.createdAt,
            startedAt = job.startedAt,
            completedAt = job.completedAt,
            errorMessage = job.errorMessage
        )
    }
}
