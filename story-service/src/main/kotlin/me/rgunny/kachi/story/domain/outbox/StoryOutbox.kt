package me.rgunny.kachi.story.domain.outbox

import java.time.Instant

/**
 * 발행해야 할 story 이벤트 1건과 그 발행 상태.
 *
 * ```
 * PENDING ──claim──▶ PUBLISHING ──publish ok──▶ PUBLISHED (terminal)
 *                        │
 *                        ├── retryable 실패, 한도 미만 ──▶ PENDING
 *                        ├── retryable 실패, 한도 도달 ──▶ DEAD
 *                        └── non-retryable 실패 ───────▶ DEAD
 * DEAD ──recover(운영자)──▶ PENDING
 * ```
 *
 * 불변이며 전이 메서드는 새 인스턴스를 반환한다. 상태에 맞지 않는 전이는 예외다. 늦은 결과는 저장소의 CAS가 거른다.
 */
class StoryOutbox private constructor(
    val id: StoryOutboxId,
    val eventType: StoryOutboxEventType,
    val eventKey: String,
    val partitionKey: String,
    val payload: String,
    val status: StoryOutboxStatus,
    val retryCount: Int,
    val nextRetryAt: Instant,
    val lastError: String?,
    val publishedAt: Instant?,
    val claim: StoryOutboxClaim?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {

        fun create(
            eventType: StoryOutboxEventType,
            eventKey: String,
            partitionKey: String,
            payload: String,
            now: Instant
        ): StoryOutbox {
            require(eventKey.isNotBlank()) { "outbox eventKey는 비어 있을 수 없습니다" }
            require(partitionKey.isNotBlank()) { "outbox partitionKey는 비어 있을 수 없습니다" }
            require(payload.isNotBlank()) { "outbox payload는 비어 있을 수 없습니다" }

            return StoryOutbox(
                id = StoryOutboxId.newId(),
                eventType = eventType,
                eventKey = eventKey,
                partitionKey = partitionKey,
                payload = payload,
                status = StoryOutboxStatus.PENDING,
                retryCount = 0,
                // 생성 즉시 발행 대상이다.
                nextRetryAt = now,
                lastError = null,
                publishedAt = null,
                claim = null,
                createdAt = now,
                updatedAt = now
            )
        }

        fun restore(
            id: StoryOutboxId,
            eventType: StoryOutboxEventType,
            eventKey: String,
            partitionKey: String,
            payload: String,
            status: StoryOutboxStatus,
            retryCount: Int,
            nextRetryAt: Instant,
            lastError: String?,
            publishedAt: Instant?,
            claim: StoryOutboxClaim?,
            createdAt: Instant,
            updatedAt: Instant
        ): StoryOutbox {
            require(eventKey.isNotBlank()) { "outbox eventKey는 비어 있을 수 없습니다" }
            require(partitionKey.isNotBlank()) { "outbox partitionKey는 비어 있을 수 없습니다" }
            require(payload.isNotBlank()) { "outbox payload는 비어 있을 수 없습니다" }
            require(retryCount >= 0) { "outbox retryCount는 0 이상이어야 합니다" }
            require(lastError == null || lastError.isNotBlank()) { "outbox lastError는 비어 있을 수 없습니다" }
            // claim은 PUBLISHING 상태와 함께 움직인다.
            require((status == StoryOutboxStatus.PUBLISHING) == (claim != null)) {
                "outbox claim은 PUBLISHING 상태에서만 존재해야 합니다"
            }
            require(status != StoryOutboxStatus.PUBLISHED || publishedAt != null) {
                "PUBLISHED outbox는 publishedAt을 가져야 합니다"
            }

            return StoryOutbox(
                id = id,
                eventType = eventType,
                eventKey = eventKey,
                partitionKey = partitionKey,
                payload = payload,
                status = status,
                retryCount = retryCount,
                nextRetryAt = nextRetryAt,
                lastError = lastError,
                publishedAt = publishedAt,
                claim = claim,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    /** 발행을 시작하며 소유권을 잡는다. */
    fun markPublishing(now: Instant, claimedBy: String): StoryOutbox {
        check(status == StoryOutboxStatus.PENDING) { "PENDING outbox만 발행을 시작할 수 있습니다: $status" }

        return transition(
            status = StoryOutboxStatus.PUBLISHING,
            claim = StoryOutboxClaim(claimedBy = claimedBy, claimedAt = now),
            updatedAt = now
        )
    }

    fun markPublished(now: Instant): StoryOutbox {
        check(status == StoryOutboxStatus.PUBLISHING) { "PUBLISHING outbox만 발행 완료할 수 있습니다: $status" }

        return transition(
            status = StoryOutboxStatus.PUBLISHED,
            lastError = null,
            publishedAt = now,
            claim = null,
            updatedAt = now
        )
    }

    /** 재시도 가능한 실패를 기록한다. 한도에 도달하면 DEAD다. */
    fun recordFailure(
        reason: String,
        retryPolicy: StoryOutboxRetryPolicy,
        now: Instant
    ): StoryOutbox {
        check(status == StoryOutboxStatus.PUBLISHING) { "PUBLISHING outbox만 실패를 기록할 수 있습니다: $status" }
        require(reason.isNotBlank()) { "outbox 실패 사유는 비어 있을 수 없습니다" }

        val attempts = retryCount + 1

        if (retryPolicy.exhausted(attempts)) {
            return transition(
                status = StoryOutboxStatus.DEAD,
                retryCount = attempts,
                lastError = reason,
                claim = null,
                updatedAt = now
            )
        }

        return transition(
            status = StoryOutboxStatus.PENDING,
            retryCount = attempts,
            nextRetryAt = now.plus(retryPolicy.backoff(attempts)),
            lastError = reason,
            claim = null,
            updatedAt = now
        )
    }

    /**
     * 재시도해도 결과가 달라지지 않는 실패를 한도와 무관하게 DEAD로 보낸다.
     */
    fun markDead(reason: String, now: Instant): StoryOutbox {
        check(status == StoryOutboxStatus.PUBLISHING) { "PUBLISHING outbox만 DEAD로 보낼 수 있습니다: $status" }
        require(reason.isNotBlank()) { "outbox 실패 사유는 비어 있을 수 없습니다" }

        return transition(
            status = StoryOutboxStatus.DEAD,
            lastError = reason,
            claim = null,
            updatedAt = now
        )
    }

    /** DEAD를 다시 발행 대상으로 되돌린다. */
    fun recoverToPending(now: Instant): StoryOutbox {
        check(status == StoryOutboxStatus.DEAD) { "DEAD outbox만 복구할 수 있습니다: $status" }

        return transition(
            status = StoryOutboxStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            lastError = null,
            publishedAt = null,
            claim = null,
            updatedAt = now
        )
    }

    /**
     * 전이 결과 인스턴스를 만든다.
     */
    private fun transition(
        status: StoryOutboxStatus = this.status,
        retryCount: Int = this.retryCount,
        nextRetryAt: Instant = this.nextRetryAt,
        lastError: String? = this.lastError,
        publishedAt: Instant? = this.publishedAt,
        claim: StoryOutboxClaim? = this.claim,
        updatedAt: Instant
    ): StoryOutbox {
        return StoryOutbox(
            id = id,
            eventType = eventType,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            publishedAt = publishedAt,
            claim = claim,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
