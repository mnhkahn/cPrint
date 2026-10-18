package com.cprint.app.domain.usecase.document

import com.cprint.app.analytics.AppAnalytics
import com.cprint.app.analytics.DocumentSource
import android.net.Uri
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Use case for opening a document
 */
class OpenDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val analytics: AppAnalytics
) {
    suspend operator fun invoke(uri: Uri, source: DocumentSource = DocumentSource.UNKNOWN): Result<RecentDocument> {
        val started = System.nanoTime()
        val result = try {
            documentRepository.openDocument(uri)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
        val document = result.getOrNull()
        analytics.documentResult(document?.type, document?.pageCount, result.exceptionOrNull(),
            AppAnalytics.elapsedMs(started), source)
        return result
    }
}
