package me.rgunny.kachi.story.application.port.inbound.outbox.model

import java.time.Instant
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus

/**
 * 조회 응답이 쓰는 outbox 행 스냅샷.
 *
 * payload는 담지 않는다. 기사 내용 전체가 들어 있어 목록 조회에 맞지 않는다.
 * 소유권은 발행 중에 멈춘 행의 소유자를 확인하는 것이 조회 목적 중 하나라 풀어 담는다.
 */
data class StoryOutboxSummary(
    val id: StoryOutboxId,
    val eventType: StoryOutboxEventType,
    val eventKey: String,
    val partitionKey: String,
    val status: StoryOutboxStatus,
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

        fun from(outbox: StoryOutbox): StoryOutboxSummary {
            return StoryOutboxSummary(
                id = outbox.id,
                eventType = outbox.eventType,
                eventKey = outbox.eventKey,
                partitionKey = outbox.partitionKey,
                status = outbox.status,
                retryCount = outbox.retryCount,
                nextRetryAt = outbox.nextRetryAt,
                lastError = outbox.lastError,
                publishedAt = outbox.publishedAt,
                claimedBy = outbox.claim?.claimedBy,
                claimedAt = outbox.claim?.claimedAt,
                createdAt = outbox.createdAt,
                updatedAt = outbox.updatedAt
            )
        }
    }
}
