package com.cprint.app.presentation.main

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.DocumentRepository
import com.cprint.app.domain.repository.PrinterRepository
import com.cprint.app.domain.usecase.document.GetRecentDocumentsUseCase
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import com.cprint.app.domain.usecase.printer.GetConnectedPrinterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val printers = mock<PrinterRepository>()
    private val documents = mock<DocumentRepository>()
    private val recent = RecentDocument(name = "test.pdf", uri = "content://test.pdf", type = "application/pdf", size = 100, pageCount = 1)
    private val documentFlow = MutableStateFlow(listOf(recent))
    private lateinit var model: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        whenever(printers.getConnectedPrinter()).thenReturn(flowOf(null))
        whenever(documents.getRecentDocuments(10)).thenReturn(documentFlow)
        model = MainViewModel(GetConnectedPrinterUseCase(printers), GetRecentDocumentsUseCase(documents),
            OpenDocumentUseCase(documents), printers, mock<Context>())
        dispatcher.scheduler.runCurrent()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `no printer and denied optional permissions still show recent documents`() {
        model.onPermissionsDenied()
        dispatcher.scheduler.runCurrent()
        val state = model.uiState.value as MainUiState.Success
        assertFalse(state.isPrinterConnected)
        assertNull(state.connectionError)
        assertEquals(listOf(recent), state.recentDocuments)
    }

    @Test fun `connection failure stays local and does not replace document list`() = runTest(dispatcher) {
        val device = mock<UsbDevice>()
        whenever(printers.connectPrinter(device)).thenReturn(Result.failure(IllegalStateException("无法打开 USB 打印机")))
        model.connectPrinter(device)
        runCurrent()
        val state = model.uiState.value as MainUiState.Success
        assertEquals("无法打开 USB 打印机", state.connectionError)
        assertEquals(listOf(recent), state.recentDocuments)
        documentFlow.value = listOf(recent, recent.copy(id = "second"))
        runCurrent()
        assertEquals(2, (model.uiState.value as MainUiState.Success).recentDocuments.size)
        model.onPrinterDisconnected()
        runCurrent()
        assertNull((model.uiState.value as MainUiState.Success).connectionError)
        assertFalse((model.uiState.value as MainUiState.Success).isPrinterConnected)
    }
}
