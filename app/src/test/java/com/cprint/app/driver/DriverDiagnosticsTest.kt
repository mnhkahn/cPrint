package com.cprint.app.driver

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class DriverDiagnosticsTest {
    @Test fun `diagnostics beyond capture limit are still drained to EOF`() {
        val input = ByteArrayInputStream(ByteArray(128 * 1024) { 'x'.code.toByte() })
        val log = StringBuilder()
        DriverDiagnostics.drain(input, log, 32 * 1024)
        assertEquals(32 * 1024, log.length)
        assertEquals(0, input.available())
        assertEquals("x".repeat(32 * 1024), log.toString())
    }
}
