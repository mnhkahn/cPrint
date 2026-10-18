package com.cprint.app.analytics

import android.content.Context
import android.os.Bundle
import com.cprint.app.domain.model.PrintSettings
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Closed event schema: never send document names, URIs, serial numbers or exception messages. */
@Singleton
class AppAnalytics internal constructor(private val sink: (String, Bundle) -> Unit) {
    @Inject constructor(@ApplicationContext context: Context) : this({ name, params ->
        FirebaseAnalytics.getInstance(context).logEvent(name, params)
    })

    private fun event(name: String, values: Map<String, Any>) {
        try {
            val params = Bundle()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> params.putString(key, value)
                    is Number -> params.putLong(key, value.toLong())
                }
            }
            sink(name, params)
        } catch (_: RuntimeException) {
            // Telemetry must never prevent importing, connecting or printing.
        }
    }

    fun documentResult(type: String?, pages: Int?, error: Throwable?, durationMs: Long, source: DocumentSource) {
        event("document_import_result", mapOf(
            "source" to source.value, "file_type" to fileType(type),
            "page_count" to (pages ?: 0), "duration_ms" to durationMs,
            "result" to outcome(error), "error_code" to errorCode(error)
        ))
    }

    fun connectionResult(vendorId: Int, productId: Int, error: Throwable?, durationMs: Long,
                         permissionGranted: Boolean) {
        event("printer_connect_result", mapOf(
            "vendor_id" to vendorId, "product_id" to productId, "duration_ms" to durationMs,
            "result" to outcome(error), "error_code" to
                if (!permissionGranted) "usb_permission_denied" else errorCode(error)
        ))
    }

    fun usbPermissionDenied(vendorId: Int, productId: Int) =
        connectionResult(vendorId, productId, SecurityException(), 0, false)

    fun printRequested(type: String, pages: Int, settings: PrintSettings, source: PrintSource) =
        event("print_requested", printParams(type, pages, settings, source))

    fun printStarted(type: String, pages: Int, settings: PrintSettings, source: PrintSource) =
        event("print_started", printParams(type, pages, settings, source))

    fun printFinished(type: String, pages: Int, settings: PrintSettings, source: PrintSource,
                      error: Throwable?, stage: String, durationMs: Long) {
        event("print_finished", printParams(type, pages, settings, source) + mapOf(
            "result" to if (error == null) "transfer_success" else outcome(error),
            "stage" to ((error as? PrintPipelineException)?.stage ?: stage),
            "error_code" to errorCode(error), "duration_ms" to durationMs
        ))
    }

    fun printRetry(error: Throwable?) = event("print_retry", mapOf(
        "result" to outcome(error), "error_code" to errorCode(error)
    ))

    private fun printParams(type: String, pages: Int, settings: PrintSettings, source: PrintSource) = mapOf(
        "source" to source.value, "file_type" to fileType(type), "page_count" to pages,
        "copies" to settings.copies, "paper_size" to settings.paperSize.value,
        "color_mode" to settings.colorMode.value, "duplex_mode" to settings.duplexMode.value,
        "pages_per_sheet" to settings.pagesPerSheet
    )

    companion object {
        fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
        internal fun fileType(type: String?): String = when {
            type == "application/pdf" -> "pdf"
            type?.startsWith("image/") == true -> "image"
            else -> "other"
        }
        internal fun outcome(error: Throwable?): String = when {
            error == null -> "success"
            error is CancellationException || errorCode(error) == "cancelled" -> "cancelled"
            else -> "failure"
        }
        internal fun errorCode(error: Throwable?): String {
            if (error == null) return "none"
            if (error is PrintPipelineException) return errorCode(error.cause)
            // Only fixed categories leave the device; messages are inspected locally.
            val message = error.message.orEmpty().lowercase()
            return when {
                error is CancellationException || "cancel" in message || "取消" in message -> "cancelled"
                error is SecurityException || "权限" in message -> "permission_denied"
                error is FileNotFoundException -> "file_not_found"
                "unsupported" in message || "不支持" in message -> "unsupported"
                "未连接" in message || "no usb connection" in message -> "printer_disconnected"
                "timeout" in message || "超时" in message -> "timeout"
                "bulk transfer" in message -> "usb_transfer_failed"
                "过大" in message -> "document_too_large"
                error is IOException -> "io_error"
                error is IllegalArgumentException -> "invalid_input"
                error is IllegalStateException -> "invalid_state"
                else -> "unknown"
            }
        }
    }
}

enum class DocumentSource(val value: String) {
    FILE_PICKER("file_picker"), SHARE("share"), OPEN_WITH("open_with"), RECENT("recent"), UNKNOWN("unknown")
}
enum class PrintSource(val value: String) { APP("app"), SYSTEM("system") }

/** Keeps a local failure stage without exposing the underlying message to Analytics. */
class PrintPipelineException(val stage: String, cause: Throwable) : Exception(cause.message, cause)
