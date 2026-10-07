package com.cprint.app.presentation.preview

import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Test

class PreviewErrorMessageTest {
    @Test fun `permission and missing files are not described as damaged PDFs`() {
        assertTrue(previewErrorMessage(SecurityException()).contains("权限"))
        assertTrue(previewErrorMessage(FileNotFoundException()).contains("找不到文件"))
        assertFalse(previewErrorMessage(SecurityException()).contains("损坏"))
    }

    @Test fun `PDF parsing error keeps actionable reason`() {
        val reason = "PDF 已加密或限制访问，请先解密后再打开"
        assertEquals(reason, previewErrorMessage(IllegalArgumentException(reason)))
    }
}
