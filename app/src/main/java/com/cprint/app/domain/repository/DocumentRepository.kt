package com.cprint.app.domain.repository

import android.net.Uri
import com.cprint.app.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for document operations
 */
interface DocumentRepository {
    /**
     * Get all recent documents
     */
    fun getRecentDocuments(): Flow<List<RecentDocument>>

    /**
     * Get recent documents with limit
     */
    fun getRecentDocuments(limit: Int): Flow<List<RecentDocument>>

    /**
     * Get document by ID
     */
    suspend fun getDocumentById(documentId: String): RecentDocument?

    /**
     * Get document by URI
     */
    suspend fun getDocumentByUri(uri: String): RecentDocument?

    /**
     * Add or update a recent document
     */
    suspend fun addRecentDocument(document: RecentDocument): Result<Unit>

    /**
     * Open a document and update access time
     */
    suspend fun openDocument(uri: Uri): Result<RecentDocument>

    /**
     * Get document info from URI
     */
    suspend fun getDocumentInfo(uri: Uri): Result<DocumentInfo>

    /**
     * Get PDF page count
     */
    suspend fun getPdfPageCount(uri: Uri): Result<Int>

    /**
     * Generate thumbnail for document
     */
    suspend fun generateThumbnail(uri: Uri, pageNumber: Int = 0): Result<String>

    /**
     * Delete a recent document
     */
    suspend fun deleteDocument(documentId: String): Result<Unit>

    /**
     * Clear all recent documents
     */
    suspend fun clearRecentDocuments(): Result<Unit>

    /**
     * Check if file type is supported
     */
    fun isSupportedFileType(mimeType: String): Boolean
}

/**
 * Document information data class
 */
data class DocumentInfo(
    val name: String,
    val uri: String,
    val type: String,
    val size: Long,
    val pageCount: Int = 1
)
