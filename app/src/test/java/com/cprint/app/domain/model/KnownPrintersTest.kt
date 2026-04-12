package com.cprint.app.domain.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for KnownPrinters object
 */
class KnownPrintersTest {

    @Test
    fun `find HP printer by exact VID PID`() {
        val printer = KnownPrinters.findPrinter(0x03F0, 0x2B17)
        assertNotNull(printer)
        assertEquals("HP", printer?.manufacturer)
        assertEquals("LaserJet Pro M404", printer?.model)
        assertEquals("PCL", printer?.protocol)
    }

    @Test
    fun `find Epson printer by exact VID PID`() {
        val printer = KnownPrinters.findPrinter(0x04B8, 0x08A9)
        assertNotNull(printer)
        assertEquals("Epson", printer?.manufacturer)
        assertEquals("L3150", printer?.model)
        assertEquals("ESC/P", printer?.protocol)
    }

    @Test
    fun `find Canon printer by exact VID PID`() {
        val printer = KnownPrinters.findPrinter(0x04A9, 0x179C)
        assertNotNull(printer)
        assertEquals("Canon", printer?.manufacturer)
        assertEquals("PIXMA G3020", printer?.model)
        assertEquals("GDI", printer?.protocol)
    }

    @Test
    fun `find Brother printer by exact VID PID`() {
        val printer = KnownPrinters.findPrinter(0x04F9, 0x032C)
        assertNotNull(printer)
        assertEquals("Brother", printer?.manufacturer)
        assertEquals("HL-L2350DW", printer?.model)
        assertEquals("PCL", printer?.protocol)
    }

    @Test
    fun `find printer by vendor ID fallback`() {
        // Unknown product ID but known vendor
        val printer = KnownPrinters.findPrinter(0x03F0, 0x9999)
        assertNotNull(printer)
        assertEquals("HP", printer?.manufacturer)
        assertEquals("LaserJet", printer?.model)
    }

    @Test
    fun `return null for unknown vendor`() {
        val printer = KnownPrinters.findPrinter(0xFFFF, 0x0000)
        assertNull(printer)
    }

    @Test
    fun `all known printers have valid data`() {
        KnownPrinters.KNOWN_PRINTERS.forEach { printer ->
            assertTrue("Vendor ID should be positive", printer.vendorId > 0)
            assertTrue("Product ID should be non-negative", printer.productId >= 0)
            assertTrue("Manufacturer should not be empty", printer.manufacturer.isNotEmpty())
            assertTrue("Model should not be empty", printer.model.isNotEmpty())
            assertTrue("Protocol should not be empty", printer.protocol.isNotEmpty())
        }
    }

    @Test
    fun `known printers list is not empty`() {
        assertTrue(KnownPrinters.KNOWN_PRINTERS.isNotEmpty())
    }

    @Test
    fun `protocol types are valid`() {
        val validProtocols = listOf("PCL", "ESC/P", "PostScript", "GDI", "PDF", "IPP")
        KnownPrinters.KNOWN_PRINTERS.forEach { printer ->
            assertTrue(
                "Protocol ${printer.protocol} should be valid",
                validProtocols.contains(printer.protocol)
            )
        }
    }
}
