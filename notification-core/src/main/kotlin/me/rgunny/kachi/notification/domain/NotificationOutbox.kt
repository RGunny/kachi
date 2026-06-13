package me.rgunny.kachi.notification.domain

import me.rgunny.kachi.notification.retry.RetryPolicy
import java.time.Instant


/**
 * 알림 outbox Aggregate Root.
 *
 * DB 트랜잭션과 메시지 broker 발행을 같은 보장 단위로 묶는 패턴.
 * 같은 tx 에서 notification + notification_outbox 동시 commit 후,
 * 즉시 path (AFTER_COMMIT listener) 또는 폴링 시 broker 로 발행.
 *
 * 처리 권한 획득은 PENDING -> PUBLISHING CAS claim 단계로 분리한다.
 * claim 에 성공한 인스턴스만 publish 를 수행하며, 결과는 callback 에서 별도 트랜잭션으로 반영.
 *
 * PENDING ─▶ PUBLISHING ─┬─▶ PUBLISHED
 *                        ├─▶ PENDING    (실패 시 retry 가능)
 *                        └─▶ DEAD       (재시도 한도 초과 또는 즉시 DEAD)
 * DEAD ─▶ PENDING (운영자 수동 복구)
 */
class NotificationOutbox private constructor(
    val id: NotificationOutboxId,
    val notificationId: NotificationId,
    val topic: String,
    val partitionKey: String,
    val eventPayload: String,
    val createdAt: Instant,
    outboxStatus: NotificationOutboxStatus = NotificationOutboxStatus.PENDING,
    retryCount: Int = 0,
    nextRetryAt: Instant = createdAt,
    lastError: String? = null,
    publishedAt: Instant? = null,
    claimedAt: Instant? = null,
    claimedBy: String? = null,
) {
    var outboxStatus: NotificationOutboxStatus = outboxStatus
        private set

    var retryCount: Int = retryCount
        private set

    var nextRetryAt: Instant = nextRetryAt
        private set

    var lastError: String? = lastError
        private set

    var publishedAt: Instant? = publishedAt
        private set

    var claimedAt: Instant? = claimedAt
        private set

    var claimedBy: String? = claimedBy
        private set

    companion object {

        fun create(
            notificationId: NotificationId,
            topic: String,
            partitionKey: String,
            eventPayload: String,
            now: Instant,
        ): NotificationOutbox {
            requireNonBlankForCreate(topic, "topic")
            requireNonBlankForCreate(partitionKey, "partitionKey")
            requireNonBlankForCreate(eventPayload, "eventPayload")

            return NotificationOutbox(
                id = NotificationOutboxId.newId(),
                notificationId = notificationId,
                topic = topic,
                partitionKey = partitionKey,
                eventPayload = eventPayload,
                createdAt = now,
            )
        }

        private fun requireNonBlankForCreate(value: String, name: String) {
            require(value.isNotBlank()) { "$name must not be blank" }
        }
    }

    /**
     * 처리 권한 획득.
     * NotificationOutboxStatus: [PENDING --> PUBLISHING]
     */
    fun markPublishing(now: Instant, claimedBy: String) {
        requireNonBlank(claimedBy, "claimedBy")

        if (this.outboxStatus != NotificationOutboxStatus.PENDING) {
            throw IllegalStateException("markPublishing requires PENDING, current=${this.outboxStatus}")
        }

        this.outboxStatus = NotificationOutboxStatus.PUBLISHING
        this.claimedAt = now
        this.claimedBy = claimedBy
    }

    /**
     * 발행 성공 처리.
     * NotificationOutboxStatus: [PUBLISHING --> PUBLISHED]
     */
    fun markPublished(now: Instant) {
        if (this.outboxStatus == NotificationOutboxStatus.PUBLISHED) {
            return
        }

        if (this.outboxStatus != NotificationOutboxStatus.PUBLISHING) {
            throw IllegalStateException("markPublished requires PUBLISHING, current=${this.outboxStatus}")
        }

        this.outboxStatus = NotificationOutboxStatus.PUBLISHED
        this.publishedAt = now
        this.lastError = null
        clearClaim()
    }

    /**
     * 발행 실패 처리.
     * NotificationOutboxStatus: [PUBLISHING --> PENDING] 또는 [PUBLISHING --> DEAD]
     */
    fun recordFailure(reason: String, retryPolicy: RetryPolicy, now: Instant) {
        requireNonBlank(reason, "reason")

        if (this.outboxStatus != NotificationOutboxStatus.PUBLISHING) {
            throw IllegalStateException("recordFailure requires PUBLISHING, current=${this.outboxStatus}")
        }

        val nextRetryCount = this.retryCount + 1
        this.retryCount = nextRetryCount
        this.lastError = reason

        if (retryPolicy.exhausted(nextRetryCount)) {
            this.outboxStatus = NotificationOutboxStatus.DEAD
        } else {
            this.outboxStatus = NotificationOutboxStatus.PENDING
            this.nextRetryAt = now.plus(retryPolicy.backoff(nextRetryCount))
        }

        clearClaim()
    }

    /**
     * 즉시 DEAD 처리.
     * NotificationOutboxStatus: [PUBLISHING --> DEAD]
     */
    fun markDead(reason: String) {
        requireNonBlank(reason, "reason")

        if (this.outboxStatus != NotificationOutboxStatus.PUBLISHING) {
            throw IllegalStateException("markDead requires PUBLISHING, current=${this.outboxStatus}")
        }

        this.outboxStatus = NotificationOutboxStatus.DEAD
        this.lastError = reason
        clearClaim()
    }

    /**
     * 운영자 복구.
     * NotificationOutboxStatus: [DEAD --> PENDING]
     */
    fun recoverToPending(now: Instant) {
        if (this.outboxStatus != NotificationOutboxStatus.DEAD) {
            throw IllegalStateException("recoverToPending requires DEAD, current=${this.outboxStatus}")
        }

        this.outboxStatus = NotificationOutboxStatus.PENDING
        this.retryCount = 0
        this.nextRetryAt = now
        this.lastError = null
        this.publishedAt = null
        clearClaim()
    }

    private fun clearClaim() {
        this.claimedAt = null
        this.claimedBy = null
    }

    private fun requireNonBlank(value: String, name: String) {
        require(value.isNotBlank()) { "$name must not be blank" }
    }
}
