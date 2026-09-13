package com.cprint.app.presentation.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import com.cprint.app.domain.usecase.print.CancelPrintJobUseCase
import com.cprint.app.domain.usecase.print.CreatePrintJobUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for PrintPreviewScreen
 */
@HiltViewModel
class PrintPreviewViewModel @Inject constructor(
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val createPrintJobUseCase: CreatePrintJobUseCase,
    private val cancelPrintJobUseCase: CancelPrintJobUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrintPreviewUiState>(PrintPreviewUiState.Loading)
    val uiState: StateFlow<PrintPreviewUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

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

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    fun loadDocument(uriString: String) {
        viewModelScope.launch {
            try {
                _uiState.value = PrintPreviewUiState.Loading

                val uri = Uri.parse(uriString)
                val result = openDocumentUseCase(uri)

                if (result.isSuccess) {
                    val document = result.getOrThrow()
                    _totalPages.value = document.pageCount

                    // Load PDF renderer
                    withContext(Dispatchers.IO) {
                        loadPdfRenderer(uri)
                    }

                    _uiState.value = PrintPreviewUiState.Success(
                        documentName = document.name,
                        documentUri = uriString,
                        documentType = document.type,
                        pageCount = document.pageCount
                    )
                } else {
                    _uiState.value = PrintPreviewUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load document"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading document")
                _uiState.value = PrintPreviewUiState.Error(e.message ?: "Unknown error")
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
        if (pageNumber in 0 until _totalPages.value) {
            _currentPage.value = pageNumber
        }
    }

    fun goToNextPage() {
        goToPage(_currentPage.value + 1)
    }

    fun goToPreviousPage() {
        goToPage(_currentPage.value - 1)
    }

    fun updatePrintSettings(settings: PrintSettings) {
        _printSettings.value = settings
    }

    fun print(documentName: String, documentUri: String, documentType: String, pageCount: Int) {
        // Cancel any existing print job
        currentPrintJob?.cancel()

        currentPrintJob = viewModelScope.launch {
            try {
                Timber.d("Starting print job for $documentName")
                _isPrinting.value = true
                _printProgress.value = 0
                _printStatusMessage.value = "准备打印..."

                // Simulate progress updates (in a real app, this would come from the repository)
                val progressJob = launch {
                    var progress = 0
                    while (progress < 100 && _isPrinting.value) {
                        delay(500)
                        progress += 2
                        _printProgress.value = progress.coerceAtMost(99)
                        _printStatusMessage.value = when {
                            progress < 20 -> "正在准备文档..."
                            progress < 50 -> "正在渲染页面..."
                            progress < 80 -> "正在发送数据到打印机..."
                            else -> "正在打印..."
                        }
                    }
                }

                val result = createPrintJobUseCase(
                    documentName = documentName,
                    documentUri = documentUri,
                    documentType = documentType,
                    totalPages = pageCount,
                    settings = _printSettings.value
                )

                progressJob.cancel()

                if (result.isSuccess) {
                    Timber.d("Print job completed successfully")
                    _printProgress.value = 100
                    _printStatusMessage.value = "打印完成"
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
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }
}

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
