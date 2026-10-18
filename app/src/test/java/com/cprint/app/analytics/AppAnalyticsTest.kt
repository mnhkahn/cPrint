package com.cprint.app.analytics

import android.app.Application
import android.net.Uri
import android.os.Bundle
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.DocumentRepository
import com.cprint.app.domain.repository.PrintJobRepository
import com.cprint.app.domain.repository.UsbPrintRepository
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import com.cprint.app.domain.usecase.print.CreatePrintJobUseCase
import com.cprint.app.domain.usecase.print.RetryPrintJobUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppAnalyticsTest {
    private val events = mutableListOf<Pair<String, Bundle>>()
    private val analytics = AppAnalytics { name, params -> events.add(name to params) }
    private val jobs: PrintJobRepository = mock()
    private val usb: UsbPrintRepository = mock()
    private val settings = PrintSettings()
    private val job = PrintJob(documentName = "private-bank-statement.pdf",
        documentUri = "content://private/secret", documentType = "application/pdf", totalPages = 2)

    private suspend fun print(source: PrintSource = PrintSource.APP) =
        CreatePrintJobUseCase(jobs, usb, analytics)(job.documentName, job.documentUri,
            job.documentType, job.totalPages, settings, source)

    private suspend fun prepareJob() {
        whenever(jobs.createJob(any())).thenReturn(Result.success(job))
        whenever(jobs.updateJobStatus(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(jobs.completeJob(any())).thenReturn(Result.success(Unit))
        whenever(jobs.failJob(any(), any())).thenReturn(Result.success(Unit))
        whenever(jobs.cancelJob(any())).thenReturn(Result.success(Unit))
    }

    @Test fun `system print emits one lifecycle and only claims transfer success`() = runBlocking<Unit> {
        prepareJob()
        whenever(usb.sendPrintJob(any(), any())).thenReturn(Result.success(Unit))
        assertTrue(print(PrintSource.SYSTEM).isSuccess)
        assertEquals(listOf("print_requested", "print_started", "print_finished"), events.map { it.first })
        assertTrue(events.all { it.second.getString("source") == "system" })
        assertEquals("transfer_success", events.last().second.getString("result"))
        val values = events.flatMap { it.second.keySet().map { key -> it.second.get(key).toString() } }
        assertFalse(values.any { "private" in it || "secret" in it })
    }

    @Test fun `creation failure finishes once without starting USB`() = runBlocking<Unit> {
        whenever(jobs.createJob(any())).thenReturn(Result.failure(IllegalStateException("private path")))
        assertTrue(print().isFailure)
        assertEquals(listOf("print_requested", "print_finished"), events.map { it.first })
        assertEquals("create_job", events.last().second.getString("stage"))
        verifyNoInteractions(usb)
    }

    @Test fun `render failure preserves stage and sends only fixed error category`() = runBlocking<Unit> {
        prepareJob()
        whenever(usb.sendPrintJob(any(), any())).thenReturn(Result.failure(
            PrintPipelineException("render", java.io.FileNotFoundException("/private/name.pdf"))))
        assertTrue(print().isFailure)
        assertEquals("render", events.last().second.getString("stage"))
        assertEquals("file_not_found", events.last().second.getString("error_code"))
        assertEquals(1, events.count { it.first == "print_finished" })
    }

    @Test fun `coroutine cancellation is rethrown and has one cancelled terminal event`() = runBlocking<Unit> {
        prepareJob()
        whenever(usb.sendPrintJob(any(), any())).thenThrow(CancellationException("cancelled"))
        try {
            print()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals("cancelled", events.last().second.getString("result"))
        assertEquals(1, events.count { it.first == "print_finished" })
        verify(jobs).cancelJob(any())
    }

    @Test fun `explicit printer cancellation returned as failure is counted as cancelled`() = runBlocking<Unit> {
        prepareJob()
        whenever(usb.sendPrintJob(any(), any())).thenReturn(Result.failure(
            PrintPipelineException("transfer", IllegalStateException("打印已取消"))))
        print()
        assertEquals("cancelled", events.last().second.getString("result"))
    }

    @Test fun `analytics failure cannot break printing`() = runBlocking<Unit> {
        prepareJob()
        whenever(usb.sendPrintJob(any(), any())).thenReturn(Result.success(Unit))
        val broken = AppAnalytics { _, _ -> throw IllegalStateException("SDK unavailable") }
        assertTrue(CreatePrintJobUseCase(jobs, usb, broken)(job.documentName, job.documentUri,
            job.documentType, 2, settings).isSuccess)
    }

    @Test fun `import records source and type without filename or URI`() = runBlocking<Unit> {
        val documents = mock<DocumentRepository>()
        val uri = Uri.parse(job.documentUri)
        whenever(documents.openDocument(uri)).thenReturn(Result.success(RecentDocument(
            name = job.documentName, uri = job.documentUri, type = job.documentType, size = 123, pageCount = 2)))
        assertTrue(OpenDocumentUseCase(documents, analytics)(uri, DocumentSource.SHARE).isSuccess)
        val params = events.single().second
        assertEquals("share", params.getString("source"))
        assertEquals("pdf", params.getString("file_type"))
        assertEquals(2L, params.getLong("page_count"))
        assertFalse(params.containsKey("document_name"))
        assertFalse(params.containsKey("document_uri"))
    }

    @Test fun `failed import reports permission error`() = runBlocking<Unit> {
        val documents = mock<DocumentRepository>()
        val uri = Uri.parse(job.documentUri)
        whenever(documents.openDocument(uri)).thenReturn(Result.failure(SecurityException("private")))
        assertTrue(OpenDocumentUseCase(documents, analytics)(uri).isFailure)
        assertEquals("permission_denied", events.single().second.getString("error_code"))
    }

    @Test fun `retry records queue result without pretending to print`() = runBlocking<Unit> {
        whenever(jobs.retryJob(any())).thenReturn(Result.success(job))
        RetryPrintJobUseCase(jobs, analytics)("private-job-id")
        assertEquals(listOf("print_retry"), events.map { it.first })
        assertEquals("success", events.single().second.getString("result"))
    }
}
