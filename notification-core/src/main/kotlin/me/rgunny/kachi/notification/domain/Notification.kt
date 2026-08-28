package me.rgunny.kachi.notification.domain

import java.time.Instant

class Notification private constructor(

    val id: NotificationId,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String?,
    val origin: NotificationOrigin,
    /**
     * 외부 요청을 알림으로 최초 접수한 시각.
     */
    val requestedAt: Instant,
    val status: NotificationStatus,
    val failureReason: String?,
    /**
     * 알림 row가 마지막으로 변경된 시각.
     */
    val updatedAt: Instant,
    /**
     * 알림 상태가 마지막으로 전이된 시각.
     */
    val lastTransitionAt: Instant,
    val dispatchAttempts: Int,
    val claimedAt: Instant?,
    val claimedBy: String?,
    /**
     * 이 인스턴스에 이르기까지 쌓였으나 아직 저장되지 않은 상태 전이 이력.
     */
    val uncommittedHistories: List<NotificationHistory>,
) {
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
            origin: NotificationOrigin,
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
                origin = origin,
                requestedAt = now,
                status = NotificationStatus.REQUESTED,
                failureReason = null,
                updatedAt = now,
                lastTransitionAt = now,
                dispatchAttempts = 0,
                claimedAt = null,
                claimedBy = null,
                uncommittedHistories = emptyList(),
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
            origin: NotificationOrigin,
            requestedAt: Instant,
            status: NotificationStatus,
            failureReason: String?,
            updatedAt: Instant,
            lastTransitionAt: Instant,
            dispatchAttempts: Int,
            claimedAt: Instant?,
            claimedBy: String?,
            uncommittedHistories: List<NotificationHistory> = emptyList(),
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
                origin = origin,
                requestedAt = requestedAt,
                status = status,
                failureReason = failureReason,
                updatedAt = updatedAt,
                lastTransitionAt = lastTransitionAt,
                dispatchAttempts = dispatchAttempts,
                claimedAt = claimedAt,
                claimedBy = claimedBy,
                uncommittedHistories = uncommittedHistories.toList(),
            )
        }
    }

    /**
     * Kafka 발행 성공 처리.
     * NotificationStatus: [REQUESTED --> PUBLISHED]
     */
    fun markPublished(now: Instant): Notification {
        // 이미 PUBLISHED 면 변화 없이 반환 (멱등).
        if (status == NotificationStatus.PUBLISHED) {
            return this
        }

        // REQUESTED 가 아니면 잘못된 상태에서의 전이요청으로 예외 처리
        check(status == NotificationStatus.REQUESTED || status == NotificationStatus.PUBLISH_FAILED) {
            "markPublished requires REQUESTED or PUBLISH_FAILED, current= $status"
        }

        return transition(status, NotificationStatus.PUBLISHED, now)
    }

    /**
     * Kafka 발행 실패 처리.
     * NotificationStatus: [REQUESTED --> PUBLISH_FAILED]
     */
    fun markPublishFailed(now: Instant, reason: String): Notification {
        // 이미 PUBLISHED, PUBLISH_FAILED 면 변화 없이 반환 (멱등).
        if (status == NotificationStatus.PUBLISHED
            || status == NotificationStatus.PUBLISH_FAILED) {
            return this
        }

        return transition(NotificationStatus.REQUESTED, NotificationStatus.PUBLISH_FAILED, now, reason)
    }

    /**
     * 카프카 요청 수신 성공 처리.
     * NotificationStatus: [PUBLISHED --> PROCESSING]
     */
    fun markProcessing(now: Instant, claimedBy: String): Notification {
        require(claimedBy.isNotBlank()) { "claimedBy must not be blank" }

        if (status == NotificationStatus.PROCESSING) {
            return this
        }
        check(status == NotificationStatus.PUBLISHED || status == NotificationStatus.RETRY_WAIT) {
            "markProcessing requires PUBLISHED or RETRY_WAIT, current= $status"
        }

        return transition(status, NotificationStatus.PROCESSING, now)
            .copy(claimedAt = now, claimedBy = claimedBy)
    }

    /**
     * 외부 채널 발송 성공 처리.
     * NotificationStatus: [PROCESSING --> SENT]
     */
    fun markSent(now: Instant): Notification {
        return transition(NotificationStatus.PROCESSING, NotificationStatus.SENT, now)
            .copy(dispatchAttempts = dispatchAttempts + 1, claimedAt = null, claimedBy = null)
    }

    /**
     * 외부 채널 발송 실패 처리.
     * NotificationStatus: [PROCESSING --> FAILED]
     */
    fun markFailed(now: Instant, reason: String): Notification {
        require(reason.isNotBlank()) { "reason must not be blank" }

        return transition(NotificationStatus.PROCESSING, NotificationStatus.FAILED, now, reason)
            .copy(dispatchAttempts = dispatchAttempts + 1, claimedAt = null, claimedBy = null)
    }

    /**
     * 수신처가 더 이상 유효하지 않아 발송하지 않고 끝내는 처리.
     * NotificationStatus: [PROCESSING --> SUPPRESSED]
     */
    fun markSuppressed(now: Instant, reason: String): Notification {
        require(reason.isNotBlank()) { "reason must not be blank" }

        return transition(NotificationStatus.PROCESSING, NotificationStatus.SUPPRESSED, now, reason)
            .copy(claimedAt = null, claimedBy = null)
    }

    /**
     * 자동 재시도 대기 처리.
     * NotificationStatus: [FAILED --> RETRY_WAIT]
     */
    fun markRetryWait(now: Instant, reason: String): Notification {
        require(reason.isNotBlank()) { "reason must not be blank" }

        return transition(NotificationStatus.FAILED, NotificationStatus.RETRY_WAIT, now, reason)
    }

    /**
     * 자동 재시도 종료 처리.
     * NotificationStatus: [FAILED --> DEAD]
     */
    fun markDead(now: Instant, reason: String): Notification {
        require(reason.isNotBlank()) { "reason must not be blank" }

        if (status == NotificationStatus.DEAD) {
            return this
        }

        return transition(NotificationStatus.FAILED, NotificationStatus.DEAD, now, reason)
    }

    /**
     * 운영자 수동 복구 처리.
     * NotificationStatus: [DEAD --> REQUESTED]
     */
    fun recoverDeadToRequested(now: Instant, reason: String): Notification {
        require(reason.isNotBlank()) { "reason must not be blank" }
        check(status == NotificationStatus.DEAD) {
            "recoverDeadToRequested requires DEAD, current=$status"
        }

        return copy(
            status = NotificationStatus.REQUESTED,
            failureReason = null,
            updatedAt = now,
            lastTransitionAt = now,
            dispatchAttempts = 0,
            claimedAt = null,
            claimedBy = null,
            uncommittedHistories = uncommittedHistories + NotificationHistory.record(
                notificationId = id,
                fromStatus = status,
                toStatus = NotificationStatus.REQUESTED,
                createdAt = now,
                reason = reason,
            ),
        )
    }

    fun canRetry(maxAttempts: Int): Boolean {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        return dispatchAttempts < maxAttempts
    }

    /**
     * 상태 전이의 최종 가드.
     *
     * 멱등 마커와 저장소 유니크 제약을 통과해도 같은 알림 row에서 허용되지 않는 상태 변경은 여기서 차단한다.
     * 통과하면 전이 이력을 덧붙인 새 인스턴스를 돌려준다.
     */
    private fun transition(
        expected: NotificationStatus, next: NotificationStatus,
        now: Instant,
        reason: String? = null
    ): Notification {
        check(status == expected) {
            "transition to $next requires $expected, current=$status"
        }

        return copy(
            status = next,
            updatedAt = now,
            lastTransitionAt = now,
            failureReason = reason,
            uncommittedHistories = uncommittedHistories + NotificationHistory.record(
                notificationId = id,
                fromStatus = status,
                toStatus = next,
                createdAt = now,
                reason = reason,
            ),
        )
    }

    /**
     * 전이 결과 인스턴스를 만든다.
     *
     * 전이해도 바뀌지 않는 식별자/요청 내용/접수 시각은 인자로 받지 않는다.
     */
    private fun copy(
        status: NotificationStatus = this.status,
        failureReason: String? = this.failureReason,
        updatedAt: Instant = this.updatedAt,
        lastTransitionAt: Instant = this.lastTransitionAt,
        dispatchAttempts: Int = this.dispatchAttempts,
        claimedAt: Instant? = this.claimedAt,
        claimedBy: String? = this.claimedBy,
        uncommittedHistories: List<NotificationHistory> = this.uncommittedHistories,
    ): Notification {
        return Notification(
            id = id,
            requestId = requestId,
            requester = requester,
            channel = channel,
            recipient = recipient,
            message = message,
            origin = origin,
            requestedAt = requestedAt,
            status = status,
            failureReason = failureReason,
            updatedAt = updatedAt,
            lastTransitionAt = lastTransitionAt,
            dispatchAttempts = dispatchAttempts,
            claimedAt = claimedAt,
            claimedBy = claimedBy,
            uncommittedHistories = uncommittedHistories,
        )
    }
}
