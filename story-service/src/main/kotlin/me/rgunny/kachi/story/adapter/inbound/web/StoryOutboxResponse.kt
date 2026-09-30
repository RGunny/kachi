package me.rgunny.kachi.story.adapter.inbound.web

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.application.port.inbound.outbox.model.StoryOutboxSummary

/**
 * outbox 행 조회 응답.
 */
data class StoryOutboxResponse(
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

        fun from(summary: StoryOutboxSummary): StoryOutboxResponse {
            return StoryOutboxResponse(
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
