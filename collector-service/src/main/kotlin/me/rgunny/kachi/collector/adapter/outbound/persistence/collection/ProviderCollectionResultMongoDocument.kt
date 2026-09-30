package me.rgunny.kachi.collector.adapter.outbound.persistence.collection

import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.ProviderCollectionResult
import me.rgunny.kachi.collector.domain.ProviderCollectionStatus
import me.rgunny.kachi.collector.domain.ProviderFailureReason

data class ProviderCollectionResultMongoDocument(
    val source: NewsSource,
    val status: ProviderCollectionStatus,
    val fetchedCount: Int,
    val savedCount: Int,
    val duplicateCount: Int,
    val failureReason: ProviderFailureReason?,
    val failureMessage: String?
) {
    fun toDomain(): ProviderCollectionResult {
        return when (status) {
            ProviderCollectionStatus.SUCCEEDED -> ProviderCollectionResult.success(
                source = source,
                fetchedCount = fetchedCount,
                savedCount = savedCount,
                duplicateCount = duplicateCount
            )

            ProviderCollectionStatus.FAILED -> ProviderCollectionResult.failure(
                source = source,
                failureReason = normalizedFailureReason(),
                failureMessage = normalizedFailureMessage()
            )
        }
    }

    private fun normalizedFailureReason(): ProviderFailureReason {
        return failureReason ?: ProviderFailureReason.UNKNOWN
    }

    private fun normalizedFailureMessage(): String {
        return failureMessage
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalizedFailureReason().name
    }

    companion object {
        fun fromDomain(result: ProviderCollectionResult): ProviderCollectionResultMongoDocument {
            return ProviderCollectionResultMongoDocument(
                source = result.source,
                status = result.status,
                fetchedCount = result.fetchedCount,
                savedCount = result.savedCount,
                duplicateCount = result.duplicateCount,
                failureReason = result.failureReason,
                failureMessage = result.failureMessage
            )
        }
    }
}
