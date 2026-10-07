package com.cprint.app.data.repository

import android.app.Application
import android.content.Context
import android.content.ContentResolver
import android.content.UriPermission
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cprint.app.data.local.RecentDocumentDao
import com.cprint.app.domain.repository.DocumentInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DocumentPersistenceTest {
    @Test fun `persistently authorized document uses original URI and creates no copy`() = runTest {
        val uri = Uri.parse("content://documents/document/original")
        val permission = mock<UriPermission>()
        whenever(permission.uri).thenReturn(uri)
        whenever(permission.isReadPermission).thenReturn(true)
        val resolver = mock<ContentResolver>()
        whenever(resolver.persistedUriPermissions).thenReturn(listOf(permission))
        val context = mock<Context>()
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.packageName).thenReturn("com.cprint.app")
        val dao = mock<RecentDocumentDao>()
        val repo = spy(DocumentRepositoryImpl(context, dao))
        doReturn(Result.success(DocumentInfo("photo.png", uri.toString(), "image/png", 4)))
            .whenever(repo).getDocumentInfo(uri)
        val source = File.createTempFile("photo-", ".png")
        try {
            source.writeText("data")
            whenever(resolver.openFileDescriptor(uri, "r")).thenAnswer {
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val document = repo.openDocument(uri).getOrThrow()
            assertEquals(uri.toString(), document.uri)
            verify(context, never()).filesDir
            verify(resolver, never()).openInputStream(any())
            verify(dao).insertDocument(check { assertEquals(uri.toString(), it.uri) })
        } finally { source.delete() }
    }
}
