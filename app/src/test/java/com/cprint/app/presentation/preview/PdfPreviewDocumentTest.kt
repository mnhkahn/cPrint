package com.cprint.app.presentation.preview

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class PdfPreviewDocumentTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val uri = Uri.parse("content://preview-test/shared.pdf")
    private val descriptors = mutableListOf<ParcelFileDescriptor>()
    private fun cachedFiles() = context.cacheDir.listFiles()!!.filter { it.name.startsWith("pdf-preview-") }

    @After fun closeMockedDescriptors() {
        // A mocked PdfRenderer does not assume descriptor ownership like Android does.
        descriptors.forEach { it.close() }
    }

    @Test fun `copies stream sources to a seekable file and deletes cache on close`() {
        val bytes = "%PDF-fixture-content".toByteArray()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        mockConstruction(PdfRenderer::class.java) { renderer, invocation ->
            descriptors.add(invocation.arguments()[0] as ParcelFileDescriptor)
            whenever(renderer.pageCount).thenReturn(3)
        }.use { renderers ->
            val document = PdfPreviewDocument.open(context, uri)
            assertEquals(3, document.pageCount)
            assertArrayEquals(bytes, cachedFiles().single().readBytes())
            assertEquals(bytes.size.toLong(), descriptors.single().statSize)

            document.close()
            document.close()

            verify(renderers.constructed().single()).close()
            assertTrue(cachedFiles().isEmpty())
        }
    }

    @Test fun `empty file fails explicitly and removes temporary file`() {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf()))
        assertThrows(IllegalStateException::class.java) { PdfPreviewDocument.open(context, uri) }
        assertTrue(cachedFiles().isEmpty())
    }

    @Test fun `unreadable source fails explicitly and removes temporary file`() {
        val missing = Uri.fromFile(File(context.cacheDir, "missing.pdf"))
        assertThrows(FileNotFoundException::class.java) { PdfPreviewDocument.open(context, missing) }
        assertTrue(cachedFiles().isEmpty())
    }

    @Test fun `zero page PDF fails and closes renderer instead of showing an empty pager`() {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1)))
        mockConstruction(PdfRenderer::class.java) { renderer, invocation ->
            descriptors.add(invocation.arguments()[0] as ParcelFileDescriptor)
            whenever(renderer.pageCount).thenReturn(0)
        }.use { renderers ->
            assertThrows(IllegalStateException::class.java) { PdfPreviewDocument.open(context, uri) }
            verify(renderers.constructed().single()).close()
            assertTrue(cachedFiles().isEmpty())
        }
    }
}
