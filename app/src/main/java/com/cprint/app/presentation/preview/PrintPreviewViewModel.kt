package com.cprint.app.presentation.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    private val openDocumentUseCase: OpenDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrintPreviewUiState>(PrintPreviewUiState.Loading)
    val uiState: StateFlow<PrintPreviewUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _printSettings = MutableStateFlow(PrintSettings())
    val printSettings: StateFlow<PrintSettings> = _printSettings.asStateFlow()

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
