package me.rgunny.kachi.collector.adapter.`in`.web

import me.rgunny.kachi.collector.application.port.`in`.CollectionRunResult
import java.time.Instant

data class CollectionRunResponse(
    val id: String,
    val targetType: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val collectedCount: Int,
    val duplicateCount: Int,
    val failureCount: Int,
    val failureReason: String?
) {
    companion object {

        fun from(result: CollectionRunResult): CollectionRunResponse {
            return CollectionRunResponse(
                id = result.id.value.toString(),
                targetType = result.targetType.name,
                status = result.status.name,
                startedAt = result.startedAt,
                finishedAt = result.finishedAt,
                requestedKeywords = result.requestedKeywords,
                collectedCount = result.collectedCount,
                duplicateCount = result.duplicateCount,
                failureCount = result.failureCount,
                failureReason = result.failureReason
            )
        }
    }
}
