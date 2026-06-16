package me.rgunny.kachi.notification.domain

import java.time.Instant

class Notification private constructor(

    val id: NotificationId,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String?,
    /**
     * 외부 요청을 알림으로 최초 접수한 시각.
     */
    val requestedAt: Instant,
    status: NotificationStatus,
    failureReason: String? = null,
    /**
     * 알림 row가 마지막으로 변경된 시각.
     */
    updatedAt: Instant = requestedAt,
    /**
     * 알림 상태가 마지막으로 전이된 시각.
     */
    lastTransitionAt: Instant = requestedAt,
    dispatchAttempts: Int = 0,
    claimedAt: Instant? = null,
    claimedBy: String? = null,
    histories: List<NotificationHistory> = emptyList(),
) {
    var status: NotificationStatus = status
        private set

    var failureReason: String? = failureReason
        private set

    var updatedAt: Instant = updatedAt
        private set

    var lastTransitionAt: Instant = lastTransitionAt
        private set

    var dispatchAttempts: Int = dispatchAttempts
        private set

    var claimedAt: Instant? = claimedAt
        private set

    var claimedBy: String? = claimedBy
        private set

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
            require(requestId.isNotBlank()) { "requestId must not be blank" }
            require(requester.isNotBlank()) { "requester must not be blank" }
            require(recipient.isNotBlank()) { "recipient must not be blank" }
            require(message == null || message.isNotBlank()) { "message must not be blank" }

            return Notification(
                id = NotificationId.newId(),
                requestId = requestId,
                requester = requester,
                channel = channel,
                recipient = recipient,
                message = message,
                requestedAt = now,
                status = NotificationStatus.REQUESTED,
            )
        }

        /**
         * 저장소에 보관된 Notification aggregate snapshot을 복원한다.
         */
        fun restore(
            id: NotificationId,
            requestId: String,
            requester: String,
            channel: NotificationChannel,
            recipient: String,
            message: String?,
            requestedAt: Instant,
            status: NotificationStatus,
            failureReason: String?,
            updatedAt: Instant,
            lastTransitionAt: Instant,
            dispatchAttempts: Int,
            claimedAt: Instant?,
            claimedBy: String?,
            histories: List<NotificationHistory>,
        ): Notification {
            require(requestId.isNotBlank()) { "requestId must not be blank" }
            require(requester.isNotBlank()) { "requester must not be blank" }
            require(recipient.isNotBlank()) { "recipient must not be blank" }
            require(message == null || message.isNotBlank()) { "message must not be blank" }
            require(dispatchAttempts >= 0) { "dispatchAttempts must not be negative" }
            require(claimedBy == null || claimedBy.isNotBlank()) { "claimedBy must not be blank" }
            require((claimedAt == null) == (claimedBy == null)) {
                "claimedAt and claimedBy must be both null or both non-null"
            }
            if (status == NotificationStatus.PROCESSING) {
                require(claimedAt != null && claimedBy != null) {
                    "PROCESSING notification must have claim information"
                }
            } else {
                require(claimedAt == null && claimedBy == null) {
                    "non-PROCESSING notification must not have claim information"
                }
            }

            return Notification(
                id = id,
                requestId = requestId,
                requester = requester,
                channel = channel,
                recipient = recipient,
                message = message,
                requestedAt = requestedAt,
                status = status,
                failureReason = failureReason,
                updatedAt = updatedAt,
                lastTransitionAt = lastTransitionAt,
                dispatchAttempts = dispatchAttempts,
                claimedAt = claimedAt,
                claimedBy = claimedBy,
                histories = histories,
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
        if (this.status != NotificationStatus.REQUESTED && this.status != NotificationStatus.PUBLISH_FAILED) {
            throw IllegalStateException("markPublished requires REQUESTED or PUBLISH_FAILED, current= ${this.status}")
        }

        transition(this.status, NotificationStatus.PUBLISHED, now)
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
    fun markProcessing(now: Instant, claimedBy: String) {
        require(claimedBy.isNotBlank()) { "claimedBy must not be blank" }

        if (this.status == NotificationStatus.PROCESSING) {
            return
        }
        if (this.status != NotificationStatus.PUBLISHED && this.status != NotificationStatus.RETRY_WAIT) {
            throw IllegalStateException("markProcessing requires PUBLISHED or RETRY_WAIT, current= ${this.status}")
        }
        transition(this.status, NotificationStatus.PROCESSING, now)
        this.claimedAt = now
        this.claimedBy = claimedBy
    }

    /**
     * 외부 채널 발송 성공 처리.
     * NotificationStatus: [PROCESSING --> SENT]
     */
    fun markSent(now: Instant) {
        transition(NotificationStatus.PROCESSING, NotificationStatus.SENT, now)
        this.dispatchAttempts += 1
        clearClaim()
    }

    /**
     * 외부 채널 발송 실패 처리.
     * NotificationStatus: [PROCESSING --> FAILED]
     */
    fun markFailed(now: Instant, reason: String) {
        require(reason.isNotBlank()) { "reason must not be blank" }
        transition(NotificationStatus.PROCESSING, NotificationStatus.FAILED, now, reason)
        this.dispatchAttempts += 1
        clearClaim()
    }

    /**
     * 자동 재시도 대기 처리.
     * NotificationStatus: [FAILED --> RETRY_WAIT]
     */
    fun markRetryWait(now: Instant, reason: String) {
        require(reason.isNotBlank()) { "reason must not be blank" }
        transition(NotificationStatus.FAILED, NotificationStatus.RETRY_WAIT, now, reason)
    }

    /**
     * 자동 재시도 종료 처리.
     * NotificationStatus: [FAILED --> DEAD]
     */
    fun markDead(now: Instant, reason: String) {
        require(reason.isNotBlank()) { "reason must not be blank" }

        if (this.status == NotificationStatus.DEAD) {
            return
        }

        transition(NotificationStatus.FAILED, NotificationStatus.DEAD, now, reason)
    }

    fun canRetry(maxAttempts: Int): Boolean {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        return this.dispatchAttempts < maxAttempts
    }

    /**
     * 상태 전이의 최종 가드.
     *
     * 멱등 마커와 저장소 유니크 제약을 통과해도 같은 알림 row에서 허용되지 않는 상태 변경은 여기서 차단한다.
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

    private fun clearClaim() {
        this.claimedAt = null
        this.claimedBy = null
    }

}
