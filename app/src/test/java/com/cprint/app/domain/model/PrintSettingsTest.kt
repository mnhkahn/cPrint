package com.cprint.app.domain.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PrintSettings class
 */
class PrintSettingsTest {

    @Test
    fun `default settings are valid`() {
        val settings = PrintSettings()
        assertTrue(settings.isValid())
        assertEquals(PaperSize.A4, settings.paperSize)
        assertEquals(Orientation.PORTRAIT, settings.orientation)
        assertEquals(ColorMode.COLOR, settings.colorMode)
        assertEquals(1, settings.copies)
        assertEquals(DuplexMode.SINGLE, settings.duplexMode)
        assertEquals(PrintQuality.NORMAL, settings.quality)
        assertEquals(1, settings.pagesPerSheet)
    }

    @Test
    fun `valid copies range`() {
        val settings1 = PrintSettings(copies = 1)
        assertTrue(settings1.isValid())

        val settings2 = PrintSettings(copies = 99)
        assertTrue(settings2.isValid())

        val settings3 = PrintSettings(copies = 50)
        assertTrue(settings3.isValid())
    }

    @Test
    fun `invalid copies range`() {
        val settings1 = PrintSettings(copies = 0)
        assertFalse(settings1.isValid())

        val settings2 = PrintSettings(copies = 100)
        assertFalse(settings2.isValid())

        val settings3 = PrintSettings(copies = -1)
        assertFalse(settings3.isValid())
    }

    @Test
    fun `valid pages per sheet`() {
        val settings1 = PrintSettings(pagesPerSheet = 1)
        assertTrue(settings1.isValid())

        val settings2 = PrintSettings(pagesPerSheet = 16)
        assertTrue(settings2.isValid())

        val settings3 = PrintSettings(pagesPerSheet = 4)
        assertTrue(settings3.isValid())
    }

    @Test
    fun `invalid pages per sheet`() {
        val settings1 = PrintSettings(pagesPerSheet = 0)
        assertFalse(settings1.isValid())

        val settings2 = PrintSettings(pagesPerSheet = 17)
        assertFalse(settings2.isValid())
    }

    @Test
    fun `getPageRangeString returns null when no range`() {
        val settings = PrintSettings(pageRange = null)
        assertNull(settings.getPageRangeString())
    }

    @Test
    fun `getPageRangeString returns string when range exists`() {
        val pageRange = PageRange(startPage = 1, endPage = 5)
        val settings = PrintSettings(pageRange = pageRange)
        assertEquals("1-5", settings.getPageRangeString())
    }

    @Test
    fun `copy settings with changes`() {
        val original = PrintSettings()
        val modified = original.copy(copies = 5, paperSize = PaperSize.LETTER)

        assertEquals(5, modified.copies)
        assertEquals(PaperSize.LETTER, modified.paperSize)
        // Original should remain unchanged
        assertEquals(1, original.copies)
        assertEquals(PaperSize.A4, original.paperSize)
    }
}
