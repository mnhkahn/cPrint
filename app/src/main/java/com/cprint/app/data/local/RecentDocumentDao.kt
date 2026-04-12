package com.cprint.app.data.local

import androidx.room.*
import com.cprint.app.data.model.entity.RecentDocumentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for recent document operations
 */
@Dao
interface RecentDocumentDao {

    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC")
    fun getAllDocuments(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: String): RecentDocumentEntity?

    @Query("SELECT * FROM recent_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): RecentDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: RecentDocumentEntity)

    @Update
    suspend fun updateDocument(document: RecentDocumentEntity)

    @Query("UPDATE recent_documents SET lastOpenedAt = :timestamp, openCount = openCount + 1 WHERE id = :documentId")
    suspend fun updateAccessTime(documentId: String, timestamp: Long)

    @Delete
    suspend fun deleteDocument(document: RecentDocumentEntity)

    @Query("DELETE FROM recent_documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: String)

    @Query("DELETE FROM recent_documents")
    suspend fun clearAllDocuments()

    @Query("SELECT COUNT(*) FROM recent_documents")
    suspend fun getDocumentCount(): Int
}
