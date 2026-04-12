package com.cprint.app.domain.usecase.document

import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting recent documents
 */
class GetRecentDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /**
     * Get all recent documents
     */
    operator fun invoke(): Flow<List<RecentDocument>> {
        return documentRepository.getRecentDocuments()
    }

    /**
     * Get recent documents with limit
     */
    fun withLimit(limit: Int): Flow<List<RecentDocument>> {
        return documentRepository.getRecentDocuments(limit)
    }
}
