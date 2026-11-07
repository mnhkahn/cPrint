package com.cprint.app.presentation.preview

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.cprint.app.service.PrintJobService
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.PageRange
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for PrintPreviewScreen
 */
@HiltViewModel
class PrintPreviewViewModel @Inject constructor(
    private val openDocumentUseCase: OpenDocumentUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrintPreviewUiState>(PrintPreviewUiState.Loading)
    val uiState: StateFlow<PrintPreviewUiState> = _uiState.asStateFlow()

    private val _previewPages = MutableStateFlow(PreviewPages())
    internal val previewPages: StateFlow<PreviewPages> = _previewPages.asStateFlow()

    private val _printSettings = MutableStateFlow(PrintSettings())
    val printSettings: StateFlow<PrintSettings> = _printSettings.asStateFlow()

    // Print progress state
    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private val _printProgress = MutableStateFlow(0)
    val printProgress: StateFlow<Int> = _printProgress.asStateFlow()

    private val _printStatusMessage = MutableStateFlow("")
    val printStatusMessage: StateFlow<String> = _printStatusMessage.asStateFlow()

    private var currentPrintJob: kotlinx.coroutines.Job? = null

    private var loadJob: kotlinx.coroutines.Job? = null
    private var sourceUri: String? = null
    private var documentLease: AutoCloseable? = null

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    fun loadDocument(uriString: String, source: com.cprint.app.analytics.DocumentSource = com.cprint.app.analytics.DocumentSource.UNKNOWN) {
        loadJob?.cancel()
        if (sourceUri != uriString) {
            sourceUri = uriString
            _printSettings.value = _printSettings.value.copy(pageRange = null)
            _previewPages.value = PreviewPages()
        }
        loadJob = viewModelScope.launch {
            try {
                documentLease?.close()
                documentLease = null
                _uiState.value = PrintPreviewUiState.Loading

                val uri = Uri.parse(uriString)
                val result = openDocumentUseCase(uri, source)

                if (result.isSuccess) {
                    val document = result.getOrThrow()
                    documentLease = com.cprint.app.data.repository.ImportedDocumentStore.retain(document.uri)
                    updatePreviewPages(document.pageCount)

                    _uiState.value = PrintPreviewUiState.Success(
                        documentName = document.name,
                        documentUri = document.uri,
                        documentType = document.type,
                        pageCount = document.pageCount
                    )
                } else {
                    _uiState.value = PrintPreviewUiState.Error(
                        previewErrorMessage(result.exceptionOrNull() ?: IllegalStateException("无法打开文档"))
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading document")
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = PrintPreviewUiState.Error(previewErrorMessage(e))
            }
        }
    }

    fun renderPage(pageNumber: Int): Bitmap? {
        return try {
            val renderer = pdfRenderer ?: return null
            if (pageNumber < 0 || pageNumber >= renderer.pageCount) return null

            renderer.openPage(pageNumber).use { page ->
                val bitmap = Bitmap.createBitmap(
                    page.width,
                    page.height,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "Error rendering page $pageNumber")
            null
        }
    }

    fun goToPage(pageNumber: Int) {
        val pages = _previewPages.value
        if (pageNumber in pages.indexes) {
            _previewPages.value = pages.copy(currentPage = pageNumber)
        }
    }

    fun updatePageCount(pageCount: Int) {
        if (pageCount <= 0) return
        updatePreviewPages(pageCount)
        val state = _uiState.value
        if (state is PrintPreviewUiState.Success) {
            _uiState.value = state.copy(pageCount = pageCount)
        }
    }

    private fun updatePreviewPages(pageCount: Int = _previewPages.value.documentPageCount) {
        val selection = _printSettings.value.pageRange
        val indexes = if (selection == null) {
            (0 until pageCount).toList()
        } else {
            try {
                selection.toPageIndexes(pageCount)
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        }
        val current = _previewPages.value.currentPage
        _previewPages.value = PreviewPages(
            documentPageCount = pageCount,
            indexes = indexes,
            currentPage = current?.takeIf { it in indexes } ?: indexes.firstOrNull()
        )
    }

    fun updatePrintSettings(settings: PrintSettings) {
        val selectionChanged = _printSettings.value.pageRange != settings.pageRange
        _printSettings.value = settings
        if (selectionChanged) updatePreviewPages()
    }

    fun updatePrintPageRange(pageRange: PageRange?) {
        updatePrintSettings(_printSettings.value.copy(pageRange = pageRange))
    }

    fun print(documentName: String, documentUri: String, documentType: String, pageCount: Int) {
        if (_isPrinting.value) return

        currentPrintJob = viewModelScope.launch {
            try {
                Timber.d("Starting print job for $documentName")
                _isPrinting.value = true
                _printProgress.value = 0
                _printStatusMessage.value = "准备打印..."

                val result = PrintJobService.print(
                    context = context,
                    documentName = documentName,
                    documentUri = documentUri,
                    documentType = documentType,
                    totalPages = pageCount,
                    settings = _printSettings.value
                ) { progress, waiting ->
                    _printProgress.value = progress.coerceAtMost(99)
                    _printStatusMessage.value = when {
                        waiting -> "等待打印机继续接收数据…"
                        progress > 0 -> "正在发送数据到打印机…"
                        else -> "正在准备打印数据…"
                    }
                }

                if (result.isSuccess) {
                    Timber.d("Print job completed successfully")
                    _printProgress.value = 100
                    _printStatusMessage.value = "已发送到打印机"
                    // Keep dialog visible for 2 seconds so user can see completion
                    delay(2000)
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Print job failed")
                    _printProgress.value = 0
                    _printStatusMessage.value = "打印失败: ${error?.message ?: "未知错误"}"
                    // Keep dialog visible for 3 seconds to show error
                    delay(3000)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Timber.d("Print job cancelled")
                    _printStatusMessage.value = "打印已取消"
                } else {
                    Timber.e(e, "Error executing print job")
                    _printProgress.value = 0
                    _printStatusMessage.value = "打印错误: ${e.message ?: "未知错误"}"
                }
                // Keep dialog visible for 3 seconds to show error
                delay(3000)
            } finally {
                _isPrinting.value = false
                currentPrintJob = null
            }
        }
    }

    fun cancelPrint() {
        viewModelScope.launch {
            Timber.d("Cancelling print job")
            PrintJobService.cancel(context)
            currentPrintJob?.cancel()
            _isPrinting.value = false
            _printProgress.value = 0
            _printStatusMessage.value = "打印已取消"
        }
    }

    private fun loadPdfRenderer(uri: Uri) {
        try {
            // This would need context to open the file descriptor
            // For now, we'll handle this in the composable
        } catch (e: Exception) {
            Timber.e(e, "Error loading PDF renderer")
        }
    }

    override fun onCleared() {
        super.onCleared()
        documentLease?.close()
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }
}

internal data class PreviewPages(
    val documentPageCount: Int = 0,
    val indexes: List<Int> = emptyList(),
    val currentPage: Int? = null
)

/**
 * UI State for PrintPreviewScreen
 */
sealed class PrintPreviewUiState {
    object Loading : PrintPreviewUiState()
    data class Success(
        val documentName: String,
        val documentUri: String,
        val documentType: String,
        val pageCount: Int
    ) : PrintPreviewUiState()
    data class Error(val message: String) : PrintPreviewUiState()
}
