package com.cprint.app.presentation.preview

import android.app.Application
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PrintPreviewIntentTest {
    @Test fun `recent external URI is passed internally without attempting to regrant expired permission`() {
        val uri = "content://media/external/downloads/1000018792"
        val intent = PrintPreviewActivity.createIntent(RuntimeEnvironment.getApplication(), uri)
        assertEquals(uri, intent.getStringExtra(PrintPreviewActivity.EXTRA_DOCUMENT_URI))
        assertEquals(PrintPreviewActivity::class.java.name, intent.component!!.className)
        assertNull(intent.data)
        assertNull(intent.clipData)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
