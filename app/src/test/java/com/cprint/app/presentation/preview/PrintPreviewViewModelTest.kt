package com.cprint.app.presentation.preview

import android.app.Application
import android.content.Context
import android.net.Uri
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
