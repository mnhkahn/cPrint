package com.cprint.app.domain.model

import java.util.Date
import java.util.UUID

/**
 * Domain model representing a print job
 */
data class PrintJob(
    val id: String = UUID.randomUUID().toString(),
    val documentName: String,
    val documentUri: String,
    val documentType: String,
    val totalPages: Int,
    val copies: Int = 1,
    val pageRange: String? = null,
    val colorMode: String = ColorMode.COLOR.value,
    val paperSize: String = PaperSize.A4.value,
    val orientation: String = Orientation.PORTRAIT.value,
    val duplexMode: String = DuplexMode.SINGLE.value,
    val quality: String = PrintQuality.NORMAL.value,
    val pagesPerSheet: Int = 1,
    val status: PrintJobStatus = PrintJobStatus.PENDING,
    val progress: Int = 0,
    val createdAt: Date = Date(),
    val startedAt: Date? = null,
    val completedAt: Date? = null,
    val errorMessage: String? = null
) {
    /**
     * Check if job is in a final state
     */
    fun isFinished(): Boolean = status == PrintJobStatus.COMPLETED ||
            status == PrintJobStatus.FAILED ||
            status == PrintJobStatus.CANCELLED

    /**
     * Check if job can be cancelled
     */
    fun canCancel(): Boolean = status == PrintJobStatus.PENDING ||
            status == PrintJobStatus.PRINTING
}

/**
 * Print job status enum
 */
enum class PrintJobStatus {
    PENDING,
    PREPARING,
    PRINTING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Color mode options
 */
enum class ColorMode(val value: String) {
    COLOR("color"),
    GRAYSCALE("grayscale"),
    BLACK_WHITE("black_white")
}

/**
 * Paper size options
 */
enum class PaperSize(val value: String, val widthMm: Float, val heightMm: Float) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    A3("A3", 297f, 420f),
    LETTER("Letter", 216f, 279f),
    LEGAL("Legal", 216f, 356f),
    B5("B5", 176f, 250f)
}

/**
 * Page orientation options
 */
enum class Orientation(val value: String) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape")
}

/**
 * Duplex printing options
 */
enum class DuplexMode(val value: String) {
    SINGLE("single"),
    LONG_EDGE("long_edge"),
    SHORT_EDGE("short_edge")
}

/**
 * Print quality options
 */
enum class PrintQuality(val value: String) {
    DRAFT("draft"),
    NORMAL("normal"),
    HIGH("high"),
    BEST("best")
}
