package com.cprint.app.domain.usecase.document

import android.net.Uri
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Use case for opening a document
 */
class OpenDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(uri: Uri): Result<RecentDocument> {
        return documentRepository.openDocument(uri)
    }
}
