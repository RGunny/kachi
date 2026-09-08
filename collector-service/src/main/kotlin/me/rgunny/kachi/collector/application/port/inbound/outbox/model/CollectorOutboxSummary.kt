package me.rgunny.kachi.collector.application.port.inbound.outbox.model

import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import java.time.Instant

/**
 * 조회 응답이 쓰는 outbox 행 스냅샷.
 *
 * payload는 담지 않는다. 기사 내용 전체가 들어 있어 목록 조회에 맞지 않는다.
 * 소유권은 발행 중에 멈춘 행의 소유자를 확인하는 것이 조회 목적 중 하나라 풀어 담는다.
 */
data class CollectorOutboxSummary(
    val id: CollectorOutboxId,
    val eventType: CollectorOutboxEventType,
    val eventKey: String,
    val partitionKey: String,
    val status: CollectorOutboxStatus,
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

        fun from(outbox: CollectorOutbox): CollectorOutboxSummary {
            return CollectorOutboxSummary(
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
