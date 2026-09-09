package me.rgunny.kachi.collector.adapter.outbound.persistence.outbox

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxClaim
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * outbox 행의 MongoDB Document.
 *
 * eventKey unique index가 "이벤트 하나당 행 하나" 계약을 보장한다.
 * 나머지 두 index는 relay가 매 tick 도는 조회(발행 대상, 회수 대상)를 받친다.
 *
 * 소유자와 점유 시각은 문서에서 두 필드로 흩어지고 도메인에서 [CollectorOutboxClaim] 하나로 합쳐진다.
 * 한쪽만 채워진 문서는 도메인 복원에서 걸러진다.
 */
@Document(collection = "collector_outbox")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_collector_outbox_event_key",
        def = "{'eventKey': 1}",
        unique = true
    ),
    CompoundIndex(
        name = "ix_collector_outbox_status_next_retry_at",
        def = "{'status': 1, 'nextRetryAt': 1}"
    ),
    CompoundIndex(
        name = "ix_collector_outbox_status_claimed_at",
        def = "{'status': 1, 'claimedAt': 1}"
    )
)
data class CollectorOutboxMongoDocument(
    @Id
    val id: UUID,
    val eventType: String,
    val eventKey: String,
    val partitionKey: String,
    val payload: String,
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

    fun toDomain(): CollectorOutbox {
        return CollectorOutbox.restore(
            id = CollectorOutboxId.of(id),
            eventType = CollectorOutboxEventType.valueOf(eventType),
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            status = CollectorOutboxStatus.valueOf(status),
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            publishedAt = publishedAt,
            claim = toClaim(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun toClaim(): CollectorOutboxClaim? {
        if (claimedBy == null || claimedAt == null) {
            return null
        }

        return CollectorOutboxClaim(claimedBy = claimedBy, claimedAt = claimedAt)
    }

    companion object {

        fun fromDomain(outbox: CollectorOutbox): CollectorOutboxMongoDocument {
            return CollectorOutboxMongoDocument(
                id = outbox.id.value,
                eventType = outbox.eventType.name,
                eventKey = outbox.eventKey,
                partitionKey = outbox.partitionKey,
                payload = outbox.payload,
                status = outbox.status.name,
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
