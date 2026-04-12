package com.cprint.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * Utility class for PDF operations
 */
object PdfUtils {

    /**
     * Get the number of pages in a PDF document
     */
    suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var pageCount = 0
        var pdfRenderer: PdfRenderer? = null
        var parcelFileDescriptor: ParcelFileDescriptor? = null

        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.let { pfd ->
                pdfRenderer = PdfRenderer(pfd)
                pageCount = pdfRenderer?.pageCount ?: 0
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting PDF page count")
        } finally {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }

        pageCount
    }

    /**
     * Render a specific page of a PDF to a bitmap
     */
    suspend fun renderPage(
        context: Context,
        uri: Uri,
        pageNumber: Int,
        scale: Float = 1.0f
    ): Bitmap? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        var pdfRenderer: PdfRenderer? = null
        var parcelFileDescriptor: ParcelFileDescriptor? = null

        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.let { pfd ->
                pdfRenderer = PdfRenderer(pfd)

                val renderer = pdfRenderer
                if (renderer != null && pageNumber in 0 until renderer.pageCount) {
                    renderer.openPage(pageNumber).use { page ->
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()

                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error rendering PDF page")
        } finally {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }

        bitmap
    }

    /**
     * Check if a URI points to a valid PDF file
     */
    fun isValidPdf(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0 } ?: false
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Get PDF metadata
     */
    suspend fun getPdfMetadata(context: Context, uri: Uri): PdfMetadata = withContext(Dispatchers.IO) {
        var pageCount = 0
        var isPasswordProtected = false

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                try {
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                } catch (e: SecurityException) {
                    isPasswordProtected = true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting PDF metadata")
        }

        PdfMetadata(pageCount, isPasswordProtected)
    }

    /**
     * Data class for PDF metadata
     */
    data class PdfMetadata(
        val pageCount: Int,
        val isPasswordProtected: Boolean
    )
}
