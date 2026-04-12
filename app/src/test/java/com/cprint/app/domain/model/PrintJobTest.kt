package com.cprint.app.domain.model

import org.junit.Test
import org.junit.Assert.*
import java.util.Date

/**
 * Unit tests for PrintJob class
 */
class PrintJobTest {

    @Test
    fun `new job is not finished`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5
        )
        assertFalse(job.isFinished())
    }

    @Test
    fun `completed job is finished`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.COMPLETED
        )
        assertTrue(job.isFinished())
    }

    @Test
    fun `failed job is finished`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.FAILED
        )
        assertTrue(job.isFinished())
    }

    @Test
    fun `cancelled job is finished`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.CANCELLED
        )
        assertTrue(job.isFinished())
    }

    @Test
    fun `pending job can be cancelled`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.PENDING
        )
        assertTrue(job.canCancel())
    }

    @Test
    fun `printing job can be cancelled`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.PRINTING
        )
        assertTrue(job.canCancel())
    }

    @Test
    fun `completed job cannot be cancelled`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.COMPLETED
        )
        assertFalse(job.canCancel())
    }

    @Test
    fun `failed job cannot be cancelled`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.FAILED
        )
        assertFalse(job.canCancel())
    }

    @Test
    fun `cancelled job cannot be cancelled again`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5,
            status = PrintJobStatus.CANCELLED
        )
        assertFalse(job.canCancel())
    }

    @Test
    fun `job has default values`() {
        val job = PrintJob(
            documentName = "test.pdf",
            documentUri = "file:///test.pdf",
            documentType = "application/pdf",
            totalPages = 5
        )
        assertEquals(1, job.copies)
        assertEquals(ColorMode.COLOR.value, job.colorMode)
        assertEquals(PaperSize.A4.value, job.paperSize)
        assertEquals(Orientation.PORTRAIT.value, job.orientation)
        assertEquals(DuplexMode.SINGLE.value, job.duplexMode)
        assertEquals(PrintQuality.NORMAL.value, job.quality)
        assertEquals(1, job.pagesPerSheet)
        assertEquals(PrintJobStatus.PENDING, job.status)
        assertEquals(0, job.progress)
        assertNotNull(job.createdAt)
    }

    @Test
    fun `job generates unique id`() {
        val job1 = PrintJob(
            documentName = "test1.pdf",
            documentUri = "file:///test1.pdf",
            documentType = "application/pdf",
            totalPages = 5
        )
        val job2 = PrintJob(
            documentName = "test2.pdf",
            documentUri = "file:///test2.pdf",
            documentType = "application/pdf",
            totalPages = 3
        )
        assertNotEquals(job1.id, job2.id)
    }
}
