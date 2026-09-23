package com.cprint.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgyerUpdateManagerTest {
    @Test fun `recognizes newer semantic versions`() {
        assertTrue(PgyerUpdateManager.isNewerVersion("v1.2.0", "1.1.9"))
        assertTrue(PgyerUpdateManager.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(PgyerUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(PgyerUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
    }

    @Test fun `parses PGYER public-page release metadata`() {
        val release = PgyerUpdateManager.parseRelease(
            "<script>{\"buildVersion\":\"0.1.7\",\"buildUpdateDescription\":\"修复打印\\n优化连接\"}</script>"
        )!!
        assertEquals("0.1.7", release.version)
        assertEquals("修复打印\n优化连接", release.notes)
    }
}
