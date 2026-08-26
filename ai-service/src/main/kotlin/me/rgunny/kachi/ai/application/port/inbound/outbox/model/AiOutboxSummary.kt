package me.rgunny.kachi.ai.application.port.inbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import java.time.Instant

/**
 * 조회 응답이 쓰는 outbox 행 스냅샷.
 *
 * payload는 담지 않는다. 요약 본문 전문이 들어 있어 목록 조회에 맞지 않는다.
 * 소유권은 발행 중에 멈춘 행의 소유자를 확인하는 것이 조회 목적 중 하나라 펼쳐 담는다.
 */
data class AiOutboxSummary(
    val id: AiOutboxId,
    val eventType: AiOutboxEventType,
    val eventKey: String,
    val partitionKey: String,
    val status: AiOutboxStatus,
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

        fun from(outbox: AiOutbox): AiOutboxSummary {
            return AiOutboxSummary(
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
