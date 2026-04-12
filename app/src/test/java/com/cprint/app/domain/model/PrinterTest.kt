package com.cprint.app.domain.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Printer class
 */
class PrinterTest {

    @Test
    fun `getDisplayName returns manufacturer and model`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL"
        )
        assertEquals("HP LaserJet Pro M404", printer.getDisplayName())
    }

    @Test
    fun `ready printer is ready`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            status = PrinterStatus.READY
        )
        assertTrue(printer.isReady())
    }

    @Test
    fun `disconnected printer is not ready`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            status = PrinterStatus.DISCONNECTED
        )
        assertFalse(printer.isReady())
    }

    @Test
    fun `busy printer is not ready`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            status = PrinterStatus.BUSY
        )
        assertFalse(printer.isReady())
    }

    @Test
    fun `error printer is not ready`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            status = PrinterStatus.ERROR
        )
        assertFalse(printer.isReady())
    }

    @Test
    fun `supports paper size in list`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            supportedPaperSizes = listOf("A4", "A5", "Letter")
        )
        assertTrue(printer.supportsPaperSize("A4"))
        assertTrue(printer.supportsPaperSize("A5"))
        assertTrue(printer.supportsPaperSize("Letter"))
    }

    @Test
    fun `does not support paper size not in list`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            supportedPaperSizes = listOf("A4", "A5")
        )
        assertFalse(printer.supportsPaperSize("Legal"))
        assertFalse(printer.supportsPaperSize("A3"))
    }

    @Test
    fun `empty paper sizes list supports all sizes`() {
        val printer = Printer(
            name = "HP LaserJet",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL",
            supportedPaperSizes = emptyList()
        )
        assertTrue(printer.supportsPaperSize("A4"))
        assertTrue(printer.supportsPaperSize("A3"))
        assertTrue(printer.supportsPaperSize("Legal"))
    }

    @Test
    fun `printer generates unique id`() {
        val printer1 = Printer(
            name = "HP LaserJet 1",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL"
        )
        val printer2 = Printer(
            name = "HP LaserJet 2",
            manufacturer = "HP",
            model = "LaserJet Pro M404",
            vendorId = 0x03F0,
            productId = 0x2B17,
            protocol = "PCL"
        )
        assertNotEquals(printer1.id, printer2.id)
    }
}
