package com.cprint.app.service

import android.graphics.pdf.PdfRenderer
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterInfo
import android.print.PrinterId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import androidx.core.content.FileProvider
import com.cprint.app.domain.model.ColorMode
import com.cprint.app.domain.model.DuplexMode
import com.cprint.app.domain.model.KnownPrinters
import com.cprint.app.domain.model.Orientation
import com.cprint.app.domain.model.PaperSize
import com.cprint.app.domain.model.PageRange as CPrintPageRange
import com.cprint.app.domain.model.PrintQuality
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.repository.UsbPrintRepository
import com.cprint.app.domain.usecase.print.CreatePrintJobUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap.newKeySet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Android system print-service bridge for cPrint USB printers.
 *
 * The platform owns the print dialog and passes a rendered PDF to this service
 * after a cPrint printer is selected. We persist that PDF in private cache and
 * send it through the existing cPrint job and USB-driver pipeline.
 */
@AndroidEntryPoint
class CPrintPrintService : PrintService() {

    @Inject lateinit var createPrintJob: CreatePrintJobUseCase
    @Inject lateinit var usbPrintRepository: UsbPrintRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val printerIdByLocalId = ConcurrentHashMap<String, PrinterId>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    // Print framework callbacks are not guaranteed to be delivered only once.
    // Keep this separate from activeJobs so the check-and-add is atomic.
    private val enqueuedSystemJobIds = newKeySet<String>()
    private val printMutex = Mutex()

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession =
        CPrintPrinterDiscoverySession()

    override fun onPrintJobQueued(printJob: PrintJob) {
        val key = printJob.id.toString()
        if (!enqueuedSystemJobIds.add(key)) {
            Timber.w("Ignoring duplicate callback for system print job $key")
            return
        }
        activeJobs[key] = serviceScope.launch {
            printMutex.withLock {
                processPrintJob(printJob)
            }
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        activeJobs.remove(printJob.id.toString())?.cancel()
        serviceScope.launch { usbPrintRepository.cancelPrint() }
    }

    override fun onDestroy() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        enqueuedSystemJobIds.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processPrintJob(systemJob: PrintJob) {
        val key = systemJob.id.toString()
        var cachedPdf: File? = null
        try {
            if (!systemJob.isQueued) return
            systemJob.start()

            cachedPdf = copyDocumentToCache(systemJob)
            val pageCount = getPdfPageCount(cachedPdf)
            val settings = systemJob.info.attributes.toPrintSettings().copy(
                pageRange = systemJob.info.pages.toCPrintPageRange()
            )
            val documentUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                cachedPdf
            )

            val progressJob = serviceScope.launch {
                usbPrintRepository.getPrintProgress().collectLatest { progress ->
                    if (systemJob.isStarted) systemJob.setProgress(progress.coerceIn(0, 1) / 100f)
                }
            }
            try {
                val result = createPrintJob(
                    documentName = systemJob.info.label.ifBlank { "document.pdf" },
                    documentUri = documentUri.toString(),
                    documentType = PDF_MIME_TYPE,
                    totalPages = pageCount,
                    settings = settings
                )
                if (result.isSuccess && !systemJob.isCancelled) {
                    systemJob.setProgress(1f)
                    systemJob.complete()
                } else if (!systemJob.isCancelled) {
                    systemJob.fail(result.exceptionOrNull()?.message ?: "cPrint failed to print the document")
                }
            } finally {
                progressJob.cancel()
            }
        } catch (error: Exception) {
            Timber.e(error, "System print job failed")
            if (!systemJob.isCancelled) {
                systemJob.fail(error.message ?: "Unable to process print job")
            }
        } finally {
            activeJobs.remove(key)
            enqueuedSystemJobIds.remove(key)
            cachedPdf?.delete()
        }
    }

    private fun copyDocumentToCache(systemJob: PrintJob): File {
        val output = File.createTempFile("system-print-", ".pdf", cacheDir)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(systemJob.document.data).use { input ->
                FileOutputStream(output).use { destination -> input.copyTo(destination) }
            }
            check(output.length() > 0L) { "The system print document is empty" }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    private fun getPdfPageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount.coerceAtLeast(1) }
        }

    private fun PrintAttributes.toPrintSettings(): PrintSettings = PrintSettings(
        copies = 1,
        paperSize = mediaSize.toCPrintPaperSize(),
        orientation = if (mediaSize?.isPortrait == false) Orientation.LANDSCAPE else Orientation.PORTRAIT,
        colorMode = if (colorMode == PrintAttributes.COLOR_MODE_MONOCHROME) ColorMode.GRAYSCALE else ColorMode.COLOR,
        duplexMode = when (duplexMode) {
            PrintAttributes.DUPLEX_MODE_LONG_EDGE -> DuplexMode.LONG_EDGE
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE -> DuplexMode.SHORT_EDGE
            else -> DuplexMode.SINGLE
        },
        quality = PrintQuality.NORMAL
    )

    private fun PrintAttributes.MediaSize?.toCPrintPaperSize(): PaperSize = when (this?.id) {
        PrintAttributes.MediaSize.ISO_A3.id -> PaperSize.A3
        PrintAttributes.MediaSize.ISO_A5.id -> PaperSize.A5
        PrintAttributes.MediaSize.NA_LETTER.id -> PaperSize.LETTER
        PrintAttributes.MediaSize.NA_LEGAL.id -> PaperSize.LEGAL
        else -> PaperSize.A4
    }

    private fun Array<PageRange>?.toCPrintPageRange(): CPrintPageRange? {
        if (isNullOrEmpty() || any { it == PageRange.ALL_PAGES }) return null
        val value = joinToString(",") { range ->
            if (range.start == range.end) "${range.start + 1}" else "${range.start + 1}-${range.end + 1}"
        }
        return CPrintPageRange.parse(value)
    }

    private inner class CPrintPrinterDiscoverySession : PrinterDiscoverySession() {
        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
            addPrinters(discoverPrinters())
        }

        override fun onStopPrinterDiscovery() = Unit

        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
            addPrinters(discoverPrinters().filter { it.id in printerIds })
        }

        override fun onStartPrinterStateTracking(printerId: PrinterId) {
            addPrinters(discoverPrinters().filter { it.id == printerId })
        }

        override fun onStopPrinterStateTracking(printerId: PrinterId) = Unit

        override fun onDestroy() {
            printerIdByLocalId.clear()
        }
    }

    private fun discoverPrinters(): List<PrinterInfo> {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        return usbManager.deviceList.values
            .filter(::isSupportedPrinter)
            .map { device ->
                val localId = "${device.vendorId}:${device.productId}:${device.deviceName}"
                val printerId = printerIdByLocalId.getOrPut(localId) { generatePrinterId(localId) }
                val model = KnownPrinters.findPrinter(device.vendorId, device.productId)
                val name = model?.let { "${it.manufacturer} ${it.model}" }
                    ?: (device.productName ?: "USB printer")
                // A physically attached printer must remain selectable. Reporting it
                // as STATUS_UNAVAILABLE when USB permission has not yet been
                // granted makes Android render it in the list but disable taps,
                // leaving the user with no path to start a print job.
                PrinterInfo.Builder(printerId, name, PrinterInfo.STATUS_IDLE)
                    .setDescription(if (usbManager.hasPermission(device)) "Connected via USB" else "Open cPrint once to grant USB access")
                    .setCapabilities(buildCapabilities(printerId))
                    .build()
            }
    }

    private fun isSupportedPrinter(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { index ->
            device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_PRINTER
        } || KnownPrinters.findPrinter(device.vendorId, device.productId) != null

    private fun buildCapabilities(printerId: PrinterId): PrinterCapabilitiesInfo =
        PrinterCapabilitiesInfo.Builder(printerId)
            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
            .addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
            .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
            .addResolution(PrintAttributes.Resolution("cprint-300dpi", "300 DPI", 300, 300), true)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorModes(
                PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_COLOR
            )
            .setDuplexModes(
                PrintAttributes.DUPLEX_MODE_NONE or PrintAttributes.DUPLEX_MODE_LONG_EDGE or PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
                PrintAttributes.DUPLEX_MODE_NONE
            )
            .build()

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
