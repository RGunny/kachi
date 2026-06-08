package me.rgunny.kachi.notification.domain

import java.time.Instant

class Notification private constructor(

    val id: NotificationId,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String?,
    var status: NotificationStatus,
    var failureReason: String? = null,
    var requestedAt: Instant? = null,
    var updatedAt: Instant? = null,
    var lastTransitionAt: Instant? = null,
    var dispatchAttempts: Int = 0,
    histories: List<NotificationHistory> = emptyList(),
) {
    private val _histories = histories.toMutableList()
    val histories: List<NotificationHistory>
        get() = _histories.toList()

    companion object {

        /**
         * 클라이언트의 발송요청 수신
         * NotificationStatus: [null --> REQUESTED]
         */
        fun request(
            requestId: String,
            requester: String,
            channel: NotificationChannel,
            recipient: String,
            message: String?,
            now: Instant
        ): Notification {
            return Notification(
                id = NotificationId.newId(),
                requestId = requestId,
                requester = requester,
                channel = channel,
                recipient = recipient,
                message = message,
                status = NotificationStatus.REQUESTED,
                requestedAt = now,
                updatedAt = now
            )
        }
    }

    /**
     * Kafka 발행 성공 처리.
     * NotificationStatus: [REQUESTED --> PUBLISHED]
     */
    fun markPublished(now: Instant) {
        // 이미 PUBLISHED 면 변화 없이 반환 (멱등).
        if (this.status == NotificationStatus.PUBLISHED) {
            return
        }

        // REQUESTED 가 아니면 잘못된 상태에서의 전이요청으로 예외 처리
        if (this.status != NotificationStatus.REQUESTED) {
            throw IllegalStateException("markPublished requires status REQUESTED, current= ${this.status}")
        }

        transition(NotificationStatus.REQUESTED, NotificationStatus.PUBLISHED, now)
    }

    /**
     * Kafka 발행 실패 처리.
     * NotificationStatus: [REQUESTED --> PUBLISH_FAILED]
     */
    fun markPublishFailed(now: Instant, reason: String) {
        // 이미 PUBLISHED, PUBLISH_FAILED 면 변화 없이 반환 (멱등).
        if (this.status == NotificationStatus.PUBLISHED
            || this.status == NotificationStatus.PUBLISH_FAILED) {
            return
        }

        transition(NotificationStatus.REQUESTED, NotificationStatus.PUBLISH_FAILED, now, reason)
    }

    /**
     * 카프카 요청 수신 성공 처리.
     * NotificationStatus: [PUBLISHED --> PROCESSING]
     */
    fun markProcessing(now: Instant) {
        if (this.status == NotificationStatus.PROCESSING) {
            return
        }
        transition(NotificationStatus.PUBLISHED, NotificationStatus.PROCESSING, now)
    }

    /**
     * 외부 채널 발송 성공 처리.
     * NotificationStatus: [PROCESSING --> SENT]
     */
    fun markSent(now: Instant) {
        transition(NotificationStatus.PROCESSING, NotificationStatus.SENT, now)
    }

    /**
     * 외부 채널 발송 실패 처리.
     * NotificationStatus: [PROCESSING --> FAILED]
     */
    fun markFailed(now: Instant, reason: String) {
        transition(NotificationStatus.PROCESSING, NotificationStatus.FAILED, now, reason)
    }

    /**
     * 도메인 상태 전이 — 3차 도메인 상태 가드.
     * expected 와 현재 상태가 다르면 IllegalStateException.
     * 1,2차 가드(Redis SETNX, DB UNIQUE)를 통과해도 같은 row 에 잘못된 상태 변경을 차단
     * (예: 이미 PUBLISHED 인 row 에 markPublished 재호출, 이미 SENT 인 row 에 markSent 재호출).
     */
    private fun transition(
        expected: NotificationStatus, next: NotificationStatus,
        now: Instant,
        reason: String? = null
    ) {
        if (this.status != expected) {
            throw IllegalStateException("transition to " + next + " requires " + expected + ", current=${this.status}")
        }
        val fromStatus = this.status
        this.status = next
        this.updatedAt = now
        this.lastTransitionAt = now
        this.failureReason = reason
        this._histories.add(NotificationHistory.record(
            notificationId = this.id,
            fromStatus = fromStatus,
            toStatus = next,
            createdAt = now,
            reason = reason
        ))
    }

}
