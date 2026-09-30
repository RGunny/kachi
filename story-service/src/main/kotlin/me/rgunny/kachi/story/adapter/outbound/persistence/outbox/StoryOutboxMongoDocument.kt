package me.rgunny.kachi.story.adapter.outbound.persistence.outbox

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * `story_outboxes` 컬렉션 문서.
 *
 * 소유자와 점유 시각은 두 필드로 저장하고 도메인에서 [StoryOutboxClaim] 하나로 합친다. 한쪽만 있는 문서는 소유권 없음으로 읽는다.
 */
@Document(collection = "story_outboxes")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_story_outboxes_event_key",
        def = "{'eventKey': 1}",
        unique = true
    ),
    CompoundIndex(
        name = "ix_story_outboxes_status_next_retry_at",
        def = "{'status': 1, 'nextRetryAt': 1}"
    ),
    CompoundIndex(
        name = "ix_story_outboxes_status_claimed_at",
        def = "{'status': 1, 'claimedAt': 1}"
    )
)
data class StoryOutboxMongoDocument(
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

    fun toDomain(): StoryOutbox {
        return StoryOutbox.restore(
            id = StoryOutboxId.of(id),
            eventType = StoryOutboxEventType.valueOf(eventType),
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            status = StoryOutboxStatus.valueOf(status),
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            publishedAt = publishedAt,
            claim = toClaim(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun toClaim(): StoryOutboxClaim? {
        if (claimedBy == null || claimedAt == null) {
            return null
        }

        return StoryOutboxClaim(claimedBy = claimedBy, claimedAt = claimedAt)
    }

    companion object {

        fun fromDomain(outbox: StoryOutbox): StoryOutboxMongoDocument {
            return StoryOutboxMongoDocument(
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
