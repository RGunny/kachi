package me.rgunny.kachi.collector.adapter.outbound.persistence

import me.rgunny.kachi.collector.domain.CollectionRun
import me.rgunny.kachi.collector.domain.CollectionRunId
import me.rgunny.kachi.collector.domain.CollectionRunStatus
import me.rgunny.kachi.collector.domain.CollectionTargetType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "collection_runs")
data class CollectionRunMongoDocument(
    @Id
    val id: UUID,
    val targetType: CollectionTargetType,
    @Indexed
    val status: CollectionRunStatus,
    @Indexed
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val collectedCount: Int,
    val duplicateCount: Int,
    val failureCount: Int,
    val failureReason: String?,
    val providerResults: List<ProviderCollectionResultMongoDocument>
) {
    fun toDomain(): CollectionRun {
        return CollectionRun.restore(
            id = CollectionRunId.of(id),
            targetType = targetType,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            requestedKeywords = requestedKeywords,
            collectedCount = collectedCount,
            duplicateCount = duplicateCount,
            failureCount = failureCount,
            failureReason = failureReason,
            providerResults = providerResults.map { it.toDomain() }
        )
    }

    companion object {
        fun fromDomain(collectionRun: CollectionRun): CollectionRunMongoDocument {
            return CollectionRunMongoDocument(
                id = collectionRun.id.value,
                targetType = collectionRun.targetType,
                status = collectionRun.status,
                startedAt = collectionRun.startedAt,
                finishedAt = collectionRun.finishedAt,
                requestedKeywords = collectionRun.requestedKeywords,
                collectedCount = collectionRun.collectedCount,
                duplicateCount = collectionRun.duplicateCount,
                failureCount = collectionRun.failureCount,
                failureReason = collectionRun.failureReason,
                providerResults = collectionRun.providerResults.map {
                    ProviderCollectionResultMongoDocument.fromDomain(it)
                }
            )
        }
    }
}
