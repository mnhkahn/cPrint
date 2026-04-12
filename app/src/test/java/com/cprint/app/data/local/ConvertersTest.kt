package com.cprint.app.data.local

import com.cprint.app.domain.model.PrintJobStatus
import com.cprint.app.domain.model.PrinterStatus
import org.junit.Test
import org.junit.Assert.*
import java.util.Date

/**
 * Unit tests for Room Type Converters
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `timestamp to date conversion`() {
        val timestamp = 1609459200000L // 2021-01-01 00:00:00 UTC
        val date = converters.fromTimestamp(timestamp)
        assertNotNull(date)
        assertEquals(timestamp, date?.time)
    }

    @Test
    fun `null timestamp returns null date`() {
        val date = converters.fromTimestamp(null)
        assertNull(date)
    }

    @Test
    fun `date to timestamp conversion`() {
        val date = Date(1609459200000L)
        val timestamp = converters.dateToTimestamp(date)
        assertNotNull(timestamp)
        assertEquals(1609459200000L, timestamp)
    }

    @Test
    fun `null date returns null timestamp`() {
        val timestamp = converters.dateToTimestamp(null)
        assertNull(timestamp)
    }

    @Test
    fun `round trip date conversion`() {
        val originalDate = Date()
        val timestamp = converters.dateToTimestamp(originalDate)
        val convertedDate = converters.fromTimestamp(timestamp)
        assertEquals(originalDate.time, convertedDate?.time)
    }

    @Test
    fun `print job status to string conversion`() {
        assertEquals("PENDING", converters.fromPrintJobStatus(PrintJobStatus.PENDING))
        assertEquals("PRINTING", converters.fromPrintJobStatus(PrintJobStatus.PRINTING))
        assertEquals("COMPLETED", converters.fromPrintJobStatus(PrintJobStatus.COMPLETED))
        assertEquals("FAILED", converters.fromPrintJobStatus(PrintJobStatus.FAILED))
        assertEquals("CANCELLED", converters.fromPrintJobStatus(PrintJobStatus.CANCELLED))
    }

    @Test
    fun `string to print job status conversion`() {
        assertEquals(PrintJobStatus.PENDING, converters.toPrintJobStatus("PENDING"))
        assertEquals(PrintJobStatus.PREPARING, converters.toPrintJobStatus("PREPARING"))
        assertEquals(PrintJobStatus.PRINTING, converters.toPrintJobStatus("PRINTING"))
        assertEquals(PrintJobStatus.PAUSED, converters.toPrintJobStatus("PAUSED"))
        assertEquals(PrintJobStatus.COMPLETED, converters.toPrintJobStatus("COMPLETED"))
        assertEquals(PrintJobStatus.FAILED, converters.toPrintJobStatus("FAILED"))
        assertEquals(PrintJobStatus.CANCELLED, converters.toPrintJobStatus("CANCELLED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid print job status string throws exception`() {
        converters.toPrintJobStatus("INVALID_STATUS")
    }

    @Test
    fun `printer status to string conversion`() {
        assertEquals("DISCONNECTED", converters.fromPrinterStatus(PrinterStatus.DISCONNECTED))
        assertEquals("CONNECTING", converters.fromPrinterStatus(PrinterStatus.CONNECTING))
        assertEquals("READY", converters.fromPrinterStatus(PrinterStatus.READY))
        assertEquals("BUSY", converters.fromPrinterStatus(PrinterStatus.BUSY))
        assertEquals("ERROR", converters.fromPrinterStatus(PrinterStatus.ERROR))
        assertEquals("OFFLINE", converters.fromPrinterStatus(PrinterStatus.OFFLINE))
    }

    @Test
    fun `string to printer status conversion`() {
        assertEquals(PrinterStatus.DISCONNECTED, converters.toPrinterStatus("DISCONNECTED"))
        assertEquals(PrinterStatus.CONNECTING, converters.toPrinterStatus("CONNECTING"))
        assertEquals(PrinterStatus.READY, converters.toPrinterStatus("READY"))
        assertEquals(PrinterStatus.BUSY, converters.toPrinterStatus("BUSY"))
        assertEquals(PrinterStatus.ERROR, converters.toPrinterStatus("ERROR"))
        assertEquals(PrinterStatus.OFFLINE, converters.toPrinterStatus("OFFLINE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid printer status string throws exception`() {
        converters.toPrinterStatus("INVALID_STATUS")
    }

    @Test
    fun `string list to string conversion`() {
        val list = listOf("A4", "A5", "Letter")
        val result = converters.toStringList(list)
        assertEquals("A4,A5,Letter", result)
    }

    @Test
    fun `empty list to empty string`() {
        val result = converters.toStringList(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `string to string list conversion`() {
        val result = converters.fromStringList("A4,A5,Letter")
        assertEquals(listOf("A4", "A5", "Letter"), result)
    }

    @Test
    fun `empty string to empty list`() {
        val result = converters.fromStringList("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `round trip string list conversion`() {
        val original = listOf("A4", "A5", "Letter", "Legal")
        val string = converters.toStringList(original)
        val converted = converters.fromStringList(string)
        assertEquals(original, converted)
    }
}
