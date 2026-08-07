package me.rgunny.kachi.collector.application.port.inbound.collection.model

import me.rgunny.kachi.collector.domain.CollectionRun
import me.rgunny.kachi.collector.domain.CollectionRunId
import me.rgunny.kachi.collector.domain.CollectionRunStatus
import me.rgunny.kachi.collector.domain.CollectionTargetType
import java.time.Instant

data class CollectionRunResult(
    val id: CollectionRunId,
    val targetType: CollectionTargetType,
    val status: CollectionRunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val collectedCount: Int,
    val duplicateCount: Int,
    val failureCount: Int,
    val failureReason: String?
) {

    companion object {

        fun from(collectionRun: CollectionRun): CollectionRunResult {
            return CollectionRunResult(
                id = collectionRun.id,
                targetType = collectionRun.targetType,
                status = collectionRun.status,
                startedAt = collectionRun.startedAt,
                finishedAt = collectionRun.finishedAt,
                requestedKeywords = collectionRun.requestedKeywords,
                collectedCount = collectionRun.collectedCount,
                duplicateCount = collectionRun.duplicateCount,
                failureCount = collectionRun.failureCount,
                failureReason = collectionRun.failureReason
            )
        }
    }
}
