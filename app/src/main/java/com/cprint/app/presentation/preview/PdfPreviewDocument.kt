package com.cprint.app.presentation.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/** Owns a seekable PDF and serializes page rendering with resource cleanup. Call on IO. */
internal class PdfPreviewDocument private constructor(
    private val file: File,
    private val renderer: PdfRenderer
) : Closeable {
    val pageCount: Int = renderer.pageCount
    private var closed = false

    @Synchronized
    fun renderPage(index: Int): Bitmap {
        check(!closed) { "预览文档已关闭，请重试" }
        return renderer.openPage(index).use { page ->
            // Bound memory even for unusually large PDF page dimensions.
            val scale = minOf(2f, 2048f / maxOf(page.width, page.height))
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (error: Exception) {
                bitmap.recycle()
                throw error
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            renderer.close() // PdfRenderer owns its descriptor.
        } finally {
            file.delete()
        }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfPreviewDocument {
            val file = File.createTempFile("pdf-preview-", ".pdf", context.cacheDir)
            try {
                checkNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件，请重新选择文档" }.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                check(file.length() > 0) { "文档为空，请重新下载文件" }
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = try {
                    PdfRenderer(descriptor)
                } catch (error: Exception) {
                    descriptor.close()
                    throw error
                }
                if (renderer.pageCount == 0) {
                    renderer.close()
                    error("PDF 中没有可预览的页面")
                }
                return PdfPreviewDocument(file, renderer)
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }
}
