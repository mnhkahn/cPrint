package com.cprint.app.domain.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PageRange class
 */
class PageRangeTest {

    @Test
    fun `parse empty string returns null`() {
        assertNull(PageRange.parse(""))
        assertNull(PageRange.parse("   "))
    }

    @Test
    fun `parse single page`() {
        val range = PageRange.parse("5")
        assertNotNull(range)
        assertEquals(listOf(5), range?.specificPages)
    }

    @Test
    fun `parse multiple specific pages`() {
        val range = PageRange.parse("1,3,5")
        assertNotNull(range)
        assertEquals(listOf(1, 3, 5), range?.specificPages)
    }

    @Test
    fun `parse range`() {
        val range = PageRange.parse("1-5")
        assertNotNull(range)
        assertEquals(1, range?.startPage)
        assertEquals(5, range?.endPage)
    }

    @Test
    fun `parse mixed range and specific pages`() {
        val range = PageRange.parse("1-3,5,7-9")
        assertNotNull(range)
        assertEquals(1, range?.startPage)
        assertEquals(3, range?.endPage)
        assertEquals(listOf(5, 7, 9), range?.specificPages)
    }

    @Test
    fun `should print page with specific pages`() {
        val range = PageRange(specificPages = listOf(1, 3, 5))
        assertTrue(range.shouldPrintPage(1, 10))
        assertTrue(range.shouldPrintPage(3, 10))
        assertTrue(range.shouldPrintPage(5, 10))
        assertFalse(range.shouldPrintPage(2, 10))
        assertFalse(range.shouldPrintPage(4, 10))
    }

    @Test
    fun `should print page with range`() {
        val range = PageRange(startPage = 2, endPage = 5)
        assertFalse(range.shouldPrintPage(1, 10))
        assertTrue(range.shouldPrintPage(2, 10))
        assertTrue(range.shouldPrintPage(3, 10))
        assertTrue(range.shouldPrintPage(5, 10))
        assertFalse(range.shouldPrintPage(6, 10))
    }

    @Test
    fun `get pages to print with specific pages`() {
        val range = PageRange(specificPages = listOf(1, 3, 5, 10))
        assertEquals(4, range.getPagesToPrint(10))
    }

    @Test
    fun `get pages to print with range`() {
        val range = PageRange(startPage = 2, endPage = 5)
        assertEquals(4, range.getPagesToPrint(10))
    }

    @Test
    fun `toString with specific pages`() {
        val range = PageRange(specificPages = listOf(1, 3, 5))
        assertEquals("1,3,5", range.toString())
    }

    @Test
    fun `toString with range`() {
        val range = PageRange(startPage = 1, endPage = 5)
        assertEquals("1-5", range.toString())
    }

    @Test
    fun `toString with only start page`() {
        val range = PageRange(startPage = 3)
        assertEquals("3-", range.toString())
    }

    @Test
    fun `toString with only end page`() {
        val range = PageRange(endPage = 5)
        assertEquals("1-5", range.toString())
    }
}
