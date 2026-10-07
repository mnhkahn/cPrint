package com.cprint.app.data.repository

import android.app.Application
import android.net.Uri
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import androidx.core.content.FileProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ImportedDocumentStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun resetProviderPaths() {
        // Robolectric creates a new dataDir per test, while AndroidX caches roots statically.
        val field = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (field.get(null) as MutableMap<*, *>).clear()
    }

    @Test fun `imported file remains readable after external source disappears`() {
        val source = File.createTempFile("shared-", ".pdf", context.cacheDir)
        val bytes = "%PDF-persisted-content".toByteArray()
        source.writeBytes(bytes)
        val store = ImportedDocumentStore(context)
        val saved = store.import(Uri.fromFile(source))
        assertTrue(store.isImported(saved))
        source.delete()
        val reopened = ImportedDocumentStore(context)
        assertTrue(reopened.isImported(saved))
        assertArrayEquals(bytes, context.contentResolver.openInputStream(saved)!!.use { it.readBytes() })
    }

    @Test fun `failed reimport preserves saved document and removes partial file`() {
        val source = File.createTempFile("shared-", ".pdf", context.cacheDir)
        source.writeText("original")
        val uri = Uri.fromFile(source)
        val store = ImportedDocumentStore(context)
        val saved = store.import(uri)
        source.writeBytes(byteArrayOf())
        assertThrows(IllegalStateException::class.java) { store.import(uri) }
        assertEquals("original", context.contentResolver.openInputStream(saved)!!.bufferedReader().use { it.readText() })
        assertTrue(File(context.filesDir, "imported-documents").listFiles()!!.none { it.name.endsWith(".tmp") })
        source.delete()
    }
    @Test fun `quota rejects oversized import without deleting existing files`() {
        val source = File.createTempFile("shared-", ".pdf", context.cacheDir)
        source.writeText("1234")
        val store = ImportedDocumentStore(context, limitBytes = 5)
        val saved = store.import(Uri.fromFile(source))
        val other = File.createTempFile("other-", ".pdf", context.cacheDir)
        other.writeText("12")
        assertThrows(IllegalStateException::class.java) { store.import(Uri.fromFile(other)) }
        assertEquals(4L, store.usedBytes())
        assertEquals("1234", context.contentResolver.openInputStream(saved)!!.bufferedReader().use { it.readText() })
        source.delete(); other.delete()
    }

    @Test fun `cleanup skips files with preview or print leases until both finish`() {
        val source = File.createTempFile("shared-", ".pdf", context.cacheDir)
        source.writeText("1234")
        val store = ImportedDocumentStore(context)
        val saved = store.import(Uri.fromFile(source))
        val preview = ImportedDocumentStore.retain(saved.toString())
        val print = ImportedDocumentStore.retain(saved.toString())
        try {
            assertEquals(0, store.clearUnused())
            preview.close()
            assertEquals(0, store.clearUnused())
            print.close()
            assertEquals(1, store.clearUnused())
            assertEquals(0L, store.usedBytes())
            assertTrue(source.exists())
        } finally { preview.close(); print.close(); source.delete() }
    }

}
