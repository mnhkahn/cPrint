package com.cprint.app.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Helper class for system printing using Android Print Framework
 * Works with Epson Print Enabler and other print services
 */
object SystemPrintHelper {

    /**
     * Print a PDF document using Android Print Framework
     * This requires Epson Print Enabler app to be installed for Epson USB printers
     */
    suspend fun printPdf(
        context: Context,
        documentUri: Uri,
        documentName: String,
        pageCount: Int,
        copies: Int = 1
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

            // Create print adapter
            val printAdapter = object : PrintDocumentAdapter() {
                private var pdfDocument: PdfDocument? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    // Build document info
                    val info = PrintDocumentInfo.Builder(documentName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pageCount)
                        .build()

                    callback?.onLayoutFinished(info, oldAttributes != newAttributes)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onWriteCancelled()
                        return
                    }

                    try {
                        // Copy PDF content to destination
                        context.contentResolver.openFileDescriptor(documentUri, "r")?.use { pfd ->
                            FileInputStream(pfd.fileDescriptor).use { input ->
                                FileOutputStream(destination?.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to write print document")
                        callback?.onWriteFailed(e.message)
                    }
                }
            }

            // Build print attributes
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("default", "default", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            // Start print job
            printManager.print(
                documentName,
                printAdapter,
                printAttributes
            )

            // Note: Android Print Framework doesn't provide direct callback for completion
            // The user needs to select Epson Print Enabler in the print dialog
            continuation.resume(Result.success(Unit))

        } catch (e: Exception) {
            Timber.e(e, "Failed to start system print")
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Check if Epson Print Enabler is installed
     */
    fun isEpsonPrintEnablerInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.epson.printenabler", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get available print services
     */
    fun getAvailablePrintServices(context: Context): List<String> {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        // Note: Getting print services requires reflection or system APIs
        // This is a simplified version
        return listOf("System Print Service")
    }
}
