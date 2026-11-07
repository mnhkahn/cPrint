package com.cprint.app.presentation.preview

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.PageRange
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import com.cprint.app.service.PrintJobService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PrintPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun `preview and print use imported URI instead of temporary browser URI`() = runTest(dispatcher) {
        val source = Uri.parse("content://media/external/downloads/1")
        val stored = "content://com.cprint.app.fileprovider/internal_files/imported-documents/1"
        val open = mock<OpenDocumentUseCase>()
        whenever(open(source)).thenReturn(Result.success(RecentDocument(name = "test.pdf", uri = stored,
            type = "application/pdf", size = 100, pageCount = 1)))
        val model = PrintPreviewViewModel(open, mock<Context>())
        model.loadDocument(source.toString())
        runCurrent()
        assertEquals(stored, (model.uiState.value as PrintPreviewUiState.Success).documentUri)
    }

    @Test fun `expired authorization shows actionable file error`() = runTest(dispatcher) {
        val source = Uri.parse("content://media/external/downloads/1")
        val open = mock<OpenDocumentUseCase>()
        whenever(open(source)).thenReturn(Result.failure(SecurityException("denied")))
        val model = PrintPreviewViewModel(open, mock<Context>())
        model.loadDocument(source.toString())
        runCurrent()
        assertTrue((model.uiState.value as PrintPreviewUiState.Error).message.contains("重新选择"))
    }

    @Test fun `selecting the second page updates both preview and print job`() = runTest(dispatcher) {
        val context = mock<Context>()
        val model = PrintPreviewViewModel(mock(), context)
        model.updatePageCount(2)
        model.goToPage(1)
        assertNull(model.printSettings.value.pageRange)
        model.goToPage(0)
        val selection = requireNotNull(PageRange.parseSelection("2", totalPages = 2))
        model.updatePrintPageRange(selection)
        assertEquals(listOf(1), model.previewPages.value.indexes)
        model.goToPage(0)
        assertEquals(1, model.previewPages.value.currentPage)
        try {
            model.print("test.pdf", "content://example/test.pdf", "application/pdf", 2)
            runCurrent()
            val intent = argumentCaptor<Intent>()
            verify(context).startForegroundService(intent.capture())
            val settings = Gson().fromJson(
                intent.firstValue.getStringExtra(PrintJobService.EXTRA_SETTINGS),
                PrintSettings::class.java
            )
            assertEquals(listOf(1), settings.pageRange?.toPageIndexes(totalPages = 2))
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
        }
    }

    @Test fun `changing the range keeps the current page when it is still selected`() = runTest(dispatcher) {
        val model = PrintPreviewViewModel(mock(), mock<Context>())
        model.updatePageCount(5)
        model.goToPage(3)
        model.updatePrintPageRange(PageRange.parseSelection("2,4", totalPages = 5))
        assertEquals(listOf(1, 3), model.previewPages.value.indexes)
        assertEquals(3, model.previewPages.value.currentPage)

        model.updatePrintPageRange(null)
        assertEquals((0 until 5).toList(), model.previewPages.value.indexes)
        assertEquals(3, model.previewPages.value.currentPage)
    }
}
