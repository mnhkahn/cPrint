package com.cprint.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cprint.app.data.local.RecentDocumentDao
import com.cprint.app.data.model.entity.RecentDocumentEntity
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.DocumentInfo
import com.cprint.app.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DocumentRepository
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentDocumentDao: RecentDocumentDao
) : DocumentRepository {

    private val supportedMimeTypes = setOf(
        "application/pdf",
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/bmp",
        "image/webp"
    )

    override fun getRecentDocuments(): Flow<List<RecentDocument>> {
        return recentDocumentDao.getAllDocuments().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getRecentDocuments(limit: Int): Flow<List<RecentDocument>> {
        return recentDocumentDao.getRecentDocuments(limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getDocumentById(documentId: String): RecentDocument? {
        return recentDocumentDao.getDocumentById(documentId)?.toDomainModel()
    }

    override suspend fun getDocumentByUri(uri: String): RecentDocument? {
        return recentDocumentDao.getDocumentByUri(uri)?.toDomainModel()
    }

    override suspend fun addRecentDocument(document: RecentDocument): Result<Unit> {
        return try {
            recentDocumentDao.insertDocument(RecentDocumentEntity.fromDomainModel(document))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openDocument(uri: Uri): Result<RecentDocument> {
        return try {
            val existingDoc = recentDocumentDao.getDocumentByUri(uri.toString())

            if (existingDoc != null) {
                // Update access time
                recentDocumentDao.updateAccessTime(existingDoc.id, Date().time)
                Result.success(existingDoc.toDomainModel())
            } else {
                // Create new document entry
                val docInfo = getDocumentInfo(uri).getOrNull()
                    ?: return Result.failure(IllegalArgumentException("Cannot read document"))

                val pageCount = if (docInfo.type == "application/pdf") {
                    getPdfPageCount(uri).getOrDefault(1)
                } else {
                    1
                }

                val document = RecentDocumentEntity(
                    id = UUID.randomUUID().toString(),
                    name = docInfo.name,
                    uri = docInfo.uri,
                    type = docInfo.type,
                    size = docInfo.size,
                    pageCount = pageCount,
                    thumbnailUri = null,
                    lastOpenedAt = Date(),
                    openCount = 1
                )

                recentDocumentDao.insertDocument(document)
                Result.success(document.toDomainModel())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDocumentInfo(uri: Uri): Result<DocumentInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)

                        val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                        val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"

                        Result.success(
                            DocumentInfo(
                                name = name,
                                uri = uri.toString(),
                                type = type,
                                size = size
                            )
                        )
                    } else {
                        Result.failure(IllegalArgumentException("Cannot read document info"))
                    }
                } ?: Result.failure(IllegalArgumentException("Cannot query document"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getPdfPageCount(uri: Uri): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext Result.failure(IllegalArgumentException("Cannot open PDF"))

                pfd.use {
                    val renderer = PdfRenderer(it)
                    val pageCount = renderer.pageCount
                    renderer.close()
                    Result.success(pageCount)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun generateThumbnail(uri: Uri, pageNumber: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext Result.failure(IllegalArgumentException("Cannot open document"))

                pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    val page = renderer.openPage(pageNumber.coerceIn(0, renderer.pageCount - 1))

                    val bitmap = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()

                    // Save thumbnail
                    val thumbnailFile = File(context.cacheDir, "thumbnails/${UUID.randomUUID()}.png")
                    thumbnailFile.parentFile?.mkdirs()

                    FileOutputStream(thumbnailFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }

                    Result.success(thumbnailFile.absolutePath)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteDocument(documentId: String): Result<Unit> {
        return try {
            recentDocumentDao.deleteDocumentById(documentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearRecentDocuments(): Result<Unit> {
        return try {
            recentDocumentDao.clearAllDocuments()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isSupportedFileType(mimeType: String): Boolean {
        return supportedMimeTypes.contains(mimeType)
    }
}
