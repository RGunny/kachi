package me.rgunny.kachi.collector.adapter.inbound.web

import me.rgunny.kachi.collector.application.port.inbound.outbox.model.CollectorOutboxSummary
import java.time.Instant
import java.util.UUID

data class CollectorOutboxResponse(
    val id: UUID,
    val eventType: String,
    val eventKey: String,
    val partitionKey: String,
    val status: String,
    val retryCount: Int,
    val nextRetryAt: Instant,
    val lastError: String?,
    val publishedAt: Instant?,
    val claimedBy: String?,
    val claimedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {

        fun from(summary: CollectorOutboxSummary): CollectorOutboxResponse {
            return CollectorOutboxResponse(
                id = summary.id.value,
                eventType = summary.eventType.name,
                eventKey = summary.eventKey,
                partitionKey = summary.partitionKey,
                status = summary.status.name,
                retryCount = summary.retryCount,
                nextRetryAt = summary.nextRetryAt,
                lastError = summary.lastError,
                publishedAt = summary.publishedAt,
                claimedBy = summary.claimedBy,
                claimedAt = summary.claimedAt,
                createdAt = summary.createdAt,
                updatedAt = summary.updatedAt
            )
        }
    }
}
