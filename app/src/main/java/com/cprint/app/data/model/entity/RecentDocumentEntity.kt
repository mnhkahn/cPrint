package com.cprint.app.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.cprint.app.data.local.Converters
import com.cprint.app.domain.model.RecentDocument
import java.util.Date

/**
 * Room entity for recent documents storage
 */
@Entity(tableName = "recent_documents")
@TypeConverters(Converters::class)
data class RecentDocumentEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val uri: String,
    val type: String,
    val size: Long,
    val pageCount: Int,
    val thumbnailUri: String?,
    val lastOpenedAt: Date,
    val openCount: Int
) {
    /**
     * Convert entity to domain model
     */
    fun toDomainModel(): RecentDocument = RecentDocument(
        id = id,
        name = name,
        uri = uri,
        type = type,
        size = size,
        pageCount = pageCount,
        thumbnailUri = thumbnailUri,
        lastOpenedAt = lastOpenedAt,
        openCount = openCount
    )

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(document: RecentDocument): RecentDocumentEntity = RecentDocumentEntity(
            id = document.id,
            name = document.name,
            uri = document.uri,
            type = document.type,
            size = document.size,
            pageCount = document.pageCount,
            thumbnailUri = document.thumbnailUri,
            lastOpenedAt = document.lastOpenedAt,
            openCount = document.openCount
        )
    }
}
