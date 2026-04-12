package com.cprint.app.domain.model

import java.util.Date
import java.util.UUID

/**
 * Domain model representing a recently opened document
 */
data class RecentDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: String,
    val type: String,
    val size: Long,
    val pageCount: Int,
    val thumbnailUri: String? = null,
    val lastOpenedAt: Date = Date(),
    val openCount: Int = 1
) {
    /**
     * Get file extension
     */
    fun getFileExtension(): String {
        return name.substringAfterLast(".", "").lowercase()
    }

    /**
     * Check if document is a PDF
     */
    fun isPdf(): Boolean = type == "application/pdf" || getFileExtension() == "pdf"

    /**
     * Check if document is an image
     */
    fun isImage(): Boolean = type.startsWith("image/") ||
            listOf("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(getFileExtension())

    /**
     * Get formatted file size
     */
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * Document type enum
 */
enum class DocumentType(val mimeType: String) {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
    PNG("image/png"),
    GIF("image/gif"),
    BMP("image/bmp"),
    WEBP("image/webp"),
    UNKNOWN("application/octet-stream")
}
